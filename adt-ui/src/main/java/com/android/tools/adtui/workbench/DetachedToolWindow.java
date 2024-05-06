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

import static com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType.DETACHED;
import static com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType.FLOATING;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a detached tool window which is essentially an Intellij {@link ToolWindowEx}.
 * At any given time a detached tool window has a corresponding {@link AttachedToolWindow},
 * which it can be turned into.
 *
 * @param <T> Specifies the type of data controlled by the {@link WorkBench}.
 */

class DetachedToolWindow<T> implements ToolWindowCallback, Disposable {
  private final ToolContent<T> myContent;
  private final ToolWindowEx myToolWindow;
  private AttachedToolWindow<T> myCorrespondingToolWindow;

  DetachedToolWindow(@NotNull Project project,
                     @NotNull String workBenchName,
                     @NotNull ToolWindowDefinition<T> definition) {
    this(definition, workBenchName, ToolWindowManager.getInstance(project));
  }

  private DetachedToolWindow(@NotNull ToolWindowDefinition<T> definition,
                             @NotNull String workBenchName,
                             @NotNull ToolWindowManager toolWindowManager) {
    myContent = definition.getFactory().apply(this);
    myToolWindow = createToolWindow(toolWindowManager, workBenchName, definition);
  }

  public void show(@NotNull AttachedToolWindow<T> correspondingWindow) {
    updateState(correspondingWindow);
    myContent.setToolContext(correspondingWindow.getContext());
    myContent.registerCallbacks(this);
    myToolWindow.setAvailable(true);
    myToolWindow.setType(toToolWindowType(correspondingWindow), null);
    myToolWindow.setSplitMode(correspondingWindow.isSplit(), null);
    myToolWindow.show(null);
  }

  public void hide() {
    myContent.setToolContext(null);
    myToolWindow.setAvailable(false);
  }

  @Override
  public void restore() {
    if (myToolWindow.isAvailable() && !myToolWindow.isVisible()) {
      myToolWindow.show(null);
    }
  }

  @NotNull
  public static <T> String idOf(@NotNull String workBenchName, @NotNull ToolWindowDefinition<T> definition) {
    return String.format("%s - %s", workBenchName, definition.getTitle());
  }

  @NotNull
  private ToolWindowType toToolWindowType(@NotNull AttachedToolWindow<T> attachedToolWindow) {
    if (attachedToolWindow.isFloating()) {
      return ToolWindowType.FLOATING;
    }
    if (attachedToolWindow.isAutoHide()) {
      return ToolWindowType.SLIDING;
    }
    return ToolWindowType.DOCKED;
  }

  private void updateState(@NotNull AttachedToolWindow<T> correspondingWindow) {
    myCorrespondingToolWindow = correspondingWindow;
  }

  public void setMinimized(boolean value) {
    if (myCorrespondingToolWindow != null) {
      myCorrespondingToolWindow.setMinimized(value);
    }
  }

  public boolean isMinimized() {
    if (myCorrespondingToolWindow == null) {
      return false;
    }
    return myCorrespondingToolWindow.isMinimized();
  }

  private ToolWindowEx createToolWindow(@NotNull ToolWindowManager toolWindowManager,
                                        @NotNull String workBenchName,
                                        @NotNull ToolWindowDefinition<T> definition) {
    String id = idOf(workBenchName, definition);
    ToolWindowEx window = (ToolWindowEx)toolWindowManager.getToolWindow(id);
    if (window == null) {
      ToolWindowAnchor anchor = definition.getSide().isLeft() ? ToolWindowAnchor.LEFT : ToolWindowAnchor.RIGHT;
      window = (ToolWindowEx)toolWindowManager.registerToolWindow(id, false, anchor, this, true);
      window.setIcon(definition.getIcon());
      window.setAutoHide(false);
      setToolWindowContent(window);
      setAdditionalGearPopupActions(window);
      setAdditionalActions(window);
    }
    return window;
  }

  private void setToolWindowContent(@NotNull ToolWindowEx toolWindow) {
    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.getSelectedContent();
    if (content == null) {
      content = contentManager.getFactory().createContent(myContent.getComponent(), null, false);
      content.setCloseable(false);
      content.setComponent(myContent.getComponent());
      content.setPreferredFocusableComponent(myContent.getFocusedComponent());
      content.setShouldDisposeContent(true);
      contentManager.addContent(content);
      contentManager.setSelectedContent(content, true);
    }
  }

  private void setAdditionalActions(@NotNull ToolWindow toolWindow) {
    List<AnAction> actionList = myContent.getAdditionalActions();
    if (!actionList.isEmpty()) {
      toolWindow.setTitleActions(actionList);
    }
  }

  private void setAdditionalGearPopupActions(@NotNull ToolWindowEx toolWindow) {
    DefaultActionGroup attachedSide = DefaultActionGroup.createPopupGroup(() -> "Attached Side");
    attachedSide.add(new AttachToSideAction(Side.LEFT));
    attachedSide.add(new AttachToSideAction(Side.RIGHT));
    attachedSide.add(new DetachedAction());
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(attachedSide));
  }

  @Override
  public void dispose() {
  }

  public void updateSettingsInAttachedToolWindow() {
    if (myCorrespondingToolWindow != null) {
      myCorrespondingToolWindow.setAutoHide(myToolWindow.getType() == ToolWindowType.SLIDING);
      myCorrespondingToolWindow.setFloating(myToolWindow.getType() == ToolWindowType.FLOATING);
      myCorrespondingToolWindow.setSplit(myToolWindow.isSplitMode());
    }
  }

  private class AttachToSideAction extends DumbAwareAction {
    private final Side mySide;

    private AttachToSideAction(@NotNull Side side) {
      super(side.isLeft() ? "Left" : "Right");
      mySide = side;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      event.getPresentation().setEnabledAndVisible(myCorrespondingToolWindow != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      if (myCorrespondingToolWindow != null) {
        myToolWindow.setAvailable(false);
        updateSettingsInAttachedToolWindow();
        myCorrespondingToolWindow.setLeft(mySide.isLeft());
        myCorrespondingToolWindow.setPropertyAndUpdate(DETACHED, false);
        myCorrespondingToolWindow.setPropertyAndUpdate(FLOATING, false);
      }
    }
  }

  private static class DetachedAction extends DumbAwareToggleAction {

    private DetachedAction() {
      super("None");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent event) {
      return true;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      // Dummy action. The tool window is always detached when using a real Intellij ToolWindow.
      // Note that the AttachToSideAction will reset the DETACHED state.
    }
  }
}
