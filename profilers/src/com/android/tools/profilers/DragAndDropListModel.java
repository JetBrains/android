/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers;

import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Model to be used when a list wants to support ordered drag and drop operations. This class currently only manages the ordering of
 * elements in a list. It supports re-ordering elements in a model, as well as caching the index that as
 * an element is added/moved, the element's position is preserved when it is removed/re-added
 *
 * All basic operation functions are overridden. We need to associate a unique ID with our element type.
 * Simply implementing a listener does not give us a way to map unique id (that can be defined ahead of time) with an element.
 * An example of where an ID is needed ahead of time is when we reset our list, we set our process id before we have the element for it.
 */
public class DragAndDropListModel<T extends DragAndDropModelListElement> extends DefaultListModel<T> {

  private static final String UNSUPPORTED_EXCEPTION_STRING =
    "Do not call DefaultListModel functions directly, use drag and drop operations for managing elements.";

  @NotNull private final OrderedSet<OrderedElement<T>> myOrderedElements = new OrderedSet<>();

  /**
   * When an element position is moved from one position to another, this function should be called to handle the moving. The new position
   * of the element will be above any null ordered elements. If a previously null element suddenly gets a value, the moved
   * element will appear above this new element.
   * @param element element in the model to move.
   * @param toPosition index of the list to move the element to.
   */
  public void moveElementTo(@NotNull T element, int toPosition) {
    OrderedElement<T> searchElement = new OrderedElement<>(element);
    int index = myOrderedElements.indexOf(searchElement);
    OrderedElement<T> orderedElement = myOrderedElements.remove(index);
    // This can happen if you drag an element to the end of the list. We insert the element at list.size however
    // we just removed one element as such our list size is now one less.
    if (toPosition > myOrderedElements.size()) {
      toPosition = myOrderedElements.size();
    }
    myOrderedElements.add(toPosition, orderedElement);
    super.removeElement(orderedElement.getElement());

    // This can happen if the last element is hidden and we try to move a new thread to the last element. In this case we want to
    // ensure that the moved elements gets pushed to the bottom of the list.
    if (toPosition > getSize()) {
      toPosition = getSize();
    }
    super.add(toPosition, orderedElement.getElement());
  }

  /**
   * Clear all elements and any ordering settings.
   */
  protected void clearOrderedElements() {
    myOrderedElements.clear();
    super.removeAllElements();
  }

  /**
   * Removes an element from the {@link DefaultListModel} however just marks it as absent from the ordered set. This helps
   * preserve the relative position of the element.
   * @param element element to be updated.
   */
  protected void removeOrderedElement(@NotNull T element) {
    OrderedElement<T> searchElement = new OrderedElement<>(element);
    int index = myOrderedElements.indexOf(searchElement);
    assert index != -1;
    super.removeElement(element);
    myOrderedElements.get(index).setElement(null);
  }

  /**
   * Inserts an element into the {@link DefaultListModel}. The element is inserted relative to other elements according to the
   * ordered set. The arrangement of the ordered set order is prioritized by element moves, and element insertion
   * order.
   * @param element object to be added to {@link DefaultListModel}.
   */
  protected void insertOrderedElement(@NotNull T element) {
    int i = 0;
    for (OrderedElement<T> orderedElement : myOrderedElements) {
      if (orderedElement.getId() == element.getId()) {
        orderedElement.setElement(element);
        super.add(i, element);
        return;
      }
      if (orderedElement.hasElement()) {
        i++;
      }
    }
    myOrderedElements.add(new OrderedElement<>(element));
    super.addElement(element);
  }

  @Override
  public void removeElementAt(int index) {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  @Override
  public void addElement(T element) {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  @Override
  public boolean removeElement(Object obj) {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  @Override
  public void removeAllElements() {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  @Override
  public void add(int index, T element) {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  @Override
  public T remove(int index) {
    throw new UnsupportedOperationException(UNSUPPORTED_EXCEPTION_STRING);
  }

  /**
   * Struct to store tuple of id's to elements used for storing ordering of elements relative to each other.
   * @param <T>
   */
  private static final class OrderedElement<T extends DragAndDropModelListElement> {
    private int myId;
    @Nullable
    private T myElement;

    public OrderedElement(@NotNull T element) {
      myId = element.getId();
      myElement = element;
    }

    /**
     * @return The id of the element when this OrderedElement was constructed.
     */
    public int getId() {
      return myId;
    }

    @Nullable
    public T getElement() {
      return myElement;
    }

    /**
     * @param element allows nulling of an element so we don't hold a reference to it, however this function does not change the id.
     */
    public void setElement(@Nullable T element) {
      myElement = element;
    }

    public boolean hasElement() {
      return myElement != null;
    }

    @Override
    public int hashCode() {
      return getId();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DragAndDropListModel.OrderedElement && getId() == ((OrderedElement)obj).getId();
    }
  }
}

