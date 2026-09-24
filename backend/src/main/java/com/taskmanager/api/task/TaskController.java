package com.taskmanager.api.task;

import com.taskmanager.api.auth.CurrentUser;
import com.taskmanager.api.board.BoardRepository;
import com.taskmanager.api.column.BoardColumn;
import com.taskmanager.api.column.BoardColumnRepository;
import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.TaskDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
public class TaskController {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final CurrentUser currentUser;

    public TaskController(TaskRepository taskRepository, BoardRepository boardRepository,
                           BoardColumnRepository columnRepository, CurrentUser currentUser) {
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/boards/{boardId}/tasks")
    public List<TaskResponse> list(
            @PathVariable String boardId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "manual") String sort,
            HttpServletRequest httpRequest
    ) {
        String userId = currentUser.require(httpRequest);
        requireBoardOwnership(boardId, userId);
        List<Task> tasks = taskRepository.findByBoardId(boardId);

        if (category != null && !category.isBlank()) {
            tasks = tasks.stream().filter(t -> t.getCategories().contains(category)).toList();
        }
        if (priority != null) {
            tasks = tasks.stream().filter(t -> t.getPriority() == priority).toList();
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            tasks = tasks.stream().filter(t ->
                    (t.getTitle() != null && t.getTitle().toLowerCase().contains(needle)) ||
                    (t.getDescription() != null && t.getDescription().toLowerCase().contains(needle))
            ).toList();
        }

        // 同じ期限・優先度どうしはDBの返却順に任せず、手動の並び順で安定させる
        Comparator<Task> byDisplayOrder = Comparator.comparingInt(Task::getDisplayOrder);
        Comparator<Task> comparator = switch (sort) {
            case "due" -> Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                    .thenComparing(byDisplayOrder);
            case "priority" -> Comparator.<Task>comparingInt(t -> priorityRank(t.getPriority())).thenComparing(byDisplayOrder);
            default -> byDisplayOrder;
        };

        return tasks.stream().sorted(comparator).map(TaskResponse::from).toList();
    }

    private int priorityRank(Priority priority) {
        return switch (priority) {
            case HIGH -> 0;
            case MID -> 1;
            case LOW -> 2;
        };
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse get(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Task task = findOrThrow(id);
        requireBoardOwnership(task.getBoardId(), userId);
        return TaskResponse.from(task);
    }

    @PostMapping("/api/boards/{boardId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable String boardId, @Valid @RequestBody TaskRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        requireBoardOwnership(boardId, userId);
        BoardColumn column = requireColumnInBoard(request.columnId(), boardId);
        Task task = new Task(boardId, column.getId(), request.title().trim());
        applyRequest(task, request);
        task.setDisplayOrder((int) taskRepository.countByColumnId(column.getId()));
        return TaskResponse.from(taskRepository.save(task));
    }

    @PutMapping("/api/tasks/{id}")
    @Transactional
    public TaskResponse update(@PathVariable String id, @Valid @RequestBody TaskRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Task task = findOrThrow(id);
        requireBoardOwnership(task.getBoardId(), userId);
        BoardColumn column = requireColumnInBoard(request.columnId(), task.getBoardId());
        task.setTitle(request.title().trim());
        applyRequest(task, request);

        if (!task.getColumnId().equals(column.getId())) {
            String previousColumnId = task.getColumnId();
            task.setColumnId(column.getId());
            task.setDisplayOrder((int) taskRepository.countByColumnId(column.getId()));
            taskRepository.save(task);
            renumberColumn(previousColumnId);
        } else {
            taskRepository.save(task);
        }
        return TaskResponse.from(task);
    }

    private void applyRequest(Task task, TaskRequest request) {
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority() != null ? request.priority() : Priority.MID);
        task.setCategories(request.categories());
        task.setCompleted(Boolean.TRUE.equals(request.completed()));

        task.clearChecklistItems();
        if (request.checklist() != null) {
            int order = 0;
            for (ChecklistItemRequest item : request.checklist()) {
                task.addChecklistItem(new ChecklistItem(item.text(), item.done(), order++));
            }
        }
    }

