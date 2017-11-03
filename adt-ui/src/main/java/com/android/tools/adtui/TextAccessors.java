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
package com.android.tools.adtui;

import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * Adapts different text components to a common interface.
 */
public final class TextAccessors {
  private TextAccessors() {
    // Utilities class
  }

  @Nullable
  public static TextAccessor getTextAccessor(@NotNull final JComponent component) {
    if (component instanceof TextAccessor) {
      return (TextAccessor)component;
    }
    else if (component instanceof JTextComponent) {
      return new JTextComponentTextAccessor(component);
    }
    else if (component instanceof JLabel) {
      return new JLabelTextAccessor((JLabel)component);
    }
    else if (component instanceof AbstractButton) {
      return new AbstractButtonTextAccessor((AbstractButton)component);
    }
    else if (component instanceof ActionButton) {
      return new ActionButtonTextAccessor((ActionButton)component);
    }
    else {
      return null;
    }
  }

  private static class JTextComponentTextAccessor implements TextAccessor {
    private final JComponent myComponent;

    public JTextComponentTextAccessor(JComponent component) {
      myComponent = component;
    }

    @Override
    public String getText() {
      return ((JTextComponent)myComponent).getText();
    }

    @Override
    public void setText(String text) {
      ((JTextComponent)myComponent).setText(text);
    }
  }

  private static class JLabelTextAccessor implements TextAccessor {
    private final JLabel myComponent;

    public JLabelTextAccessor(JLabel component) {
      myComponent = component;
    }

    @Override
    public String getText() {
      return myComponent.getText();
    }

    @Override
    public void setText(String text) {
      myComponent.setText(text);
    }
  }

  private static class AbstractButtonTextAccessor implements TextAccessor {
    private final AbstractButton myComponent;

    public AbstractButtonTextAccessor(AbstractButton component) {
      myComponent = component;
    }

    @Override
    public String getText() {
      return myComponent.getText();
    }

    @Override
    public void setText(String text) {
      myComponent.setText(text);
    }
  }

  private static class ActionButtonTextAccessor implements TextAccessor {
    private final ActionButton myComponent;

    public ActionButtonTextAccessor(ActionButton component) {
      myComponent = component;
    }

    @Override
    public String getText() {
      return myComponent.getAction().getTemplatePresentation().getText();
    }

    @Override
    public void setText(String text) {
      myComponent.getAction().getTemplatePresentation().setText(text);
    }
  }
}
