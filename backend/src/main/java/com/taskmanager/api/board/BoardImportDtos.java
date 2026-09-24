package com.taskmanager.api.board;

import com.taskmanager.api.task.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

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
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            LocalDate dueDate,
            Priority priority,
            List<String> categories,
            List<ImportChecklistItemRequest> checklist,
            Boolean completed
    ) {
    }

    public record ImportRequest(
            @NotEmpty List<ImportColumnRequest> columns,
            List<ImportTaskRequest> tasks
    ) {
    }
}
