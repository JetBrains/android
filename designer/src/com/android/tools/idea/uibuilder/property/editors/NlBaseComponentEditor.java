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

import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class NlBaseComponentEditor implements NlComponentEditor, BrowsePanel.Context {
  protected static final JBColor DEFAULT_VALUE_TEXT_COLOR = new JBColor(Gray._128, Gray._128);
  protected static final JBColor CHANGED_VALUE_TEXT_COLOR = JBColor.BLUE;

  private final NlEditingListener myListener;

  private JLabel myLabel;

  public NlBaseComponentEditor(@NotNull NlEditingListener listener) {
    myListener = listener;
  }

  @Nullable
  @Override
  public JLabel getLabel() {
    return myLabel;
  }

  @Override
  public void setLabel(@NotNull JLabel label) {
    myLabel = label;
  }

  @Override
  public void setVisible(boolean visible) {
    getComponent().setVisible(visible);
    if (myLabel != null) {
      myLabel.setVisible(visible);
    }
  }

  @Override
  public void refresh() {
    NlProperty property = getProperty();
    if (property != null) {
      setProperty(property);
    }
  }

  @Override
  @Nullable
  public Object getValue() {
    return null;
  }

  @Override
  public void activate() {
  }

  @Override
  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
  }

  @Override
  public void requestFocus() {
    getComponent().requestFocus();
  }

  @Override
  public void cancelEditing() {
    myListener.cancelEditing(this);
  }

  protected void stopEditing(@Nullable Object newValue) {
    myListener.stopEditing(this, newValue);
    refresh();
  }

  protected void displayResourcePicker() {
    NlProperty property = getProperty();
    if (property == null) {
      return;
    }
    ChooseResourceDialog dialog = BrowsePanel.showResourceChooser(property);
    if (dialog.showAndGet()) {
      stopEditing(dialog.getResourceName());
    }
    else {
      cancelEditing();
    }
  }
}
