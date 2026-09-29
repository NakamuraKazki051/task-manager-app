package com.taskmanager.api.board;

import com.taskmanager.api.task.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public class BoardImportDtos {

    private BoardImportDtos() {
    }

    public record ImportChecklistItemRequest(@Size(max = 200, message = "チェックリストの項目は200文字以内で入力してください") String text, boolean done) {
    }

    public record ImportColumnRequest(String id, @NotBlank @Size(max = 30, message = "列名は30文字以内で入力してください") String name, boolean done) {
    }

    public record ImportTaskRequest(
            @NotBlank String columnId,
            @NotBlank @Size(max = 100) String title,
            @Size(max = 500) String description,
            LocalDate dueDate,
            Priority priority,
            List<@Size(max = 30, message = "カテゴリは1つ30文字以内で入力してください") String> categories,
            List<@Valid ImportChecklistItemRequest> checklist,
            Boolean completed
    ) {
    }

    // 要素に @Valid を付けないと、列・タスクそれぞれの @NotBlank / @Size が検証されない
    public record ImportRequest(
            @NotEmpty List<@Valid ImportColumnRequest> columns,
            List<@Valid ImportTaskRequest> tasks
    ) {
    }
}
