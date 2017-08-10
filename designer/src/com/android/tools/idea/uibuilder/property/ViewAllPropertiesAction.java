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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.uibuilder.property.ToggleXmlPropertyEditor.NL_XML_PROPERTY_EDITOR;

public class ViewAllPropertiesAction extends ToggleAction {
  public static final String VIEW_ALL_ATTRIBUTES = "View all attributes";
  public static final String VIEW_FEWER_ATTRIBUTES = "View fewer attributes";
  public static final String VIEW_XML_ATTRIBUTES = "View XML attributes";
  private final Model myModel;

  public interface Model {
    boolean isAllPropertiesPanelVisible();
    void setAllPropertiesPanelVisible(boolean viewAllProperties);
  }

  public ViewAllPropertiesAction(@NotNull Model model) {
    myModel = model;
    Presentation presentation = getTemplatePresentation();
    String text = getActualText();
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(StudioIcons.LayoutEditor.Properties.TOGGLE_PROPERTIES);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    String text = getActualText();
    presentation.setText(text);
    presentation.setDescription(text);
  }

  @NotNull
  private String getActualText() {
    if (myModel.isAllPropertiesPanelVisible()) {
      return VIEW_FEWER_ATTRIBUTES;
    }
    PropertiesComponent properties = PropertiesComponent.getInstance();
    return properties.getBoolean(NL_XML_PROPERTY_EDITOR) ? VIEW_XML_ATTRIBUTES : VIEW_ALL_ATTRIBUTES;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myModel.isAllPropertiesPanelVisible();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myModel.setAllPropertiesPanelVisible(state);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
