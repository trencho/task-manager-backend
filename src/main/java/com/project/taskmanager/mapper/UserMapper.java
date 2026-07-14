package com.project.taskmanager.mapper;

import com.project.taskmanager.dto.UserRegistrationDTO;
import com.project.taskmanager.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(UserRegistrationDTO userRegistrationDTO);
}
