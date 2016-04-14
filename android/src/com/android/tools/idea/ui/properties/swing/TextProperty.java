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
package com.android.tools.idea.ui.properties.swing;

import com.android.tools.idea.ui.LabelWithEditLink;
import com.android.tools.idea.ui.properties.ObservableProperty;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * {@link ObservableProperty} that wraps a Swing component and exposes its text value.
 */
public final class TextProperty extends StringProperty implements DocumentListener, PropertyChangeListener,
                                                                  com.intellij.openapi.editor.event.DocumentListener {
  @NotNull private final JComponent myComponent;

  public TextProperty(@NotNull JTextComponent textComponent) {
    myComponent = textComponent;
    textComponent.getDocument().addDocumentListener(this);
  }

  public TextProperty(@NotNull AbstractButton button) {
    myComponent = button;
    button.addPropertyChangeListener("text", this);
  }

  public TextProperty(@NotNull JLabel label) {
    myComponent = label;
    label.addPropertyChangeListener("text", this);
  }

  public TextProperty(@NotNull LabelWithEditLink editLabel) {
    myComponent = editLabel;
    editLabel.getDocument().addDocumentListener(this);
  }

  public TextProperty(@NotNull EditorComboBox editorComboBox) {
    myComponent = editorComboBox;
    editorComboBox.getDocument().addDocumentListener(this);
  }

  @Override
  public void insertUpdate(DocumentEvent documentEvent) {
    notifyInvalidated();
  }

  @Override
  public void removeUpdate(DocumentEvent documentEvent) {
    notifyInvalidated();
  }

  @Override
  public void changedUpdate(DocumentEvent documentEvent) {
    notifyInvalidated();
  }

  @Override
  public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
    notifyInvalidated();
  }

  @Override
  public void beforeDocumentChange(com.intellij.openapi.editor.event.DocumentEvent event) {
    // Do nothing, required interface override
  }

  @Override
  public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public String get() {
    if (myComponent instanceof JTextComponent) {
      return ((JTextComponent)myComponent).getText();
    }
    else if (myComponent instanceof AbstractButton) {
      return ((AbstractButton)myComponent).getText();
    }
    else if (myComponent instanceof JLabel) {
      return ((JLabel)myComponent).getText();
    }
    else if (myComponent instanceof LabelWithEditLink) {
      return ((LabelWithEditLink)myComponent).getText();
    }
    else if (myComponent instanceof EditorComboBox) {
      return ((EditorComboBox)myComponent).getText();
    }
    else {
      throw new IllegalStateException("Unexpected text component type: " + myComponent.getClass().getSimpleName());
    }
  }

  @Override
  protected void setDirectly(@NotNull String value) {
    if (myComponent instanceof JTextComponent) {
      ((JTextComponent)myComponent).setText(value);
    }
    else if (myComponent instanceof AbstractButton) {
      ((AbstractButton)myComponent).setText(value);
    }
    else if (myComponent instanceof JLabel) {
      ((JLabel)myComponent).setText(value);
    }
    else if (myComponent instanceof LabelWithEditLink) {
      ((LabelWithEditLink)myComponent).setText(value);
    }
    else if (myComponent instanceof EditorComboBox) {
      ((EditorComboBox)myComponent).setText(value);
    }
    else {
      throw new IllegalStateException("Unexpected text component type: " + myComponent.getClass().getSimpleName());
    }
  }
}
