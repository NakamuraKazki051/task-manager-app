package com.taskmanager.api.column;

import jakarta.validation.constraints.NotBlank;

public class ColumnDtos {

    private ColumnDtos() {
    }

    public record ColumnRequest(@NotBlank String name, boolean done) {
    }

    public record ColumnUpdateRequest(String name, Boolean done) {
    }

    public record ColumnResponse(String id, String boardId, String name, boolean done, int displayOrder) {
        public static ColumnResponse from(BoardColumn column) {
            return new ColumnResponse(column.getId(), column.getBoardId(), column.getName(), column.isDone(), column.getDisplayOrder());
        }
    }
}
