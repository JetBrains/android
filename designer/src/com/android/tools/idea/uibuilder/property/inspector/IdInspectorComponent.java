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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.util.Map;

public class IdInspectorComponent implements InspectorComponent {
  @NotNull private final NlPropertiesManager myPropertiesManager;

  private final NlProperty myIdAttr;
  private final NlProperty myWidthAttr;
  private final NlProperty myHeightAttr;

  private final NlReferenceEditor myIdField;
  private final NlEnumEditor myWidthEditor;
  private final NlEnumEditor myHeightEditor;

  public IdInspectorComponent(@NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    myPropertiesManager = propertiesManager;

    myIdAttr = properties.get(SdkConstants.ATTR_ID);
    myWidthAttr = properties.get(SdkConstants.ATTR_LAYOUT_WIDTH);
    myHeightAttr = properties.get(SdkConstants.ATTR_LAYOUT_HEIGHT);

    NlEnumEditor.Listener enumListener = createEnumListener();

    myIdField = NlReferenceEditor.createForInspector(propertiesManager.getProject(), createReferenceListener());
    myWidthEditor = NlEnumEditor.createForInspector(enumListener);
    myHeightEditor = NlEnumEditor.createForInspector(enumListener);
  }

  @Override
  public void attachToInspector(@NotNull JPanel inspector) {
    InspectorPanel.addComponent(inspector, "ID", getTooltip(myIdAttr), myIdField.getComponent());
    InspectorPanel.addSeparator(inspector);
    InspectorPanel.addComponent(inspector, "Width", getTooltip(myWidthAttr), myWidthEditor.getComponent());
    InspectorPanel.addComponent(inspector, "Height", getTooltip(myHeightAttr), myHeightEditor.getComponent());
    refresh();
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

    enabled = myWidthAttr != null;
    myWidthEditor.setEnabled(enabled);
    if (enabled) {
      myWidthEditor.setProperty(myWidthAttr);
    }

    enabled = myHeightAttr != null;
    myHeightEditor.setEnabled(enabled);
    if (enabled) {
      myHeightEditor.setProperty(myHeightAttr);
    }
  }

  private static String getText(@NotNull JBTextField textField) {
    Document doc = textField.getDocument();
    try {
      return doc.getText(0, doc.getLength());
    }
    catch (BadLocationException e) {
      return "";
    }
  }

  private NlEnumEditor.Listener createEnumListener() {
    return new NlEnumEditor.Listener() {
      @Override
      public void itemPicked(@NotNull NlEnumEditor source, @NotNull String value) {
        NlProperty property = source == myWidthEditor ? myWidthAttr : myHeightAttr;
        myPropertiesManager.setValue(property, value);
      }

      @Override
      public void resourcePicked(@NotNull NlEnumEditor source, @NotNull String value) {
        itemPicked(source, value);
      }

      @Override
      public void resourcePickerCancelled(@NotNull NlEnumEditor source) {
      }
    };
  }

  private NlReferenceEditor.EditingListener createReferenceListener() {
    return new NlReferenceEditor.EditingListener() {
      @Override
      public void stopEditing(@NotNull NlReferenceEditor source, @NotNull String value) {
        if (source.getProperty() != null) {
          myPropertiesManager.setValue(source.getProperty(), value);
          source.setProperty(source.getProperty());
        }
      }

      @Override
      public void cancelEditing(@NotNull NlReferenceEditor editor) {
      }
    };
  }
}
