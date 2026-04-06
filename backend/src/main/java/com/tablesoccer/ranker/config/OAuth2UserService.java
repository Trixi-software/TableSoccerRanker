package com.tablesoccer.ranker.config;

import com.tablesoccer.ranker.user.Role;
import com.tablesoccer.ranker.user.User;
import com.tablesoccer.ranker.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

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
        OAuth2User oAuth2User = super.loadUser(request);

        String hostedDomain = oAuth2User.getAttribute("hd");
        if (!allowedDomain.isBlank() && !allowedDomain.equals(hostedDomain)) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("invalid_domain"),
                "Only @" + allowedDomain + " accounts are allowed"
            );
        }

        String sub = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        userRepository.findByGoogleSub(sub).ifPresentOrElse(
            user -> {
                user.setDisplayName(name);
                user.setAvatarUrl(picture);
                user.setEmail(email);
                userRepository.save(user);
            },
            () -> {
                var user = new User();
                user.setGoogleSub(sub);
                user.setEmail(email);
                user.setDisplayName(name);
                user.setAvatarUrl(picture);
                // First user gets ADMIN role (countByRole is safer against race conditions)
                user.setRole(userRepository.countByRole(Role.ADMIN) == 0 ? Role.ADMIN : Role.PLAYER);
                userRepository.save(user);
            }
        );

        return oAuth2User;
    }
}
