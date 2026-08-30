package uz.ithunter.crm.task.dto;

/** Body for POST /tasks/{id}/complete. Version is the optimistic-lock echo from the task. */
public record CompleteTaskRequest(long version) {
}
