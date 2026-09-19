package com.taskmanager.api.board;

import com.taskmanager.api.task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;

public class BoardImportDtos {

    private BoardImportDtos() {
    }

    public record ImportChecklistItemRequest(String text, boolean done) {
    }

    public record ImportColumnRequest(String id, @NotBlank String name, boolean done) {
    }

    public record ImportTaskRequest(
            @NotBlank String columnId,
            @NotBlank String title,
            String description,
            LocalDate dueDate,
            Priority priority,
            List<String> categories,
            List<ImportChecklistItemRequest> checklist
    ) {
    }

    public record ImportRequest(
            @NotEmpty List<ImportColumnRequest> columns,
            List<ImportTaskRequest> tasks
    ) {
    }
}
