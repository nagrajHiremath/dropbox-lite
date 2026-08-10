package com.dropbox.account_service.config;

import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

/**
 * Returns a fixed, ordered sequence of backoff intervals (matching
 * TECHNICAL_DESIGN.md's literal "2 sec, 10 sec, 30 sec" example) then STOP -
 * Spring's built-in ExponentialBackOff can't hit that exact non-multiplicative
 * sequence. Mirrors metadata-service's copy of the same class exactly.
 */
class FixedSequenceBackOff implements BackOff {

    private final long[] intervalsMs;

    FixedSequenceBackOff(long... intervalsMs) {
        this.intervalsMs = intervalsMs;
    }

    @Override
    public BackOffExecution start() {
        return new BackOffExecution() {
            private int index = 0;

            @Override
            public long nextBackOff() {
                if (index < intervalsMs.length) {
                    return intervalsMs[index++];
                }
                return BackOffExecution.STOP;
            }
        };
    }
}
