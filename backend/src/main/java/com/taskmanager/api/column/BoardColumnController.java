package com.taskmanager.api.column;

import com.taskmanager.api.column.ColumnDtos.ColumnRequest;
import com.taskmanager.api.column.ColumnDtos.ColumnResponse;
import com.taskmanager.api.column.ColumnDtos.ColumnUpdateRequest;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BoardColumnController {

    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;

    public BoardColumnController(BoardColumnRepository columnRepository, TaskRepository taskRepository) {
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/api/boards/{boardId}/columns")
    public List<ColumnResponse> list(@PathVariable String boardId) {
        return columnRepository.findByBoardIdOrderByDisplayOrderAsc(boardId).stream()
                .map(ColumnResponse::from).toList();
    }

    @PostMapping("/api/boards/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public ColumnResponse create(@PathVariable String boardId, @Valid @RequestBody ColumnRequest request) {
        int nextOrder = (int) columnRepository.countByBoardId(boardId);
        BoardColumn column = new BoardColumn(boardId, request.name().trim(), request.done(), nextOrder);
        return ColumnResponse.from(columnRepository.save(column));
    }

    @PutMapping("/api/columns/{id}")
    public ColumnResponse update(@PathVariable String id, @RequestBody ColumnUpdateRequest request) {
        BoardColumn column = columnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("列が見つかりません: " + id));
        if (request.name() != null && !request.name().isBlank()) {
            column.setName(request.name().trim());
        }
        if (request.done() != null) {
            column.setDone(request.done());
        }
        return ColumnResponse.from(columnRepository.save(column));
    }

    @DeleteMapping("/api/columns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        BoardColumn column = columnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("列が見つかりません: " + id));
        if (taskRepository.countByColumnId(id) > 0) {
            throw ApiException.badRequest("タスクが残っている列は削除できません");
        }
        if (columnRepository.countByBoardId(column.getBoardId()) <= 1) {
            throw ApiException.badRequest("最後の1列は削除できません");
        }
        columnRepository.deleteById(id);
    }
}
