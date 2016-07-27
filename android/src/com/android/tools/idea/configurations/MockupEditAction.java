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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.mockup.MockupEditorPopup;
import com.android.tools.idea.uibuilder.mockup.tools.MockupEditor;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Shows the popup for editing the mockup of the selected component
 */
public class MockupEditAction extends AnAction {

  private final static String EDIT_ACTION_TITLE = "Edit Mockup";
  private final static String ADD_ACTION_TITLE = "Add Mockup";
  private final ScreenView myCurrentScreenView;

  public MockupEditAction(DesignSurface surface) {
    super(ADD_ACTION_TITLE);

    myCurrentScreenView = surface.getCurrentScreenView();
    if (myCurrentScreenView != null) {
      List<NlComponent> selection = myCurrentScreenView.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = myCurrentScreenView.getModel().getComponents();
      }
      if (!selection.isEmpty()) {
        NlComponent nlComponent = selection.get(0);
        Presentation presentation = getTemplatePresentation();

        // If the selected component already has a mockup attribute, display the Edit text
        // else display the add text
        if (nlComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP) != null) {
          presentation.setText(EDIT_ACTION_TITLE);
        }
        else {
          presentation.setText(ADD_ACTION_TITLE);
        }
      }
      else {
        getTemplatePresentation().setEnabled(false);
      }
    }
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myCurrentScreenView != null) {
      List<NlComponent> selection = myCurrentScreenView.getSelectionModel().getSelection();
      if(selection.isEmpty()) {
        selection = myCurrentScreenView.getModel().getComponents();
      }
      if (!selection.isEmpty()) {
        NlComponent nlComponent = selection.get(0);
        if ((e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK) {
          MockupEditorPopup.create(myCurrentScreenView, nlComponent);
        }
        else {
          MockupEditor.create(myCurrentScreenView, nlComponent);
        }
      }
    }
  }
}
