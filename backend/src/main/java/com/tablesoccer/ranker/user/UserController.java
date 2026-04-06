package com.tablesoccer.ranker.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    public UserController(UserService userService, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/auth/me")
    public UserDto currentUser(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken oauth) {
            return userService.findCurrentUser(oauth.getPrincipal());
        }
        return userService.findCurrentUserByUsername(principal.getName());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/auth/login")
    public UserDto login(@Valid @RequestBody LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        var authToken = new UsernamePasswordAuthenticationToken(request.username(), request.password());
        var authentication = authenticationManager.authenticate(authToken);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Invalidate existing session to prevent session fixation
        var existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        // Create new session
        var session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        return userService.findCurrentUserByUsername(request.username());
    }

    @PutMapping("/auth/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        userService.changePassword(principal, request);
    }

    @GetMapping("/users")
    public List<UserDto> listUsers() {
        return userService.findAllActive();
    }

    @GetMapping("/users/{id}")
    public UserDto getUser(@PathVariable UUID id) {
        return userService.findById(id);
    }
}
