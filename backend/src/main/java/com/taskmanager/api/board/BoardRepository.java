package com.taskmanager.api.board;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, String> {
    List<Board> findByUserId(String userId);
    Optional<Board> findByIdAndUserId(String id, String userId);
    boolean existsByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
}
