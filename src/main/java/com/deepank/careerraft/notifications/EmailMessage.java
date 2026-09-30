package com.deepank.careerraft.notifications;

import java.nio.file.Path;
import java.util.List;

public record EmailMessage(
        String to,
        String subject,
        String text,
        String html,
        List<Path> attachments
) {
    public EmailMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public EmailMessage(String to, String subject, String text, List<Path> attachments) {
        this(to, subject, text, null, attachments);
    }
}
