package com.giri.oms.auth.service;

import com.giri.oms.auth.dto.AuthResponse;
import com.giri.oms.auth.dto.LoginRequest;
import com.giri.oms.auth.dto.RegisterRequest;
import com.giri.oms.auth.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /**
     * Revokes the given bearer token so it's rejected on subsequent requests even
     * though it hasn't naturally expired yet.
     */
    void logout(String token);

}
