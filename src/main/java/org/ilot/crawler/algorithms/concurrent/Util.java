package org.ilot.crawler.algorithms.concurrent;

import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

abstract class Util {

    static <E> boolean awaitNotEmpty(Deque<Node<E>> workDeque, Lock lock, Condition isEmpty, long timeout) {
        if (!workDeque.isEmpty()) {
            return true;
        }
        try {
            lock.lock();
            {
                while (workDeque.isEmpty()) {
                    try {
                        if (!isEmpty.await(timeout, TimeUnit.MILLISECONDS)) return false;
                    } catch (InterruptedException ignored) {
                        // nobody should interrupt the main thread
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            // shouldn't be thrown
            return false;
        } finally {
            lock.unlock();
        }
    }

    static void signalNotEmpty(Lock lock, Condition isEmpty) {
        try {
            lock.lock();
            isEmpty.signal();
        } catch (IllegalMonitorStateException e) {
            // shouldn't be thrown
            // TODO rethrow
        } finally {
            lock.unlock();
        }
    }
}