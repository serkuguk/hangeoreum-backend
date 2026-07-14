package com.hangeoreum.api.identity.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthLink {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private OauthProvider provider;

    private String providerUid;

    private Instant createdAt = Instant.now();

    public static OauthLink link(UUID userId, OauthProvider provider, String providerUid) {
        OauthLink link = new OauthLink();
        link.userId = userId;
        link.provider = provider;
        link.providerUid = providerUid;
        return link;
    }
}
