package com.hangeoreum.api.identity.infrastructure;

import com.hangeoreum.api.identity.domain.OauthLink;
import com.hangeoreum.api.identity.domain.OauthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OauthLinkRepository extends JpaRepository<OauthLink, UUID> {

    List<OauthLink> findByUserId(UUID userId);

    Optional<OauthLink> findByUserIdAndProvider(UUID userId, OauthProvider provider);

    Optional<OauthLink> findByProviderAndProviderUid(OauthProvider provider, String providerUid);
}
