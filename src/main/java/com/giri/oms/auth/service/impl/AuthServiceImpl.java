package com.giri.oms.auth.service.impl;

import com.giri.oms.auth.constants.AuthConstants;
import com.giri.oms.auth.dto.AuthResponse;
import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.dto.UserResponse;
import com.giri.oms.auth.entity.AppUser;
import com.giri.oms.auth.exception.EmailAlreadyExistsException;
import com.giri.oms.auth.exception.UsernameAlreadyExistsException;
import com.giri.oms.auth.mapper.UserMapper;
import com.giri.oms.auth.repository.UserRepository;
import com.giri.oms.auth.service.AuthService;
import com.giri.oms.security.JwtProperties;
import com.giri.oms.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Registering new user: {}", request.getUsername());

        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            log.warn("Rejected registration — username already taken: {}", request.getUsername());
            throw new UsernameAlreadyExistsException(request.getUsername());
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            log.warn("Rejected registration — email already registered: {}", request.getEmail());
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // never store the raw password
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setEnabled(true);

        AppUser savedUser = userRepository.save(user);

        log.info(AuthConstants.USER_REGISTERED_LOG, savedUser.getId(), savedUser.getUsername());
        return userMapper.mapToUserResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt for user: {}", request.getUsername());

        // Delegates to the configured AuthenticationProvider (DaoAuthenticationProvider,
        // wired in SecurityConfig), which loads the user via UserDetailsServiceImpl and
        // checks the password against passwordEncoder. Throws BadCredentialsException /
        // DisabledException on failure — handled centrally in GlobalExceptionHandler.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.replace(ROLE_PREFIX, ""))
                .orElse(null);

        log.info(AuthConstants.USER_LOGGED_IN_LOG, request.getUsername());
        return new AuthResponse(token, "Bearer", jwtProperties.expirationMs(), userDetails.getUsername(), role);
    }
}
