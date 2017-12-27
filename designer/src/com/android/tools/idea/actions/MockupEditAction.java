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

import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.mockup.editor.MockUpFileChooser;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.base.Strings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Shows the popup for editing the mockup of the selected component
 */
public class MockupEditAction extends AnAction {
  private final static String EDIT_ACTION_TITLE = "Edit Mockup";
  private final static String ADD_ACTION_TITLE = "Add Mockup";

  private final MockupToggleAction myMockupToggleAction;
  private final NlDesignSurface myDesignSurface;

  public MockupEditAction(@NotNull NlDesignSurface designSurface) {
    super(ADD_ACTION_TITLE);

    if (!StudioFlags.NELE_MOCKUP_EDITOR.get()) {
      getTemplatePresentation().setEnabledAndVisible(false);
      myMockupToggleAction = null;
      myDesignSurface = null;
      return;
    }

    myDesignSurface = designSurface;
    myMockupToggleAction = new MockupToggleAction(designSurface);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    if (StudioFlags.NELE_MOCKUP_EDITOR.get()) {
      // If the selected component already has a mock-up attribute, display the Edit text
      // else display the add text
      NlComponent component = getFirstSelectedComponent();
      if (component == null) {
        presentation.setEnabled(false);
      }
      else if (component.getAttribute(TOOLS_URI, ATTR_MOCKUP) != null) {
        presentation.setText(EDIT_ACTION_TITLE);
      }
      else {
        presentation.setText(ADD_ACTION_TITLE);
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myMockupToggleAction.setSelected(event, true);
    NlComponent component = getFirstSelectedComponent();
    if (component == null) {
      return;
    }
    myMockupToggleAction.setSelected(event, true);
    MockUpFileChooser.INSTANCE.chooseMockUpFile(
      component,
      (path) -> NlWriteCommandAction.run(component, Strings.nullToEmpty(event.getPresentation().getText()), () -> {
        component.setAttribute(TOOLS_URI, ATTR_MOCKUP, path);
        component.setAttribute(TOOLS_URI, ATTR_MOCKUP_CROP, "");
      })
    );
  }

  @Nullable
  public NlComponent getFirstSelectedComponent() {
    ScreenView screenView = myDesignSurface.getCurrentSceneView();
    if (screenView == null) {
      return null;
    }
    List<NlComponent> selection = screenView.getSelectionModel().getSelection();
    if (selection.isEmpty()) {
      selection = screenView.getModel().getComponents();
    }
    if (selection.isEmpty()) {
      return null;
    }
    return selection.get(0);
  }
}
