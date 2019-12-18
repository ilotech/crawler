package org.ilot.crawler.algorithms.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

public class ExecutorServiceFactory {
    private static final int numberOfProcessorThreads = Runtime.getRuntime().availableProcessors();

    public static ExecutorService createDefaultExecutorService(ExecutorServiceType executorServiceType) {
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            return Executors.newFixedThreadPool(numberOfProcessorThreads);
        } else {
            return ForkJoinPool.commonPool();
        }
    }

    public static ExecutorService createCustomExecutorService(ExecutorServiceType executorServiceType, int numberOfExecutorThreads) {
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            return Executors.newFixedThreadPool(numberOfExecutorThreads);
        } else {
            return new ForkJoinPool(numberOfExecutorThreads);
        }
    }

    public static ExecutorService createCustomExecutorService(ExecutorServiceType executorServiceType, double blockingCoefficient) {
        // from Venkat Subramaniam - Programming Concurrency on the JVM
        int numberOfExecutorThreads = (int) Math.round(numberOfProcessorThreads / (1 - blockingCoefficient));
        if (executorServiceType == ExecutorServiceType.EXECUTOR_SERVICE) {
            return Executors.newFixedThreadPool(numberOfExecutorThreads);
        } else {
            return new ForkJoinPool(numberOfExecutorThreads);
        }
    }
}