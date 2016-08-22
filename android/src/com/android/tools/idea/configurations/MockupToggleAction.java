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
package com.android.tools.idea.configurations;

import com.android.tools.idea.uibuilder.mockup.editor.AnimatedComponentSplitter;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.*;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Display or hide the mockup layer
 *
 * @see com.android.tools.idea.uibuilder.surface.MockupLayer
 */
public class MockupToggleAction extends ToggleAction {
  private final DesignSurface mySurface;

  private final static String TOGGLE_ACTION_TITLE = "Show/Hide Mockup";

  public MockupToggleAction(@NotNull DesignSurface surface) {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(getDesignIcon(surface.isMockupVisible()));
    presentation.setDescription(TOGGLE_ACTION_TITLE);
    mySurface = surface;
  }

  private static Icon getDesignIcon(boolean active) {
    return active ? AndroidIcons.NeleIcons.DesignPropertyEnabled
                  : AndroidIcons.NeleIcons.DesignProperty;
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
        ((AnimatedComponentSplitter)mockupEditor.getParent())
          .showAnimateChild(mockupEditor, state);
      }
      else {
        mockupEditor.setSize(mySurface.getWidth() / 3, mySurface.getHeight());
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setIcon(getDesignIcon(mySurface.isMockupVisible()));
  }
}
