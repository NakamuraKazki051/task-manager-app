package com.taskmanager.api.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class TaskDtos {

    private TaskDtos() {
    }

    public record ChecklistItemRequest(@Size(max = 200, message = "チェックリストの項目は200文字以内で入力してください") String text, boolean done) {
    }

    public record ChecklistItemResponse(String id, String text, boolean done) {
        public static ChecklistItemResponse from(ChecklistItem item) {
            return new ChecklistItemResponse(item.getId(), item.getText(), item.isDone());
        }
    }

    public record TaskRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            @NotBlank String columnId,
            LocalDate dueDate,
            Priority priority,
            // リスト自体ではなく中の要素を検証するため、型引数側に付ける
            List<@Size(max = 30, message = "カテゴリは1つ30文字以内で入力してください") String> categories,
            List<@Valid ChecklistItemRequest> checklist,
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
