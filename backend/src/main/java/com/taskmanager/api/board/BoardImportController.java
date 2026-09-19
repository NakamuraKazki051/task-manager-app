package com.taskmanager.api.board;

import com.taskmanager.api.board.BoardImportDtos.ImportChecklistItemRequest;
import com.taskmanager.api.board.BoardImportDtos.ImportColumnRequest;
import com.taskmanager.api.board.BoardImportDtos.ImportRequest;
import com.taskmanager.api.board.BoardImportDtos.ImportTaskRequest;
import com.taskmanager.api.column.BoardColumn;
import com.taskmanager.api.column.BoardColumnRepository;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.ChecklistItem;
import com.taskmanager.api.task.Priority;
import com.taskmanager.api.task.Task;
import com.taskmanager.api.task.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class BoardImportController {

    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final TaskRepository taskRepository;

    public BoardImportController(BoardRepository boardRepository, BoardColumnRepository columnRepository, TaskRepository taskRepository) {
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.taskRepository = taskRepository;
    }

    @PutMapping("/api/boards/{boardId}/import")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void importBoard(@PathVariable String boardId, @Valid @RequestBody ImportRequest request) {
        if (!boardRepository.existsById(boardId)) {
            throw ApiException.notFound("ボードが見つかりません: " + boardId);
        }

        taskRepository.deleteByBoardId(boardId);
        columnRepository.deleteByBoardId(boardId);

        Map<String, String> columnIdMap = new HashMap<>();
        int order = 0;
        for (ImportColumnRequest columnRequest : request.columns()) {
            BoardColumn column = columnRepository.save(new BoardColumn(boardId, columnRequest.name().trim(), columnRequest.done(), order++));
            if (columnRequest.id() != null) {
                columnIdMap.put(columnRequest.id(), column.getId());
            }
        }

        if (request.tasks() == null) {
            return;
        }
        Map<String, Integer> nextOrderByColumn = new HashMap<>();
        for (ImportTaskRequest taskRequest : request.tasks()) {
            String newColumnId = columnIdMap.get(taskRequest.columnId());
            if (newColumnId == null) {
                throw ApiException.badRequest("タスクが参照する列が見つかりません: " + taskRequest.columnId());
            }
            Task task = new Task(boardId, newColumnId, taskRequest.title().trim());
            task.setDescription(taskRequest.description());
            task.setDueDate(taskRequest.dueDate());
            task.setPriority(taskRequest.priority() != null ? taskRequest.priority() : Priority.MID);
            task.setCategories(taskRequest.categories());
            if (taskRequest.checklist() != null) {
                int checklistOrder = 0;
                for (ImportChecklistItemRequest item : taskRequest.checklist()) {
                    task.addChecklistItem(new ChecklistItem(item.text(), item.done(), checklistOrder++));
                }
            }
            int displayOrder = nextOrderByColumn.merge(newColumnId, 1, Integer::sum) - 1;
            task.setDisplayOrder(displayOrder);
            taskRepository.save(task);
        }
    }
}
