package com.taskmanager.api.task;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    private String text;
    private boolean done;
    private int displayOrder;

    protected ChecklistItem() {
    }

    public ChecklistItem(String text, boolean done, int displayOrder) {
        this.text = text;
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

    public void setTask(Task task) {
        this.task = task;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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
}
