package com.taskmanager.api.column;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "board_columns")
public class BoardColumn {

    @Id
    private String id;

    private String boardId;
    private String name;
    private boolean done;
    private int displayOrder;

    protected BoardColumn() {
    }

    public BoardColumn(String boardId, String name, boolean done, int displayOrder) {
        this.boardId = boardId;
        this.name = name;
        this.done = done;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public String getBoardId() {
        return boardId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
