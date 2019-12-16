package org.ilot.crawler;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings({"WeakerAccess", "unused"})
public class Algorithms {
    private Algorithms() { }

    public static class BFS<E> extends GraphSearch<E> {
        private BFS(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            super(workDequeue, visited, Deque::poll, getNeighboursFunction);
        }

        private BFS(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction, Predicate<Node<E>> searchPredicate) {
            super(workDequeue, visited, Deque::poll, getNeighboursFunction, searchPredicate);
        }

        public static <E> BFS createSingleThreadingBFS(Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            return new BFS<>(new LinkedList<>(), new HashSet<>(), getNeighboursFunction);
        }
    }

    public static class DFS<E> extends GraphSearch<E> {
        private DFS(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction);
        }

        private DFS(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction, Predicate<Node<E>> searchPredicate) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction, searchPredicate);
        }

        public static <E> DFS createSingleThreadingDFS(Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            return new DFS<>(new ArrayDeque<>(), new HashSet<>(), getNeighboursFunction);
        }
    }

    public static class UnorderedSearch<E> extends GraphSearch<E> {
        private UnorderedSearch(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction);
        }

        private UnorderedSearch(ExecutorService executorService, Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            super(executorService, workDequeue, visited, Deque::pop, getNeighboursFunction);
        }

        private UnorderedSearch(Deque<Node<E>> workDequeue, Set<Node<E>> visited, Function<Node<E>, Set<Node<E>>> getNeighboursFunction, Predicate<Node<E>> searchPredicate) {
            super(workDequeue, visited, Deque::pop, getNeighboursFunction, searchPredicate);
        }

        public static <E> UnorderedSearch createConcurrentUnorderedSearch(Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            return new UnorderedSearch<>(Executors.newFixedThreadPool(72), new LinkedBlockingDeque<>(), new ConcurrentHashMap<>().newKeySet(), getNeighboursFunction);
        }
    }

    private static abstract class GraphSearch<E> {
        // TODO create config class
        private final Deque<Node<E>> workDequeue;
        private final Set<Node<E>> visited;
        private final Function<Deque<Node<E>>, Node<E>> getElementFunction;
        private final Function<Node<E>, Set<Node<E>>> getNeighboursFunction;
        private Predicate<Node<E>> searchPredicate;

        private AtomicLong levelCounter = new AtomicLong(0L);
        // TODO make final
        private ExecutorService executorService;
        private final Lock lock = new ReentrantLock(true);
        private final Lock taskLock = new ReentrantLock(true);
        private final Condition isEmpty = lock.newCondition();

        private GraphSearch(Deque<Node<E>> workDequeue,
                            Set<Node<E>> visited,
                            Function<Deque<Node<E>>, Node<E>> getElementFunction,
                            Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            this.workDequeue = workDequeue;
            this.visited = visited;
            this.getElementFunction = getElementFunction;
            this.getNeighboursFunction = getNeighboursFunction;
        }

        private GraphSearch(ExecutorService executorService,
                            Deque<Node<E>> workDequeue,
                            Set<Node<E>> visited,
                            Function<Deque<Node<E>>, Node<E>> getElementFunction,
                            Function<Node<E>, Set<Node<E>>> getNeighboursFunction) {
            this.executorService = executorService;
            this.workDequeue = workDequeue;
            this.visited = visited;
            this.getElementFunction = getElementFunction;
            this.getNeighboursFunction = getNeighboursFunction;
        }

        private GraphSearch(Deque<Node<E>> workDequeue,
                            Set<Node<E>> visited,
                            Function<Deque<Node<E>>, Node<E>> getElementFunction,
                            Function<Node<E>, Set<Node<E>>> getNeighboursFunction,
                            Predicate<Node<E>> searchPredicate) {
            this(workDequeue, visited, getElementFunction, getNeighboursFunction);
            this.searchPredicate = searchPredicate;
        }

        public void traverse(Node<E> rootElement) {
            workDequeue.add(rootElement);
            System.out.println("Thread -> " + Thread.currentThread() + " starting threads");
            while (awaitNotEmpty(5000L)) {
                levelCounter.incrementAndGet();
                System.out.println("Thread -> " + Thread.currentThread() + "new thread started");

                Runnable runnable = () -> {
                    System.out.println("Thread -> " + Thread.currentThread() + "starting new thread");
                    System.out.println("Thread -> " + Thread.currentThread() + "\t removing an element from the deque");
                    Node<E> element;
                    try {
                        element = getElementFunction.apply(workDequeue);
                    } catch (Exception ignored) {
                        return;
                    }

                    if (visited.contains(element)) {
                        return;
                    }
                    visited.add(element);
                    System.out.println("Thread -> " + Thread.currentThread() + "\tCurrent element -> " + element.toString());

                    Set<Node<E>> neighbours;
                    try {
                        neighbours = getNeighboursFunction.apply(element)
                                .stream()
                                .filter(e -> !visited.contains(e))
                                .collect(Collectors.toSet());
                    } catch (Exception e) {
                        System.out.println("Thread -> " + Thread.currentThread() + "\texception occurred while getting neighbours for -> " + element.toString());
                        return;
                    }

                    if (neighbours.isEmpty()) {
                        System.out.println("Thread -> " + Thread.currentThread() + "\tno neighbours found for element -> " + element.toString());
                    }

                    if (neighbours.isEmpty() || !workDequeue.addAll(neighbours)) return;
                    System.out.println("Thread -> " + Thread.currentThread() + "\tadding all neighbours to queue -> " + neighbours.size());
                    try {
                        lock.lock();
                        isEmpty.signal();
                    } catch (Exception e) {
                        // TODO log
                    } finally {
                        lock.unlock();
                    }

                    System.out.println("Thread -> " + Thread.currentThread() + "\t signaling all\n WorkDeque -> " + workDequeue.toString());
                };

                System.out.println("Thread -> " + Thread.currentThread() + " executing runnable");
                try {
                    Thread.sleep(500);
                    System.out.println("Thread -> " + Thread.currentThread() + " workDeuqe size -> " + workDequeue.size());

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                executorService.execute(runnable);
//                if (levelCounter.intValue() >= 100000) {
//                    System.out.println("Thread -> " + Thread.currentThread() + " reached 100000, exiting");
//                    executorService.shutdownNow();
//                    break;
//                }
            }
        }

        private boolean awaitNotEmpty(long timeout) {
            if (!workDequeue.isEmpty()) {
                return true;
            }
            try {
                lock.lock();
                System.out.println("Thread -> " + Thread.currentThread() + "\t acquired lock awaitNotEmpty");
                {
                    while (workDequeue.isEmpty()) {
                        try {
                            if (!isEmpty.await(timeout, TimeUnit.MILLISECONDS)) {
                                System.out.println("Thread -> " + Thread.currentThread() + "\t empty deque timeout, shutting down\n WorkDeque -> " + workDequeue.toString());
                                return false;
                            } else {
                                System.out.println("Thread -> " + Thread.currentThread() + "\t waiting\n WorkDeque -> " + workDequeue.toString());
                            }
                        } catch (InterruptedException e) {
                            return false;
                        } finally {
                            System.out.println("Thread -> " + Thread.currentThread() + "\t releasing lock awaitNotEmpty");
                        }
                    }
                    return true;
                }
            } catch (Exception e) {
                return false;
                // TODO log exception
            } finally {
                lock.unlock();
            }

        }

        private void shutdown() {
            if (levelCounter.intValue() >= 100) {
                executorService.shutdownNow();
            }
        }

        // TODO revisit method
//        public E search(E rootElement) {
//            if (searchPredicate == null) {
//                // TODO revisit message
//                throw new IllegalStateException("Search predicate must be defined whn using search function.");
//            }
//            workDequeue.add(rootElement);
//            while (!workDequeue.isEmpty()) {
//                E element = getElementFunction.apply(workDequeue);
//                if (searchPredicate.test(element)) {
//                    return element;
//                }
//                if (visited.contains(element)) {
//                    continue;
//                }
//                visited.add(element);
//                System.out.println("Current element -> " + element);
//
//                Set<E> neighbours = getNeighboursFunction.apply(element)
//                        .stream()
//                        .filter(e -> !visited.contains(e))
//                        .collect(Collectors.toSet());
//
//                workDequeue.addAll(neighbours);
//            }
//            // TODO revisit
//            throw new NoSuchElementException("Search element not found.");
//        }
    }
}