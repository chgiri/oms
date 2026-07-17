package com.giri.oms.auth.mapper;

import com.giri.oms.auth.dto.UserResponse;
import com.giri.oms.auth.entity.AppUser;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // Password is deliberately absent from UserResponse's fields, so MapStruct
    // has no target property to map it to — it never appears in the output.
    UserResponse mapToUserResponse(AppUser user);
}
