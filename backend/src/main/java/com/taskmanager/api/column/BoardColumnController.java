package com.taskmanager.api.column;

import com.taskmanager.api.auth.CurrentUser;
import com.taskmanager.api.board.BoardRepository;
import com.taskmanager.api.column.ColumnDtos.ColumnMoveRequest;
import com.taskmanager.api.column.ColumnDtos.ColumnRequest;
import com.taskmanager.api.column.ColumnDtos.ColumnResponse;
import com.taskmanager.api.column.ColumnDtos.ColumnUpdateRequest;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class BoardColumnController {

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final CurrentUser currentUser;

    public BoardColumnController(BoardColumnRepository columnRepository, TaskRepository taskRepository,
                                  BoardRepository boardRepository, CurrentUser currentUser) {
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/boards/{boardId}/columns")
    public List<ColumnResponse> list(@PathVariable String boardId, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        requireBoardOwnership(boardId, userId);
        return columnRepository.findByBoardIdOrderByDisplayOrderAsc(boardId).stream()
                .map(ColumnResponse::from).toList();
    }

    @PostMapping("/api/boards/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse create(@PathVariable String boardId, @Valid @RequestBody ColumnRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        requireBoardOwnership(boardId, userId);
        int nextOrder = (int) columnRepository.countByBoardId(boardId);
        BoardColumn column = new BoardColumn(boardId, request.name().trim(), request.done(), nextOrder);
        return ColumnResponse.from(columnRepository.save(column));
    }

    @PutMapping("/api/columns/{id}")
    public ColumnResponse update(@PathVariable String id, @RequestBody ColumnUpdateRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        BoardColumn column = findOrThrow(id);
        requireBoardOwnership(column.getBoardId(), userId);
        if (request.name() != null && !request.name().isBlank()) {
            column.setName(request.name().trim());
        }
        if (request.done() != null) {
            column.setDone(request.done());
        }
        return ColumnResponse.from(columnRepository.save(column));
    }

    @PatchMapping("/api/columns/{id}/move")
    @Transactional
    public ColumnResponse move(@PathVariable String id, @Valid @RequestBody ColumnMoveRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        BoardColumn column = findOrThrow(id);
        requireBoardOwnership(column.getBoardId(), userId);
        List<BoardColumn> boardColumns = new ArrayList<>(columnRepository.findByBoardIdOrderByDisplayOrderAsc(column.getBoardId()));
        boardColumns.removeIf(c -> c.getId().equals(id));
        int index = Math.max(0, Math.min(request.displayOrder(), boardColumns.size()));
        boardColumns.add(index, column);
        for (int i = 0; i < boardColumns.size(); i++) {
            boardColumns.get(i).setDisplayOrder(i);
        }
        columnRepository.saveAll(boardColumns);
        return ColumnResponse.from(column);
    }

    @DeleteMapping("/api/columns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        BoardColumn column = findOrThrow(id);
        requireBoardOwnership(column.getBoardId(), userId);
        if (taskRepository.countByColumnId(id) > 0) {
            throw ApiException.badRequest("タスクが残っている列は削除できません");
        }
        if (columnRepository.countByBoardId(column.getBoardId()) <= 1) {
            throw ApiException.badRequest("最後の1列は削除できません");
        }
        String boardId = column.getBoardId();
        columnRepository.deleteById(id);
        List<BoardColumn> remaining = columnRepository.findByBoardIdOrderByDisplayOrderAsc(boardId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i);
        }
        columnRepository.saveAll(remaining);
    }

    private void requireBoardOwnership(String boardId, String userId) {
        if (!boardRepository.existsByIdAndUserId(boardId, userId)) {
            throw ApiException.notFound("ボードが見つかりません: " + boardId);
        }
    }

    private BoardColumn findOrThrow(String id) {
        return columnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("列が見つかりません: " + id));
    }
}
