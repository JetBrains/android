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
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;

/**
 * Action which navigates to the primary selected XML element
 */
public class GotoComponentAction extends AnAction {
  private final DesignSurface mySurface;

  public GotoComponentAction(DesignSurface surface) {
    super("Go to XML");
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView != null) {
      SelectionModel selectionModel = screenView.getSelectionModel();
      NlComponent primary = selectionModel.getPrimary();
      if (primary != null) {
        XmlTag tag = primary.getTag();
        if (tag.isValid()) {
          PsiNavigateUtil.navigate(tag);
        }
      }
    }
  }
}
