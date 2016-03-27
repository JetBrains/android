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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;


public class IdInspectorComponent implements InspectorComponent, ActionListener {
  @Nullable private final NlComponent myComponent;
  @NonNull private final NlPropertiesManager myPropertiesManager;

  private final NlProperty myIdAttr;
  private final NlProperty myWidthAttr;
  private final NlProperty myHeightAttr;

  private final JBTextField myIdTextField;
  private final JBTextField myWidthTextField;
  private final JBTextField myHeightTextField;

  public IdInspectorComponent(@Nullable NlComponent component,
                              @NonNull Map<String, NlProperty> properties,
                              @NonNull NlPropertiesManager propertiesManager) {
    myComponent = component;
    myPropertiesManager = propertiesManager;

    myIdAttr = properties.get(SdkConstants.ATTR_ID);
    myWidthAttr = properties.get(SdkConstants.ATTR_LAYOUT_WIDTH);
    myHeightAttr = properties.get(SdkConstants.ATTR_LAYOUT_HEIGHT);

    myIdTextField = new JBTextField();
    myWidthTextField = new JBTextField();
    myHeightTextField = new JBTextField();

    myIdTextField.addActionListener(this);
    myWidthTextField.addActionListener(this);
    myHeightTextField.addActionListener(this);
  }

  @Override
  public void attachToInspector(@NonNull JPanel inspector) {
    InspectorPanel.addComponent(inspector, "ID", myIdTextField);
    InspectorPanel.addSeparator(inspector);
    InspectorPanel.addComponent(inspector, "Width", myWidthTextField);
    InspectorPanel.addComponent(inspector, "Height", myHeightTextField);
    refresh();
  }

  @Override
  public void refresh() {
    boolean en = myIdAttr != null;
    myIdTextField.setEnabled(en);
    myIdTextField.setText(en ? StringUtil.notNullize(myIdAttr.getValue()) : "");

    en = myWidthAttr != null;
    myWidthTextField.setEnabled(en);
    myWidthTextField.setText(en ? StringUtil.notNullize(myWidthAttr.getValue()) : "");

    en = myHeightAttr != null;
    myHeightTextField.setEnabled(en);
    myHeightTextField.setText(en ? StringUtil.notNullize(myHeightAttr.getValue()) : "");
  }

  @Override
  public void actionPerformed(@NonNull ActionEvent e) {
    Object source = e.getSource();
    NlProperty property = null;
    String value = null;

    if (myIdTextField == source) {
      property = myIdAttr;
      value = getText(myIdTextField);
    }
    else if (myWidthTextField == source) {
      property = myWidthAttr;
      value = getText(myWidthTextField);
    }
    else if (myHeightTextField == source) {
      property = myHeightAttr;
      value = getText(myHeightTextField);
    }

    if (property == null) {
      return;
    }

    myPropertiesManager.setValue(property, value);
  }

  private static String getText(@NonNull JBTextField textField) {
    Document doc = textField.getDocument();
    try {
      return doc.getText(0, doc.getLength());
    }
    catch (BadLocationException e) {
      // TODO: highlight in the field
      return "";
    }
  }
}
