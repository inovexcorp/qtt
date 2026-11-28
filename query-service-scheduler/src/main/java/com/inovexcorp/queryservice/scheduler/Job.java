package com.inovexcorp.queryservice.scheduler;

/** A scheduled QTT job. Each implementation also declares a Quarkus {@code @Scheduled} trigger that calls it. */
public interface Job {
    void execute(JobContext context);
}
