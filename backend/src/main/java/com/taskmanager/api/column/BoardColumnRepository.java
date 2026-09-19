package com.taskmanager.api.column;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, String> {
    List<BoardColumn> findByBoardIdOrderByDisplayOrderAsc(String boardId);

    long countByBoardId(String boardId);

    void deleteByBoardId(String boardId);
}
