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

import com.intellij.util.containers.Queue;
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
   * Return a stream of components, starting from the current root up until no more parents can be
   * found.
   */
  public Stream<Component> ancestorStream() {
    return streamFromIterator(new AncestorIterator(myRoot));
  }

  /**
   * Return components so they can be iterated over.
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
   * Return a stream of components, starting from the current root and walking breadth-first until
   * all children are visited (e.g. all in first generation, then second generation, etc.)
   */
  public Stream<Component> descendantStream() {
    return streamFromIterator(new DescendantIterator(myRoot));
  }


  /**
   * Return components so they can be iterated over.
   */
  public Iterable<Component> descendants() {
    return new Iterable<Component>() {
      @Override
      public Iterator<Component> iterator() {
        return new DescendantIterator(myRoot);
      }
    };
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

  private static final class DescendantIterator implements Iterator<Component> {
    private final Queue<Component> myDescendants = new Queue<>(10);

    public DescendantIterator(Component root) {
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
}