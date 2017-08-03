/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.adtui.ui;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A component that displays breadcrumbs to allow navigation.
 */
public class NavigationComponent<T extends NavigationComponent.Item> extends JEditorPane {
  private boolean myDisplaySingleRoot;

  /**
   * Base class for the Breadcrumbs
   */
  public static abstract class Item {
    @NotNull
    public abstract String getDisplayText();
  }

  /**
   * Listener to receive notifications when items are selected.
   */
  public interface ItemListener<T extends NavigationComponent.Item> {
    void itemSelected(@NotNull T item);
  }

  private final ArrayList<ItemListener<T>> myItemListeners = new ArrayList<ItemListener<T>>();
  private final LinkedList<T> myItemStack = new LinkedList<T>();
  private boolean hasRootItem = false;

  public NavigationComponent() {
    setEditable(false);
    setContentType(UIUtil.HTML_MIME);
    putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

    // Disable links decoration.
    ((HTMLDocument)getDocument()).getStyleSheet().addRule("a { text-decoration:none; }");

    addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }

        int idx = Integer.parseInt(e.getDescription());
        final T item = myItemStack.get(idx);

        for (final ItemListener<T> listener : myItemListeners) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              listener.itemSelected(item);
            }
          });
        }
      }
    });
  }

  public void addItemListener(@NotNull ItemListener<T> itemListener) {
    myItemListeners.add(itemListener);
  }

  public void removeItemListener(@NotNull ItemListener<T> itemListener) {
    myItemListeners.remove(itemListener);
  }

  private void updateText() {
    if (myItemStack.isEmpty()) {
      setText("");
      return;
    }

    if (myItemStack.size() == 1 && hasRootItem && !myDisplaySingleRoot) {
      setText("");
      return;
    }

    final AtomicInteger id = new AtomicInteger(myItemStack.size() - 1);
    String text = Joiner.on(" &gt; ").join(Iterators.transform(myItemStack.descendingIterator(), new Function<T, String>() {
      @Override
      public String apply(T input) {
        // Do not display link for the last element.
        if (id.get() == 0) {
          return input.getDisplayText();
        }
        return String.format("<a href=\"%d\">%s</a>", id.getAndDecrement(), input.getDisplayText());
      }

      @Override
      public boolean equals(Object object) {
        return false;
      }
    }));

    setText(text);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) {
      return super.getMinimumSize();
    }

    // The height of the JEditorPane on MacOS doesn't not update correctly when setting text if the height was previously 0.
    // This makes sure that we use our own calculated minimum size in that situation so it works in every platform.
    boolean hasContentToDisplay = myDisplaySingleRoot ? hasRootItem :  myItemStack.size() > 1;
    return hasContentToDisplay ? new Dimension(0, getFontMetrics(getFont()).getHeight()) : super.getMinimumSize();
  }

  /**
   * Sets whether the component should display the root component if it's the only one in the navigation stack.
   */
  public void setDisplaySingleRoot(boolean displaySingleRoot) {
    myDisplaySingleRoot = displaySingleRoot;
  }

  /**
   * Sets a root item that is always present in the navigation stack. The root item cannot be removed by {@link #pop}.
   * @param item the root item value or null to remove the root.
   */
  public void setRootItem(@Nullable T item) {
    if (hasRootItem) {
      myItemStack.removeFirst();
    }

    hasRootItem = item != null;
    myItemStack.addFirst(item);

    updateText();
  }

  /**
   * Adds an element to the navigation stack.
   */
  public void push(@NotNull T item) {
    if (item.equals(peek())) {
      return;
    }
    myItemStack.push(item);

    updateText();
  }

  /**
   * Removes an element from the navigation stack.
   */
  @Nullable
  public T pop() {
    if (myItemStack.size() == 1 && hasRootItem) {
      // The root item can not be removed
      return null;
    }

    T removed = myItemStack.pop();
    updateText();

    return removed;
  }

  /**
   * Returns the current element at the top of the navigation stack.
   */
  @Nullable
  public T peek() {
    return myItemStack.peek();
  }

  /**
   * Unwinds the navigation stack until the passed item is found. If the item is not found the whole stack will be cleared with the
   * exception of the root element.
   */
  public void goTo(@NotNull T goToItem) {
    T item;
    while ((item = peek()) != null) {
      if (goToItem.equals(item)) {
        return;
      }

      if (pop() == null) {
        // End of the list.
        return;
      }
    }
  }

  @Override
  public Dimension getMaximumSize() {
    if (isMaximumSizeSet()) {
      return super.getMaximumSize();
    }

    // By default, allow only one line.
    return new Dimension(Integer.MAX_VALUE, getFontMetrics(getFont()).getHeight());
  }
}
