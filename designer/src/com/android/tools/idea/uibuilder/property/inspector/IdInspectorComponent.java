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
package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener;
import com.android.tools.idea.uibuilder.property.editors.NlLayoutEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ATTR_ID;

public class IdInspectorComponent implements InspectorComponent {
  private final NlReferenceEditor myIdField;
  private final NlLayoutEditor myLayoutEditor;

  private NlProperty myIdAttr;

  public IdInspectorComponent(@NotNull NlPropertiesManager propertiesManager) {
    myIdField = NlReferenceEditor.createForInspector(propertiesManager.getProject(), NlEditingListener.DEFAULT_LISTENER);
    myLayoutEditor = new NlLayoutEditor(propertiesManager.getProject());
  }

  @Override
  public void updateProperties(@NotNull List<NlComponent> components, @NotNull Map<String, NlProperty> properties) {
    myIdAttr = properties.get(ATTR_ID);
    myLayoutEditor.setSelectedComponents(properties);
  }

  @Override
  public int getMaxNumberOfRows() {
    return 6;
  }

  @Override
  public void attachToInspector(@NotNull InspectorPanel inspector) {
    if (myIdAttr != null) {
      inspector.addComponent("ID", getTooltip(myIdAttr), myIdField.getComponent());
    }
    inspector.addPanel(myLayoutEditor);
    addEditor(inspector, myLayoutEditor.getEnumPropertyEditor());
    addEditor(inspector, myLayoutEditor.getReferencePropertyEditor());
    addEditorWithLabelOnSeparateLine(inspector, myLayoutEditor.getGravityEditor());
    refresh();
  }

  private static void addEditor(@NotNull InspectorPanel inspector, @NotNull NlComponentEditor editor) {
    JLabel label = inspector.addComponent("", null, editor.getComponent());
    editor.setLabel(label);
    editor.setVisible(false);
  }

  private static void addEditorWithLabelOnSeparateLine(@NotNull InspectorPanel inspector, @NotNull NlComponentEditor editor) {
    JLabel label = inspector.addLabel("");
    inspector.addPanel(editor.getComponent());
    editor.setLabel(label);
    editor.setVisible(false);
  }

  @Nullable
  private static String getTooltip(@Nullable NlProperty property) {
    if (property == null) {
      return null;
    }

    return property.getTooltipText();
  }

  @Override
  public void refresh() {
    boolean enabled = myIdAttr != null;
    myIdField.setEnabled(enabled);
    if (enabled) {
      myIdField.setProperty(myIdAttr);
    }
    myLayoutEditor.refresh();
  }
}
