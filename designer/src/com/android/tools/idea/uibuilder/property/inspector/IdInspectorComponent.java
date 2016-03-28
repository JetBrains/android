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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlEnumEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;


public class IdInspectorComponent implements InspectorComponent, ActionListener, NlEnumEditor.Listener {
  @NonNull private final NlPropertiesManager myPropertiesManager;

  private final NlProperty myIdAttr;
  private final NlProperty myWidthAttr;
  private final NlProperty myHeightAttr;

  private final JBTextField myIdTextField;
  private final NlEnumEditor myWidthEditor;
  private final NlEnumEditor myHeightEditor;

  public IdInspectorComponent(@Nullable NlComponent component,
                              @NonNull Map<String, NlProperty> properties,
                              @NonNull NlPropertiesManager propertiesManager) {
    myPropertiesManager = propertiesManager;

    myIdAttr = properties.get(SdkConstants.ATTR_ID);
    myWidthAttr = properties.get(SdkConstants.ATTR_LAYOUT_WIDTH);
    myHeightAttr = properties.get(SdkConstants.ATTR_LAYOUT_HEIGHT);

    myIdTextField = new JBTextField();
    myWidthEditor = new NlEnumEditor(this);
    myHeightEditor = new NlEnumEditor(this);

    myIdTextField.addActionListener(this);
  }

  @Override
  public void attachToInspector(@NonNull JPanel inspector) {
    InspectorPanel.addComponent(inspector, "ID", getTooltip(myIdAttr), myIdTextField);
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
    boolean en = myIdAttr != null;
    myIdTextField.setEnabled(en);
    myIdTextField.setText(en ? StringUtil.notNullize(myIdAttr.getValue()) : "");

    en = myWidthAttr != null;
    myWidthEditor.setEnabled(en);
    if (en) {
      myWidthEditor.setProperty(myWidthAttr);
    }

    en = myHeightAttr != null;
    myHeightEditor.setEnabled(en);
    if (en) {
      myHeightEditor.setProperty(myHeightAttr);
    }
  }

  @Override
  public void actionPerformed(@NonNull ActionEvent e) {
    myPropertiesManager.setValue(myIdAttr, getText(myIdTextField));
  }

  private static String getText(@NonNull JBTextField textField) {
    Document doc = textField.getDocument();
    try {
      return doc.getText(0, doc.getLength());
    }
    catch (BadLocationException e) {
      return "";
    }
  }

  @Override
  public void itemPicked(@NonNull NlEnumEditor source, @NonNull String value) {
    NlProperty property = source == myWidthEditor ? myWidthAttr : myHeightAttr;
    myPropertiesManager.setValue(property, value);
  }

  @Override
  public void resourcePicked(@NonNull NlEnumEditor source, @NonNull String value) {
    itemPicked(source, value);
  }

  @Override
  public void resourcePickerCancelled(@NonNull NlEnumEditor source) {
  }
}
