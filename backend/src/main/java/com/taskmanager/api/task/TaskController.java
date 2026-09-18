package com.taskmanager.api.task;

import com.taskmanager.api.common.ApiException;
import com.taskmanager.api.task.TaskDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/api/boards/{boardId}/tasks")
    public List<TaskResponse> list(
            @PathVariable String boardId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "manual") String sort
    ) {
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

        Comparator<Task> comparator = switch (sort) {
            case "due" -> Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "priority" -> Comparator.comparing(t -> priorityRank(t.getPriority()));
            default -> Comparator.comparingInt(Task::getDisplayOrder);
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
    public TaskResponse get(@PathVariable String id) {
        return TaskResponse.from(findOrThrow(id));
    }

    @PostMapping("/api/boards/{boardId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@PathVariable String boardId, @Valid @RequestBody TaskRequest request) {
        int nextOrder = taskRepository.findByBoardId(boardId).size();
        Task task = new Task(boardId, request.columnId(), request.title().trim());
        applyRequest(task, request);
        task.setDisplayOrder(nextOrder);
        return TaskResponse.from(taskRepository.save(task));
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(@PathVariable String id, @Valid @RequestBody TaskRequest request) {
        Task task = findOrThrow(id);
        task.setTitle(request.title().trim());
        applyRequest(task, request);
        return TaskResponse.from(taskRepository.save(task));
    }

    private void applyRequest(Task task, TaskRequest request) {
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority() != null ? request.priority() : Priority.MID);
        task.setCategories(request.categories());
        task.setColumnId(request.columnId());

        task.clearChecklistItems();
        if (request.checklist() != null) {
            int order = 0;
            for (ChecklistItemRequest item : request.checklist()) {
                task.addChecklistItem(new ChecklistItem(item.text(), item.done(), order++));
            }
        }
    }

    @PatchMapping("/api/tasks/{id}/move")
    public TaskResponse move(@PathVariable String id, @Valid @RequestBody MoveRequest request) {
        Task task = findOrThrow(id);
        task.setColumnId(request.columnId());
        task.setDisplayOrder(request.displayOrder());
        return TaskResponse.from(taskRepository.save(task));
    }

    @DeleteMapping("/api/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        if (!taskRepository.existsById(id)) {
            throw ApiException.notFound("タスクが見つかりません: " + id);
        }
        taskRepository.deleteById(id);
    }

    private Task findOrThrow(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("タスクが見つかりません: " + id));
    }
}
