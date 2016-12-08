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

import com.android.tools.idea.uibuilder.mockup.editor.FileChooserActionListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.android.dom.attrs.ToolsAttributeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.RenderService.MOCKUP_EDITOR_ENABLED;

/**
 * Shows the popup for editing the mockup of the selected component
 */
public class MockupEditAction extends AnAction {

  private static FileChooserActionListener ourFileChooserActionListener = new FileChooserActionListener();
  private final static String EDIT_ACTION_TITLE = "Edit Mockup";
  private final static String ADD_ACTION_TITLE = "Add Mockup";
  private final MockupToggleAction myMockupToggleAction;

  public MockupEditAction(@NotNull DesignSurface designSurface) {
    super(ADD_ACTION_TITLE);

    if (!MOCKUP_EDITOR_ENABLED) {
      getTemplatePresentation().setEnabledAndVisible(false);
      myMockupToggleAction = null;
      return;
    }

    myMockupToggleAction = new MockupToggleAction(designSurface);
    ScreenView screenView = designSurface.getCurrentScreenView();
    if (screenView != null) {
      List<NlComponent> selection = screenView.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = screenView.getModel().getComponents();
      }
      if (!selection.isEmpty()) {
        NlComponent nlComponent = selection.get(0);
        Presentation presentation = getTemplatePresentation();

        // If the selected component already has a mockup attribute, display the Edit text
        // else display the add text
        if (nlComponent.getAttribute(TOOLS_URI, ATTR_MOCKUP) != null) {
          presentation.setText(EDIT_ACTION_TITLE);
        }
        else {
          presentation.setText(ADD_ACTION_TITLE);
        }

        // When changing the mockup, we want to change both the file and reset the cropping
        // so we add the two properties
        ourFileChooserActionListener.setFilePathProperty(new NlPropertyItem(
          Collections.singletonList(nlComponent),
          TOOLS_URI, ToolsAttributeUtil.getAttrDefByName(ATTR_MOCKUP)));

        ourFileChooserActionListener.setCropProperty(new NlPropertyItem(
          Collections.singletonList(nlComponent),
          TOOLS_URI, ToolsAttributeUtil.getAttrDefByName(ATTR_MOCKUP_CROP)));
      }
      else {
        getTemplatePresentation().setEnabled(false);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myMockupToggleAction.setSelected(e, true);
    ourFileChooserActionListener.actionPerformed(null);
  }
}
