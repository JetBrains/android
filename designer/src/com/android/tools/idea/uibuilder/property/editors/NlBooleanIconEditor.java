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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Editor for editing a boolean property displays as an icon with selection on/off.
 */
public class NlBooleanIconEditor {
  private static final JBColor ON_BACKGROUND = new JBColor(0xD0D0D0, 0xA0A0A0);
  private static final JBColor OFF_BACKGROUND = new JBColor(0xF0F0F0, 0x404040);

  private final Icon myOnIcon;
  private final Icon myOffIcon;
  private final JButton myButton;
  private final String myTrueValue;
  private final String myFalseValue;
  private NlProperty myProperty;

  public NlBooleanIconEditor(@NotNull VdIcon icon) {
    this(icon, SdkConstants.VALUE_TRUE, SdkConstants.VALUE_FALSE);
  }

  public NlBooleanIconEditor(@NotNull VdIcon icon, @NotNull String singleValue) {
    this(icon, singleValue, null);
  }

  public NlBooleanIconEditor(@NotNull VdIcon icon, @NotNull String trueValue, @Nullable String falseValue) {
    myOnIcon = new VdIcon(icon, ON_BACKGROUND);
    myOffIcon = new VdIcon(icon, OFF_BACKGROUND);
    myTrueValue = trueValue;
    myFalseValue = falseValue;
    myButton = new JButton();
    myButton.setBorder(BorderFactory.createEmptyBorder());
    myButton.setMargin(new Insets(0, 0, 0, 0));
    myButton.setContentAreaFilled(false);
    myButton.addActionListener(event -> toggle());
  }

  public Component getComponent() {
    return myButton;
  }

  public void setProperty(@NotNull NlProperty property) {
    myProperty = property;
    String value = property.getValue();
    updateDisplayValue(value != null && value.equalsIgnoreCase(myTrueValue));
  }

  private void updateDisplayValue(boolean on) {
    if (on) {
      myButton.setIcon(myOnIcon);
    }
    else {
      myButton.setIcon(myOffIcon);
    }
  }

  private void toggle() {
    boolean newValue = myButton.getIcon() != myOnIcon;
    if (myProperty != null) {
      myProperty.setValue(newValue ? myTrueValue : myFalseValue);
      updateDisplayValue(newValue);
    }
  }
}
