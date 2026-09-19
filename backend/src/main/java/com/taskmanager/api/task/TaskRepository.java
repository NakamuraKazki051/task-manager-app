package com.taskmanager.api.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {
    List<Task> findByBoardId(String boardId);

    List<Task> findByColumnIdOrderByDisplayOrderAsc(String columnId);

    long countByColumnId(String columnId);

    void deleteByBoardId(String boardId);
}
