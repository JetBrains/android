/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class ShowExpertProperties extends ToggleAction {
  private final Model myModel;

  public interface Model {
    boolean isShowingExpertProperties();
    void setShowExpertProperties(boolean en);
  }

  public ShowExpertProperties(@NotNull Model model) {
    Presentation presentation = getTemplatePresentation();
    String text = "Show expert properties";
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(AndroidIcons.NeleIcons.ToggleProperties);

    myModel = model;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myModel.isShowingExpertProperties();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myModel.setShowExpertProperties(state);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
