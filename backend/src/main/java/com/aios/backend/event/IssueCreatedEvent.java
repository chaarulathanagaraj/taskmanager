package com.aios.backend.event;

import com.aios.backend.model.IssueEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event fired when a new issue is created.
 * Used to trigger async processing like AI diagnosis.
 */
@Getter
public class IssueCreatedEvent extends ApplicationEvent {

    private final IssueEntity issue;

    public IssueCreatedEvent(Object source, IssueEntity issue) {
        super(source);
        this.issue = issue;
    }
}