    @PatchMapping("/api/tasks/{id}/move")
    @Transactional
    public TaskResponse move(@PathVariable String id, @Valid @RequestBody MoveRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Task task = findOrThrow(id);
        requireBoardOwnership(task.getBoardId(), userId);
        BoardColumn column = requireColumnInBoard(request.columnId(), task.getBoardId());
        String sourceColumnId = task.getColumnId();
        String targetColumnId = column.getId();

        List<Task> targetTasks = new ArrayList<>(taskRepository.findByColumnIdOrderByDisplayOrderAsc(targetColumnId));
        targetTasks.removeIf(t -> t.getId().equals(id));
        int index = Math.max(0, Math.min(request.displayOrder(), targetTasks.size()));
        targetTasks.add(index, task);
        task.setColumnId(targetColumnId);
        for (int i = 0; i < targetTasks.size(); i++) {
            targetTasks.get(i).setDisplayOrder(i);
        }
        taskRepository.saveAll(targetTasks);

        if (!sourceColumnId.equals(targetColumnId)) {
            renumberColumn(sourceColumnId);
        }
        return TaskResponse.from(task);
    }

    @PatchMapping("/api/tasks/{id}/complete")
    @Transactional
    public TaskResponse complete(@PathVariable String id, @Valid @RequestBody CompleteRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Task task = findOrThrow(id);
        requireBoardOwnership(task.getBoardId(), userId);
        task.setCompleted(request.completed());
        return TaskResponse.from(taskRepository.save(task));
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        Task task = findOrThrow(id);
        requireBoardOwnership(task.getBoardId(), userId);
        String columnId = task.getColumnId();
        taskRepository.deleteById(id);
        renumberColumn(columnId);
    }

    @PostMapping("/api/boards/{boardId}/tasks/bulk-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void bulkDelete(@PathVariable String boardId, @Valid @RequestBody BulkDeleteRequest request, HttpServletRequest httpRequest) {
        String userId = currentUser.require(httpRequest);
        requireBoardOwnership(boardId, userId);
        Set<String> ids = new LinkedHashSet<>(request.taskIds());
        List<Task> targets = taskRepository.findAllById(ids);
        // 1件でも見つからない・別ボードのタスクが混ざっていれば何も削除せずに失敗させる
        if (targets.size() != ids.size() || targets.stream().anyMatch(t -> !t.getBoardId().equals(boardId))) {
            throw ApiException.notFound("削除対象に存在しないタスクが含まれています");
        }
        Set<String> affectedColumnIds = new LinkedHashSet<>();
        targets.forEach(t -> affectedColumnIds.add(t.getColumnId()));
        taskRepository.deleteAll(targets);
        taskRepository.flush();
        affectedColumnIds.forEach(this::renumberColumn);
    }

    private void renumberColumn(String columnId) {
        List<Task> tasks = taskRepository.findByColumnIdOrderByDisplayOrderAsc(columnId);
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setDisplayOrder(i);
        }
        taskRepository.saveAll(tasks);
    }

    private void requireBoardOwnership(String boardId, String userId) {
        if (!boardRepository.existsByIdAndUserId(boardId, userId)) {
            throw ApiException.notFound("ボードが見つかりません: " + boardId);
        }
    }

    private BoardColumn requireColumnInBoard(String columnId, String boardId) {
        BoardColumn column = columnRepository.findById(columnId)
                .orElseThrow(() -> ApiException.notFound("列が見つかりません: " + columnId));
        if (!column.getBoardId().equals(boardId)) {
            throw ApiException.badRequest("指定した列はこのボードに属していません");
        }
        return column;
    }

    private Task findOrThrow(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("タスクが見つかりません: " + id));
    }
}
