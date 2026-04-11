package com.tablesoccer.ranker.config;

import com.tablesoccer.ranker.user.Role;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserService.class);

    private final UserRepository userRepository;
    private final String allowedDomain;

    public OAuth2UserService(UserRepository userRepository,
                             @Value("${app.allowed-domain:trixi.cz}") String allowedDomain) {
        this.userRepository = userRepository;
        this.allowedDomain = allowedDomain;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        log.info("OAuth2 loadUser called for registrationId={}", request.getClientRegistration().getRegistrationId());
        OAuth2User oAuth2User = super.loadUser(request);
        syncUser(oAuth2User);
        return oAuth2User;
    }

    /**
     * Creates or updates a user in the database from OAuth2/OIDC attributes.
     * Called from both OAuth2 and OIDC flows.
     * Returns the merged set of authorities (original + DB role).
     */
    @Transactional
    public Set<GrantedAuthority> syncUser(OAuth2User oAuth2User) {
        log.info("syncUser called with attributes: sub={}, email={}, name={}, hd={}, all keys={}",
            oAuth2User.getAttribute("sub"),
            oAuth2User.getAttribute("email"),
            oAuth2User.getAttribute("name"),
            oAuth2User.getAttribute("hd"),
            oAuth2User.getAttributes().keySet());

        String hostedDomain = oAuth2User.getAttribute("hd");
        if (!allowedDomain.isBlank() && !allowedDomain.equals(hostedDomain)) {
            log.warn("OAuth2 domain rejected: expected='{}', got='{}'", allowedDomain, hostedDomain);
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_domain"),
                "Only @" + allowedDomain + " accounts are allowed"
            );
        }

        String sub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        var existing = userRepository.findByGoogleSub(sub)
            .or(() -> userRepository.findByEmail(email));

        Role role;
        if (existing.isPresent()) {
            var user = existing.get();
            log.info("Existing user found: id={}, matchedBy={}, updating profile",
                user.getId(), user.getGoogleSub() != null ? "googleSub" : "email");
            user.setGoogleSub(sub);
            user.setDisplayName(name);
            user.setAvatarUrl(picture);
            user.setEmail(email);
            userRepository.save(user);
            role = user.getRole();
        } else {
            log.info("No user found by googleSub={} or email={}, creating new user", sub, email);
            var user = new User();
            user.setGoogleSub(sub);
            user.setEmail(email);
            user.setDisplayName(name);
            user.setAvatarUrl(picture);
            user.setRole(userRepository.countByRole(Role.ADMIN) == 0 ? Role.ADMIN : Role.PLAYER);
            userRepository.save(user);
            log.info("New user created: id={}, role={}", user.getId(), user.getRole());
            role = user.getRole();
        }

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return authorities;
    }
}
