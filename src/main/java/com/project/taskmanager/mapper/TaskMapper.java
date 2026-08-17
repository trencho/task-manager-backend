package com.project.taskmanager.mapper;

import com.project.taskmanager.dto.TaskDTO;
import com.project.taskmanager.dto.TaskResponseDTO;
import com.project.taskmanager.entity.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    // `id` is assigned by Mongo on insert and `username` comes from the authenticated principal,
    // never from the request body -- mapping either here would let a client set them. Declared so
    // the omission is a decision on the record rather than an unmapped-target warning.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    Task toEntity(TaskDTO taskDTO);

    /**
     * `username` has no target field on TaskResponseDTO, so MapStruct simply drops it.
     */
    TaskResponseDTO toResponse(Task task);
}
