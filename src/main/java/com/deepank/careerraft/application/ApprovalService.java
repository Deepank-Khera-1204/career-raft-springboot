package com.deepank.careerraft.application;

import com.deepank.careerraft.repository.JobRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class ApprovalService {
    private final JobRepository repository;

    public ApprovalService(JobRepository repository) {
        this.repository = repository;
    }

    public ApprovalCommand request(String jobId) {
        String normalized = jobId.toUpperCase();
        String token = "YES-" + normalized;
        repository.createApproval(normalized, token);
        return new ApprovalCommand(normalized, token);
    }

    public boolean validate(ApprovalCommand command) {
        Optional<JobRepository.ApprovalRow> approval = repository.getApproval(command.jobId());
        return approval.isPresent()
                && "PENDING".equals(approval.get().state())
                && approval.get().token().equals(command.token());
    }

    public Optional<JobRepository.ApprovalRow> get(String jobId) {
        return repository.getApproval(jobId);
    }
}
