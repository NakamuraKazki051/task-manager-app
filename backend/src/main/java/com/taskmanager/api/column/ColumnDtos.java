package com.taskmanager.api.column;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ColumnDtos {

    private ColumnDtos() {
    }

    public record ColumnRequest(@NotBlank @Size(max = 30, message = "列名は30文字以内で入力してください") String name, boolean done) {
    }

    public record ColumnUpdateRequest(@Size(max = 30, message = "列名は30文字以内で入力してください") String name, Boolean done) {
    }

    public record ColumnMoveRequest(int displayOrder) {
    }

    public record ColumnResponse(String id, String boardId, String name, boolean done, int displayOrder) {
        public static ColumnResponse from(BoardColumn column) {
            return new ColumnResponse(column.getId(), column.getBoardId(), column.getName(), column.isDone(), column.getDisplayOrder());
        }
    }
}
