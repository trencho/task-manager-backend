package com.project.taskmanager.repository;

import com.project.taskmanager.entity.Task;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<Task, String>, TaskRepositoryCustom {

    // `findByUsername` is gone: TaskRepositoryCustom.search(...) covers it with every filter null.
    // Two code paths to the same listing is one too many.

}
