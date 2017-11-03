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
package com.android.tools.adtui.workbench;

import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link SideModel} keeps track of all registered tool windows for a {@link WorkBench}.
 * At any given time there can be at most 4 visible docked tool windows and 1 auto hide
 * tool window.<br/>
 * The {@link SideModel} is notified about all changes to any of the tool windows. After
 * a given change the visible tools are computed and listeners are notified that there is
 * a new layout of the tool windows.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
class SideModel<T> {
  private final List<AttachedToolWindow<T>> myAllTools;
  private final VisiblePair<T> myLeftVisibleTools;
  private final VisiblePair<T> myRightVisibleTools;
  private final List<Listener<T>> myListeners;
  private final Project myProject;
  private T myContext;
  private AttachedToolWindow<T> myVisibleAutoHideTool;

  public SideModel(@NotNull Project project) {
    myAllTools = new ArrayList<>(8);
    myLeftVisibleTools = new VisiblePair<>();
    myRightVisibleTools = new VisiblePair<>();
    myListeners = new ArrayList<>(2);
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public void setContext(T context) {
    myContext = context;
    myAllTools.forEach(tool -> tool.setContext(context));
  }

  public T getContext() {
    return myContext;
  }

  public void addListener(@NotNull Listener<T> listener) {
    myListeners.add(listener);
  }

  @NotNull
  public List<AttachedToolWindow<T>> getAllTools() {
    return myAllTools;
  }

  @NotNull
  public List<AttachedToolWindow> getVisibleTools(@NotNull Side side) {
    return getVisibleTools(side.isLeft()).asList();
  }

  @NotNull
  public List<AttachedToolWindow> getHiddenTools(@NotNull Side side) {
    return myAllTools.stream()
      .filter(tool -> tool.isMinimized() && !tool.isDetached() && !tool.isAutoHide() && tool.isLeft() == side.isLeft())
      .collect(Collectors.toList());
  }

  @NotNull
  public List<AttachedToolWindow> getTopTools(@NotNull Side side) {
    return myAllTools.stream()
      .filter(tool -> !tool.isDetached() && tool.isLeft() == side.isLeft() && (!tool.isSplit() || tool.isAutoHide()))
      .collect(Collectors.toList());
  }

  @NotNull
  public List<AttachedToolWindow> getBottomTools(@NotNull Side side) {
    return myAllTools.stream()
      .filter(tool -> !tool.isDetached() && tool.isLeft() == side.isLeft() && tool.isSplit() && !tool.isAutoHide())
      .collect(Collectors.toList());
  }

  @Nullable
  public AttachedToolWindow<T> getVisibleAutoHideTool() {
    return myVisibleAutoHideTool;
  }

  @NotNull
  public List<AttachedToolWindow<T>> getHiddenSliders() {
    return myAllTools.stream()
      .filter(tool -> tool.isAutoHide() && !tool.isDetached() && tool.isMinimized())
      .collect(Collectors.toList());
  }

  @NotNull
  public List<AttachedToolWindow<T>> getDetachedTools() {
    return myAllTools.stream()
      .filter(AttachedToolWindow::isDetached)
      .collect(Collectors.toList());
  }

  public void setTools(@NotNull List<AttachedToolWindow<T>> tools) {
    tools.forEach(this::add);
    updateLocally();
  }

  private void add(@NotNull AttachedToolWindow<T> tool) {
    myAllTools.add(tool);
    if (!tool.isMinimized() && !tool.isDetached()) {
      VisiblePair<T> visible = getVisibleTools(tool.isLeft());
      if (!visible.setIfEmpty(tool)) {
        tool.setMinimized(true);
      }
    }
  }

  public void changeToolSettingsAfterDragAndDrop(@NotNull AttachedToolWindow<T> tool,
                                                 @NotNull Side side,
                                                 @NotNull Split split,
                                                 int wantedSideToolIndex) {
    tool.setLeft(side.isLeft());
    tool.setSplit(split.isBottom());
    List<AttachedToolWindow> list = split.isBottom() ? getBottomTools(side) : getTopTools(side);
    int index = list.indexOf(tool);
    if (index != wantedSideToolIndex && wantedSideToolIndex >= 0 && wantedSideToolIndex < list.size()) {
      int insertAfter = index < wantedSideToolIndex ? 1 : 0;
      myAllTools.remove(tool);
      myAllTools.add(myAllTools.indexOf(list.get(wantedSideToolIndex)) + insertAfter, tool);
    }
    update(Collections.singletonList(tool), EventType.UPDATE_TOOL_ORDER);
  }

  public void update(@NotNull AttachedToolWindow<T> tool, @NotNull PropertyType typeOfChange) {
    update(Collections.singletonList(tool), typeOfChange == PropertyType.DETACHED ? EventType.UPDATE_DETACHED_WINDOW : EventType.UPDATE);
  }

  public void updateLocally() {
    myLeftVisibleTools.removeBoth();
    myRightVisibleTools.removeBoth();
    update(myAllTools, EventType.LOCAL_UPDATE);
  }

  private void update(@NotNull List<AttachedToolWindow<T>> tools, @NotNull EventType eventType) {
    for (AttachedToolWindow<T> tool : tools) {
      getVisibleTools(true).remove(tool);
      getVisibleTools(false).remove(tool);
      if (myVisibleAutoHideTool == tool) {
        myVisibleAutoHideTool = null;
      }
      if (!tool.isMinimized() && !tool.isDetached()) {
        if (tool.isAutoHide()) {
          if (myVisibleAutoHideTool != null) {
            myVisibleAutoHideTool.setMinimized(true);
          }
          myVisibleAutoHideTool = tool;
        }
        else {
          AttachedToolWindow<T> old = getVisibleTools(tool.isLeft()).set(tool.isSplit(), tool);
          if (old != null && old != tool) {
            old.setMinimized(true);
          }
        }
      }
    }
    notifyListeners(eventType);
  }

  public void swap() {
    myAllTools.forEach(tool -> tool.setLeft(!tool.isLeft()));
    myLeftVisibleTools.swap(myRightVisibleTools);
    notifyListeners(EventType.SWAP);
  }

  private void notifyListeners(@NotNull EventType type) {
    myListeners.forEach(listener -> listener.modelChanged(this, type));
  }

  @NotNull
  private VisiblePair<T> getVisibleTools(boolean isLeft) {
    return isLeft ? myLeftVisibleTools : myRightVisibleTools;
  }

  public static class VisiblePair<T> {
    @Nullable
    public AttachedToolWindow<T> myTop;

    @Nullable
    public AttachedToolWindow<T> myBottom;

    @Nullable
    public AttachedToolWindow<T> get(boolean isSplit) {
      return isSplit ? myBottom : myTop;
    }

    @Nullable
    public AttachedToolWindow<T> set(boolean isSplit, @Nullable AttachedToolWindow<T> tool) {
      AttachedToolWindow<T> old = get(isSplit);
      if (isSplit) {
        myBottom = tool;
      }
      else {
        myTop = tool;
      }
      return old;
    }

    public boolean setIfEmpty(@NotNull AttachedToolWindow<T> tool) {
      if (get(tool.isSplit()) != null) {
        return false;
      }
      set(tool.isSplit(), tool);
      return true;
    }

    public void remove(@NotNull AttachedToolWindow<T> tool) {
      if (myTop == tool) {
        myTop = null;
      }
      if (myBottom == tool) {
        myBottom = null;
      }
    }

    public void removeBoth() {
      myTop = null;
      myBottom = null;
    }

    public void swap(@NotNull VisiblePair<T> other) {
      myTop = other.set(false, myTop);
      myBottom = other.set(true, myBottom);
    }

    @NotNull
    public List<AttachedToolWindow> asList() {
      if (myTop == null && myBottom == null) {
        return ImmutableList.of();
      }
      else if (myTop == null || myBottom == null) {
        return ImmutableList.of(myTop == null ? myBottom : myTop);
      }
      else {
        return ImmutableList.of(myTop, myBottom);
      }
    }
  }

  public enum EventType {
    UPDATE, UPDATE_DETACHED_WINDOW, LOCAL_UPDATE, SWAP, UPDATE_TOOL_ORDER
  }

  public interface Listener<T> {
    void modelChanged(@NotNull SideModel<T> model, @NotNull EventType type);
  }
}
