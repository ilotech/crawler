package org.ilot.crawler.algorithms.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class ExecutorServiceConfiguration {
    private static final int numberOfProcessorThreads = Runtime.getRuntime().availableProcessors();

    private final ExecutorService executorService;

    public ExecutorServiceConfiguration(ExecutorServiceType executorServiceType, double blockingCoefficient) {
        int numberOfExecutorThreads = (int) Math.round(numberOfProcessorThreads - 1/blockingCoefficient);
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            this.executorService = Executors.newFixedThreadPool(numberOfExecutorThreads);
        } else {
            this.executorService = new ForkJoinPool(numberOfExecutorThreads);
        }
    }

    public ExecutorServiceConfiguration(ExecutorServiceType executorServiceType, int numberOfExecutorThreads) {
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            this.executorService = Executors.newFixedThreadPool(numberOfExecutorThreads);
        } else {
            this.executorService = new ForkJoinPool(numberOfExecutorThreads);
        }
    }

    public ExecutorServiceConfiguration(ExecutorServiceType executorServiceType) {
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            this.executorService = Executors.newFixedThreadPool(numberOfProcessorThreads);
        } else {
            this.executorService = ForkJoinPool.commonPool();
        }
    }

    public ExecutorService getExecutorService() {
        return this.executorService;
    }
}