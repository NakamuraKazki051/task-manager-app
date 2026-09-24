package com.taskmanager.api.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class TaskDtos {

    private TaskDtos() {
    }

    public record ChecklistItemRequest(String text, boolean done) {
    }

    public record ChecklistItemResponse(String id, String text, boolean done) {
        public static ChecklistItemResponse from(ChecklistItem item) {
            return new ChecklistItemResponse(item.getId(), item.getText(), item.isDone());
        }
    }

    public record TaskRequest(
            @NotBlank String title,
            String description,
            String columnId,
            LocalDate dueDate,
            Priority priority,
            List<String> categories,
            List<ChecklistItemRequest> checklist,
            Boolean completed
    ) {
    }

    public record MoveRequest(@NotBlank String columnId, int displayOrder) {
    }

    public record CompleteRequest(boolean completed) {
    }

    public record BulkDeleteRequest(@NotEmpty List<String> taskIds) {
    }

    public record TaskResponse(
            String id,
            String boardId,
            String columnId,
            String title,
            String description,
            LocalDate dueDate,
            Priority priority,
            Instant createdAt,
            int displayOrder,
            List<String> categories,
            List<ChecklistItemResponse> checklist,
            boolean completed
    ) {
        public static TaskResponse from(Task task) {
            return new TaskResponse(
                    task.getId(),
                    task.getBoardId(),
                    task.getColumnId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getDueDate(),
                    task.getPriority(),
                    task.getCreatedAt(),
                    task.getDisplayOrder(),
                    task.getCategories(),
                    task.getChecklistItems().stream().map(ChecklistItemResponse::from).toList(),
                    task.isCompleted()
            );
        }
    }
}
