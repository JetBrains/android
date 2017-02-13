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
package com.android.tools.profilers.common;

import com.android.tools.adtui.model.AspectModel;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.profilers.common.ThreadId.INVALID_THREAD_ID;

/**
 * A model which represents a list of stack frames that each make up a stack trace. Furthermore,
 * you can select a line via the {@link #setSelectedIndex(int)} method, which will represent a user's
 * intention to navigate to that line of code.
 */
public final class StackTraceModel extends AspectModel<StackTraceModel.Aspect> {
  public enum Aspect {
    STACK_FRAMES,
    SELECTED_LOCATION,
  }

  public enum Type {
    INVALID,
    STACK_FRAME,
    THREAD_ID,
  }

  private static final int INVALID_INDEX = -1;

  @NotNull
  private ThreadId myThreadId;

  @NotNull
  private List<CodeLocation> myStackFrames;

  private int mySelectedIndex = INVALID_INDEX;

  public StackTraceModel() {
    myStackFrames = Collections.emptyList();
    myThreadId = INVALID_THREAD_ID;
  }

  public void setStackFrames(@NotNull ThreadId threadId, @NotNull List<CodeLocation> stackFrames) {
    updateStackFrames(threadId, ImmutableList.copyOf(stackFrames));
  }

  public void setStackFrames(@NotNull String trace) {
    updateStackFrames(INVALID_THREAD_ID, Arrays.stream(trace.split("\\n")).map(
      stackFrame -> {
        StackFrameParser parser = new StackFrameParser(stackFrame);
        return new CodeLocation(parser.getClassName(), parser.getFileName(), parser.getMethodName(), parser.getLineNumber() - 1);
      }).collect(Collectors.toList()));
  }

  public void clearStackFrames() {
    updateStackFrames(INVALID_THREAD_ID, Collections.emptyList());
  }

  private void updateStackFrames(@NotNull ThreadId threadId, @NotNull List<CodeLocation> stackFrames) {
    clearSelection();
    myStackFrames = stackFrames;
    myThreadId = threadId;
    changed(Aspect.STACK_FRAMES);
  }

  @NotNull
  public ThreadId getThreadId() {
    return myThreadId;
  }

  @NotNull
  public List<CodeLocation> getCodeLocations() {
    return myStackFrames;
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public Type getSelectedType() {
    if (mySelectedIndex >= 0 && mySelectedIndex < myStackFrames.size()) {
      return Type.STACK_FRAME;
    }
    else if (mySelectedIndex == myStackFrames.size()) {
      return Type.THREAD_ID;
    }
    else {
      return Type.INVALID;
    }
  }

  /**
   * Selects a target {@link CodeLocation} by using an index into the list returned by
   * {@link #getCodeLocations()}.
   *
   * If out of bounds, e.g. -1, this will clear the selection, but for clarity you should
   * prefer to use {@link #clearSelection()} instead.
   */
  public void setSelectedIndex(int index) {
    int size = myStackFrames.size() + (INVALID_THREAD_ID.equals(myThreadId) ? 0 : 1);
    int newIndex = index >= 0 && index < size ? index : INVALID_INDEX;
    boolean indexChanging = newIndex != mySelectedIndex;
    mySelectedIndex = index;
    if (indexChanging) {
      changed(Aspect.SELECTED_LOCATION);
    }
  }

  /**
   * Clear the selected stack frame.
   */
  public void clearSelection() {
    setSelectedIndex(INVALID_INDEX);
  }
}
