package uz.ithunter.crm.task.dto;

/** Body for POST /tasks/{id}/approve-result. comment is optional. */
public record ApproveResultRequest(String comment) {
}
