pipeline {
    agent { label 'built-in' }

    options {
        timestamps()
        skipDefaultCheckout()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        booleanParam(name: 'DEPLOY_TO_VPS', defaultValue: false, description: 'Deploy the tested build to the VPS')
        booleanParam(name: 'CONFIRM_DATABASE_PREPARED', defaultValue: false, description: 'Required for the first or a changed migration manifest. Enable after separately preparing the target database and verifying its Flyway history for this build.')
        string(name: 'EXPECTED_MIGRATION_MANIFEST_SHA256', defaultValue: '', trim: true, description: 'Required with database confirmation. Enter the migration manifest SHA-256 from the build whose migrations you verified.')
    }

    environment {
        DEPLOY_HOST = 'deploy@207.180.212.3'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/serkuguk/hangeoreum-backend.git'
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    set -eu
                    sh ./mvnw -B clean package
                    set -- target/hangeoreum-api-*.jar
                    test "$#" -eq 1
                    test -s "$1"
                    mv -- "$1" target/app.jar

                    migrations=$(mktemp -d)
                    trap 'rm -rf -- "$migrations"' EXIT
                    unzip -q target/app.jar 'BOOT-INF/classes/db/migration/*' -d "$migrations"
                    (
                        cd "$migrations"
                        find BOOT-INF/classes/db/migration -type f -exec sha256sum {} + > unsorted
                        LC_ALL=C sort -k2 unsorted
                    ) > target/migrations.sha256
                    test -s target/migrations.sha256
                    manifest_sha256=$(sha256sum < target/migrations.sha256)
                    printf '%s\\n' "${manifest_sha256%% *}" > target/migration-manifest.sha256
                    printf 'EXPECTED_MIGRATION_MANIFEST_SHA256=%s\\n' "${manifest_sha256%% *}"
                '''
                archiveArtifacts artifacts: 'target/app.jar,target/migrations.sha256,target/migration-manifest.sha256', fingerprint: true
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Deploy') {
            when { expression { params.DEPLOY_TO_VPS } }
            steps {
                sshagent(credentials: ['vps-deploy']) {
                    sh '''
                        set -eu
                        expected_manifest_sha256=${EXPECTED_MIGRATION_MANIFEST_SHA256:-}
                        if test -n "$expected_manifest_sha256" &&
                           { test "${#expected_manifest_sha256}" -ne 64 ||
                             ! printf '%s\\n' "$expected_manifest_sha256" | LC_ALL=C grep -Eq '^[0-9a-f]{64}$'; }; then
                            echo 'EXPECTED_MIGRATION_MANIFEST_SHA256 must be exactly 64 lowercase hexadecimal characters.' >&2
                            exit 1
                        fi
                        stage=$(mktemp -d)
                        trap 'rm -rf -- "$stage"' EXIT
                        cp target/app.jar target/migrations.sha256 "$stage/"
                        cat > "$stage/deploy.sh" <<'REMOTE'
set -eu
umask 077
incoming=$1
service=coreano-api.service
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
cd "$HOME/projects/coreano-api"
test -f coreano-api.env
test "$(stat -c %a coreano-api.env)" = 600
test "$(stat -c %u coreano-api.env)" = "$(id -u)"
mkdir -p releases shared/uploads

exec 9> .deploy.lock
flock -x 9

test "$(systemctl --user show --property=LoadState --value "$service")" = loaded
fragment=$(systemctl --user show --property=FragmentPath --value "$service")
fragment_sha256=$(sha256sum < "$fragment")
drop_ins=$(systemctl --user show --property=DropInPaths --all "$service")
reload=$(systemctl --user show --property=NeedDaemonReload --value "$service")
exec_start=$(systemctl --user show --property=ExecStart --value "$service")
expected_argv="/usr/bin/java -jar $HOME/projects/coreano-api/current/app.jar --spring.profiles.active=prod --server.address=127.0.0.1 --server.port=8082 --spring.flyway.enabled=false --app.storage.dir=$HOME/projects/coreano-api/shared/uploads"
# systemctl flattens argv. The approved unit checksum also checks argument boundaries.
if test "${fragment_sha256%% *}" != dbc94775a995c0c9416fa0b65045f490b0eb06fbb31bd86174f784b66fdda013 ||
   test "$drop_ins" != 'DropInPaths=' ||
   test "$reload" != no ||
   test "${exec_start%% ; start_time=*}" != "{ path=/usr/bin/java ; argv[]=$expected_argv ; ignore_errors=no"; then
    echo 'Install the approved coreano-api.service without drop-ins and reload the user service manager before deploying. Its ExecStart must disable Flyway.' >&2
    exit 1
fi

previous=
same_manifest=false
if test -L current; then
    previous=$(readlink current)
    if test -f "$previous/.healthy" && cmp -s "$previous/migrations.sha256" "$incoming/migrations.sha256"; then
        same_manifest=true
    fi
else
    test ! -e current
fi
if ! "$same_manifest"; then
    if test "$2" != true; then
        echo 'First, changed, or unverified migration manifest. Prepare the target database and verify its Flyway history separately before enabling CONFIRM_DATABASE_PREPARED.' >&2
        exit 1
    fi
    candidate_manifest_sha256=$(sha256sum < "$incoming/migrations.sha256")
    if test "$3" != "${candidate_manifest_sha256%% *}"; then
        echo 'EXPECTED_MIGRATION_MANIFEST_SHA256 does not match the candidate. Verify the target database against this build before deploying.' >&2
        exit 1
    fi
    echo 'Database preparation confirmed by the operator. Jenkins compares release manifests only.'
fi

# shortcut: releases accumulate, add retention when disk usage warrants it.
release=$(mktemp -d releases/build-XXXXXXXX)
mv -- "$incoming/app.jar" "$incoming/migrations.sha256" "$release/"

activate() {
    ln -s -- "$1" "$release/.next-current" && mv -Tf -- "$release/.next-current" current
}

wait_healthy() {
    for attempt in $(seq 1 45); do
        if pid=$(systemctl --user show --property=MainPID --value "$service") &&
           test "$pid" -gt 0 &&
           ss -H -ltnp 'sport = :8082' | grep -Fq "pid=$pid," &&
           health=$(curl --fail --silent --max-time 2 http://127.0.0.1:8082/actuator/health) &&
           printf '%s\\n' "$health" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

finish() {
    status=$?
    trap '' HUP INT TERM
    if test "$status" -ne 0 && test "$(readlink current || true)" = "$release"; then
        if "$same_manifest"; then
            if activate "$previous" && systemctl --user restart "$service" && wait_healthy; then
                echo 'Restored the previous healthy release.' >&2
            else
                echo 'Failed to restore health. Check the VPS service journal.' >&2
            fi
        else
            systemctl --user stop "$service" || echo 'Failed to stop the candidate service.' >&2
            rm -- current || echo 'Failed to remove the candidate release link.' >&2
            echo 'Check database compatibility and the VPS service journal before restarting. The previous release may require a different schema.' >&2
            printf 'Candidate release: %s. Previous release: %s.\\n' "$release" "${previous:-none}" >&2
        fi
    fi
    exit "$status"
}
trap finish EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

activate "$release"
systemctl --user restart "$service"
if ! wait_healthy; then
    echo 'API health check failed. Check the VPS service journal.' >&2
    exit 1
fi
touch "$release/.healthy"
echo 'API release is healthy.'
REMOTE

                        tar -C "$stage" -cf "$stage/release.tar" app.jar migrations.sha256 deploy.sh
                        checksum=$(sha256sum < "$stage/release.tar")
                        remote_command='
                            set -eu
                            umask 077
                            stage=$(mktemp -d)
                            cleanup() { rm -rf -- "$stage"; }
                            trap cleanup EXIT
                            cat > "$stage/release.tar"
                            actual=$(sha256sum < "$stage/release.tar")
                            test "${actual%% *}" = "$1"
                            tar -xf "$stage/release.tar" -C "$stage"
                            sh "$stage/deploy.sh" "$stage" "$2" "$3"
                        '
                        ssh -T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15 "$DEPLOY_HOST" \
                            "sh -c '$remote_command' deploy '${checksum%% *}' '$CONFIRM_DATABASE_PREPARED' '$expected_manifest_sha256'" < "$stage/release.tar"
                    '''
                }
            }
        }
    }
}
