/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup.backgroundremove;

import com.android.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;

/**
 * Class to save object for undo/redo by saving a maximum of object set by setMax
 */
public class HistoryManager<T> {

  private static final int DEFAULT_MAX_HISTORY = 10;
  private final ArrayDeque<T> myUndoStack = new ArrayDeque<>(DEFAULT_MAX_HISTORY);
  private final ArrayDeque<T> myRedoStack = new ArrayDeque<>(DEFAULT_MAX_HISTORY);
  private int myMaxHistory = DEFAULT_MAX_HISTORY;

  /**
   * Clear the undo stack and adds the provided image in it.
   * Used to set the first image of the history
   *
   * @param firstObject The image to add in the cleared undo stack
   */
  public void setOriginalImage(@NotNull T firstObject) {
    myUndoStack.clear();
    myUndoStack.addLast(firstObject);
  }

  /**
   * Set the maximum size that the undo stack can have.
   * If the maximum size is reached. The first element is removed from the stack.
   *
   * @param maxHistory
   */
  public void setMaxHistory(int maxHistory) {
    myMaxHistory = maxHistory;
  }

  /**
   * Add an object in the undo stack after clearing the redo stask
   *
   * @param object
   */
  public void pushUndo(@NotNull T object) {
    if (!myRedoStack.isEmpty()) {
      myRedoStack.clear();
    }
    myUndoStack.addLast(object);
    if (myUndoStack.size() > myMaxHistory) {
      myUndoStack.removeFirst();
    }
  }

  /**
   * Pop the first element of the undo stack and push this element in the redo stack.
   * If there is only one element in the stack, just peek it.
   * @return The new head of the undo stack
   */
  @Nullable
  public T undo() {
    if (canUndo()) {
      if (myUndoStack.size() == 1) {
        return myUndoStack.getLast();
      }
      T poppedObject = myUndoStack.removeLast();
      myRedoStack.addLast(poppedObject);
    }
    return getCurrentObject();
  }

  /**
   * @return The new head of the undo stack or null if the stack is empty
   */
  @Nullable
  public T getCurrentObject() {
    return myUndoStack.peekLast();
  }

  /**
   * Pop the first element of the redo stack and push this element in the undo stack.
   * @return The new head of the undo stack
   */
  @Nullable
  public T redo() {
    if (!myRedoStack.isEmpty()) {
      myUndoStack.addLast(myRedoStack.removeLast());
    }
    return getCurrentObject();
  }

  /**
   * @return true is there is an element to unstack from the undo stack
   */
  public boolean canUndo() {
    return myUndoStack.size() > 1;
  }

  /**
   * @return is the redo stack is not empty
   */
  public boolean canRedo() {
    return !myRedoStack.isEmpty();
  }
}
