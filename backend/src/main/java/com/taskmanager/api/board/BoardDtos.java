package com.taskmanager.api.board;

import jakarta.validation.constraints.NotBlank;

public class BoardDtos {

    private BoardDtos() {
    }

    public record BoardRequest(@NotBlank String name) {
    }

    public record BoardResponse(String id, String name) {
        public static BoardResponse from(Board board) {
            return new BoardResponse(board.getId(), board.getName());
        }
    }
}
