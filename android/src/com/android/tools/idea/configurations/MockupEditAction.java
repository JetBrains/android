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

import com.android.tools.idea.uibuilder.mockup.MockupEditorPopup;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Menu to control the Mockup Layer
 */
public class MockupEditAction extends FlatComboAction {
  private final DesignSurface mySurface;
  private AnAction myMainAction;

  private final static String TOGGLE_ACTION_TITLE =  "Show/Hide Mockup";
  private final static String EDIT_ACTION_TITLE =  "Edit Mockup";

  public MockupEditAction(@Nullable DesignSurface surface) {
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(getDesignIcon(surface != null && surface.isMockupVisible()));
    myMainAction = new ToggleShowMockupAction();
    presentation.setDescription(myMainAction.getTemplatePresentation().getText());
    mySurface = surface;
  }

  private static Icon getDesignIcon(boolean active) {
    return active ? AndroidIcons.NeleIcons.DesignPropertyEnabled
                  : AndroidIcons.NeleIcons.DesignProperty;
  }

  @Override
  public void update(AnActionEvent event) {
    event.getPresentation().setIcon(getDesignIcon(mySurface != null && mySurface.isMockupVisible()));
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup(null, true);
    group.add(myMainAction);
    if(mySurface != null && mySurface.getCurrentScreenView() != null ) {
      group.add(new DisplayMockupEditorAction(mySurface.getCurrentScreenView().getModel()));
    }
    return group;
  }

  @Override
  protected boolean handleIconClicked() {
    myMainAction.actionPerformed(null);
    return true;
  }

  /**
   * Action to display or hide the MockupLayer
   */
  private class ToggleShowMockupAction extends AnAction {

    public ToggleShowMockupAction() {
      super(TOGGLE_ACTION_TITLE, null, getDesignIcon(mySurface != null && mySurface.isMockupVisible()));
    }

    @Override
    public void actionPerformed(@Nullable  AnActionEvent e) {
      final boolean mockupVisible = MockupEditAction.this.mySurface != null && !MockupEditAction.this.mySurface.isMockupVisible();
      getTemplatePresentation().setIcon(getDesignIcon(mockupVisible));
      if (MockupEditAction.this.mySurface != null) {
        MockupEditAction.this.mySurface.setMockupVisible(!MockupEditAction.this.mySurface.isMockupVisible());
        MockupEditAction.this.mySurface.repaint();
      }
    }
  }

  /**
   * Shows the popup for editing the mockup of the selected component
   */
  private class DisplayMockupEditorAction extends AnAction {

    @NotNull NlModel myModel;

    public DisplayMockupEditorAction(@NotNull NlModel model) {
      super(EDIT_ACTION_TITLE, null, AndroidIcons.NeleIcons.DesignPropertyEnabled);
      myModel = model;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      MockupEditorPopup.create(mySurface, myModel);
    }
  }
}
