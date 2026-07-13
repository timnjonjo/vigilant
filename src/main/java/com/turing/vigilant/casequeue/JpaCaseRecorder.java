package com.turing.vigilant.casequeue;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Postgres-backed {@link CaseRecorder}. */
@Component
public class JpaCaseRecorder implements CaseRecorder {

    private final com.turing.vigilant.casequeue.FraudCaseRepository repository;

    public JpaCaseRecorder(com.turing.vigilant.casequeue.FraudCaseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public long record(CaseOpening opening) {
        return repository.save(FraudCase.open(opening)).getId();
    }
}
