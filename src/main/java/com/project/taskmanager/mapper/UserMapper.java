package com.project.taskmanager.mapper;

import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    // `id` is assigned by Mongo on insert and `roles` are granted by the service, never taken from
    // a registration payload -- mapping either here would let a caller self-assign a role.
    // Declared so the omission is a decision on the record rather than an unmapped-target warning.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRegistrationDTO userRegistrationDTO);
}
