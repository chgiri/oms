package com.giri.oms.auth.service;

import com.giri.oms.auth.dto.AuthResponse;
import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.dto.UserResponse;
import com.giri.oms.auth.entity.AppUser;
import com.giri.oms.auth.entity.Role;
import com.giri.oms.auth.exception.EmailAlreadyExistsException;
import com.giri.oms.auth.exception.UsernameAlreadyExistsException;
import com.giri.oms.auth.mapper.UserMapper;
import com.giri.oms.auth.repository.UserRepository;
import com.giri.oms.auth.service.impl.AuthServiceImpl;
import com.giri.oms.security.JwtProperties;
import com.giri.oms.security.JwtService;
import com.giri.oms.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AuthServiceImpl authService;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        // Constructed manually rather than via @InjectMocks — JwtProperties is a
        // plain record (not mockable/injectable the way interfaces are), so it's
        // simplest to just pass a real instance through the constructor directly.
        JwtProperties jwtProperties = new JwtProperties("unused-in-this-test", "unused-in-this-test", "test-key", 86_400_000L);
        authService = new AuthServiceImpl(userRepository, userMapper, passwordEncoder, authenticationManager, jwtService, jwtProperties, tokenBlacklistService);

        registerRequest = new RegisterRequest("jane.doe", "S3curePass!", "jane.doe@example.com", Role.STAFF);
    }

    @Nested
    class Register {

        @Test
        void savesEncodedPasswordAndReturnsMappedResponse() {
            when(userRepository.existsByUsernameIgnoreCase("jane.doe")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(false);
            when(passwordEncoder.encode("S3curePass!")).thenReturn("encoded-hash");

            AppUser saved = new AppUser();
            saved.setId(1L);
            saved.setUsername("jane.doe");
            saved.setEmail("jane.doe@example.com");
            saved.setRole(Role.STAFF);
            saved.setEnabled(true);
            saved.setCreatedAt(LocalDateTime.now());
            when(userRepository.save(any(AppUser.class))).thenReturn(saved);

            UserResponse expectedResponse = new UserResponse(1L, "jane.doe", "jane.doe@example.com", Role.STAFF, true, LocalDateTime.now());
            when(userMapper.mapToUserResponse(saved)).thenReturn(expectedResponse);

            UserResponse result = authService.register(registerRequest);

            assertThat(result).isEqualTo(expectedResponse);

            var captor = ArgumentCaptor.forClass(AppUser.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getPassword()).isEqualTo("encoded-hash"); // never the raw password
        }

        @Test
        void throwsUsernameAlreadyExistsException_whenUsernameTaken() {
            when(userRepository.existsByUsernameIgnoreCase("jane.doe")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(UsernameAlreadyExistsException.class)
                    .hasMessageContaining("jane.doe");

            verify(userRepository, never()).save(any());
        }

        @Test
        void throwsEmailAlreadyExistsException_whenEmailTaken() {
            when(userRepository.existsByUsernameIgnoreCase("jane.doe")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("jane.doe@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("jane.doe@example.com");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Login {

        @Test
        void authenticatesAndReturnsTokenOnSuccess() {
            var userDetails = User.withUsername("jane.doe")
                    .password("encoded-hash")
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                    .build();
            var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(jwtService.generateToken(userDetails)).thenReturn("signed.jwt.token");

            AuthResponse result = authService.login(new LoginRequest("jane.doe", "S3curePass!"));

            assertThat(result.getAccessToken()).isEqualTo("signed.jwt.token");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
            assertThat(result.getUsername()).isEqualTo("jane.doe");
            assertThat(result.getRole()).isEqualTo("STAFF");
        }

        @Test
        void propagatesBadCredentialsException_whenAuthenticationFails() {
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(new LoginRequest("jane.doe", "wrong-password")))
                    .isInstanceOf(BadCredentialsException.class);

            verify(jwtService, never()).generateToken(any());
        }
    }
}
