package com.taskmanager.api.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BoardDtos {

    private BoardDtos() {
    }

    public record BoardRequest(@NotBlank @Size(max = 30, message = "ボード名は30文字以内で入力してください") String name) {
    }

    public record BoardResponse(String id, String name) {
        public static BoardResponse from(Board board) {
            return new BoardResponse(board.getId(), board.getName());
        }
    }
}
