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
package com.android.tools.idea.actions;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.AnimatedComponentSplitter;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.*;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Display or hide the mockup layer
 *
 * @see com.android.tools.idea.uibuilder.surface.MockupLayer
 */
public class MockupToggleAction extends ToggleAction {
  public static final Dimension CLOSED_DIMENSION = new Dimension(0, 0);
  public static final Dimension OPEN_DIMENSION = new Dimension(200, 200);
  private final DesignSurface mySurface;

  private final static String SHOW_ACTION_TITLE = "Show Mockup Editor";
  private final static String HIDE_ACTION_TITLE = "Hide Mockup Editor";

  public MockupToggleAction(@NotNull DesignSurface surface) {
    mySurface = surface;
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(getDesignIcon());
    presentation.setDescription(getDescription());
  }


  @NotNull
  private String getDescription() {
    return mySurface.isCanvasResizing() ? HIDE_ACTION_TITLE : SHOW_ACTION_TITLE;
  }

  private static Icon getDesignIcon() {
    return AndroidIcons.Mockup.Mockup;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return mySurface.isMockupVisible();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getTemplatePresentation().setEnabled(state);
    mySurface.setMockupVisible(state);
    final MockupEditor mockupEditor = mySurface.getMockupEditor();
    if (mockupEditor != null) {
      if (mockupEditor.getParent() instanceof AnimatedComponentSplitter) {
        mockupEditor.setMaximumSize(state ? OPEN_DIMENSION : CLOSED_DIMENSION);
        AnimatedComponentSplitter animatedComponentSplitter = (AnimatedComponentSplitter)mockupEditor.getParent();
        animatedComponentSplitter.showAnimateChild(mockupEditor, state);
        animatedComponentSplitter.setDividerMouseZoneSize(state ? 1 : 0);
        animatedComponentSplitter.setDividerWidth(state ? 1 : 0);
      }
      else {
        mockupEditor.setSize(mySurface.getWidth() / 3, mySurface.getHeight());
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabledAndVisible(Mockup.ENABLE_FEATURE);
    event.getPresentation().setIcon(getDesignIcon());
    getTemplatePresentation().setDescription(getDescription());
  }
}
