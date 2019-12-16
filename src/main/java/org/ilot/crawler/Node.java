package org.ilot.crawler;

import java.util.Objects;

public class Node<E> {
    private final E element;
    private final int level;

    public Node(E element, int level) {
        this.element = element;
        this.level = level;
    }

    public E getElement() {
        return element;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node<?> node = (Node<?>) o;
        return level == node.level &&
                Objects.equals(element, node.element);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, level);
    }

    @Override
    public String toString() {
        return "Node{" +
                "element=" + element +
                ", level=" + level +
                '}';
    }
}