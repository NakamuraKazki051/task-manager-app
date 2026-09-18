package com.taskmanager.api.task;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private String id;

    private String boardId;
    private String columnId;

    private String title;

    @Column(length = 500)
    private String description;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MID;

    private Instant createdAt;
    private int displayOrder;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "task_categories", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "category")
    @OrderColumn(name = "position")
    private List<String> categories = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    private List<ChecklistItem> checklistItems = new ArrayList<>();

    protected Task() {
    }

    public Task(String boardId, String columnId, String title) {
        this.boardId = boardId;
        this.columnId = columnId;
        this.title = title;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addChecklistItem(ChecklistItem item) {
        item.setTask(this);
        checklistItems.add(item);
    }

    public void clearChecklistItems() {
        checklistItems.clear();
    }

    // getters / setters
    public String getId() { return id; }
    public String getBoardId() { return boardId; }
    public String getColumnId() { return columnId; }
    public void setColumnId(String columnId) { this.columnId = columnId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Instant getCreatedAt() { return createdAt; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) {
        this.categories.clear();
        if (categories != null) {
            this.categories.addAll(categories);
        }
    }
    public List<ChecklistItem> getChecklistItems() { return checklistItems; }
}
