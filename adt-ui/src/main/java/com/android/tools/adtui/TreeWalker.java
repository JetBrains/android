/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui;

import com.intellij.util.Function;
import com.intellij.util.containers.Queue;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class that returns streams for walking up and/or down a component tree from some given
 * source component.
 *
 * Example:
 * <pre>
 *   TreeWalker walker = new TreeWalker(myRoot);
 *   Component found = walker.descendantStream().
 *      filter(c -> c.getName().equals("some-id")).
 *      findFirst();
 *
 *   for (Component c : walker.ancestors()) {
 *      System.out.println(c.getName() + ": " + c.getPreferredSize());
 *   }
 * </pre>
 */
public final class TreeWalker {
  private final Component myRoot;

  private static Stream<Component> streamFromIterator(Iterator<Component> componentIterator) {
    return StreamSupport.stream(Spliterators.spliteratorUnknownSize(componentIterator,
                                                                    Spliterator.ORDERED |
                                                                    Spliterator.DISTINCT |
                                                                    Spliterator.NONNULL |
                                                                    Spliterator.IMMUTABLE), false);
  }

  public static boolean isAncestor(@NotNull Container ancestor, @NotNull Component child) {
    if (child == ancestor) {
      return true;
    }
    else if (child.getParent() == null) {
      return false;
    }
    else {
      return isAncestor(ancestor, child.getParent());
    }
  }

  public TreeWalker(@NotNull Component root) {
    myRoot = root;
  }

  /**
   * Return a stream of ancestors, starting from the current root up until no more parents can be
   * found.
   */
  public Stream<Component> ancestorStream() {
    return streamFromIterator(new AncestorIterator(myRoot));
  }

  /**
   * Return ancestors so they can be iterated over.
   */
  public Iterable<Component> ancestors() {
    return new Iterable<Component>() {
      @Override
      public Iterator<Component> iterator() {
        return new AncestorIterator(myRoot);
      }
    };
  }

  /**
   * Return a stream of descendants, starting from the current root and walking down until
   * all children are visited.
   *
   * @param order Whether the order should be depth first or breadth first
   */
  public Stream<Component> descendantStream(DescendantOrder order) {
    return streamFromIterator(order.createIterator.fun(myRoot));
  }


  /**
   * Return descendants so they can be iterated over.
   *
   * @param order Whether the order should be depth first or breadth first
   */
  public Iterable<Component> descendants(DescendantOrder order) {
    return new Iterable<Component>() {
      @Override
      public Iterator<Component> iterator() {
        return order.createIterator.fun(myRoot);
      }
    };
  }

  /**
   * Returns a descendant stream in breadth-first order.
   */
  public Stream<Component> descendantStream() {
    return descendantStream(DescendantOrder.BREADTH_FIRST);
  }

  /**
   * Return descendants so they can be iterated over in breadth-first order.
   */
  public Iterable<Component> descendants() {
    return descendants(DescendantOrder.BREADTH_FIRST);
  }

  public enum DescendantOrder {
    BREADTH_FIRST(BfsDescendantIterator::new),
    DEPTH_FIRST(DfsDescendantIterator::new);

    final Function<Component, Iterator<Component>> createIterator;

    DescendantOrder(Function<Component, Iterator<Component>> createIterator) {
      this.createIterator = createIterator;
    }
  }

  private static final class AncestorIterator implements Iterator<Component> {
    Component currComponent;

    public AncestorIterator(Component root) {
      currComponent = root;
    }

    @Override
    public boolean hasNext() {
      return currComponent != null;
    }

    @Override
    public Component next() {
      Component next = currComponent;
      currComponent = currComponent.getParent();
      return next;
    }
  }

  private static final class BfsDescendantIterator implements Iterator<Component> {
    private final Queue<Component> myDescendants = new Queue<>(10);

    public BfsDescendantIterator(Component root) {
      myDescendants.addLast(root);
    }

    @Override
    public boolean hasNext() {
      return !myDescendants.isEmpty();
    }

    @Override
    public Component next() {
      Component c = myDescendants.pullFirst();
      // When we visit a component, enqueue any children it may have, so that if the iterator
      // continues to run, we'll visit its children later, in a breadth-first manner.
      if (c instanceof Container) {
        for (Component child : ((Container)c).getComponents()) {
          myDescendants.addLast(child);
        }
      }

      return c;
    }
  }

  private static final class DfsDescendantIterator implements Iterator<Component> {
    private final Stack<Component> myDescendants = new Stack<>();

    public DfsDescendantIterator(Component root) {
      myDescendants.push(root);
    }

    @Override
    public boolean hasNext() {
      return !myDescendants.isEmpty();
    }

    @Override
    public Component next() {
      Component c = myDescendants.pop();
      // When we visit a component, enqueue any children it may have, so that if the iterator
      // continues to run, we'll visit its children later, in a depth-first manner.
      if (c instanceof Container) {
        for (int i = ((Container)c).getComponentCount() - 1; i >= 0; i--) {
          Component child = ((Container)c).getComponent(i);
          myDescendants.push(child);
        }
      }

      return c;
    }
  }
}