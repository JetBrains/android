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
package com.android.tools.idea.ui;

import com.google.common.base.Strings;
import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A label which automatically listens to all focus events whenever it is part of the current UI
 * hierarchy, and sets its text to the focused component's tooltip, if any.
 * <p/>
 * Normally, tooltips are made visible by mouseover, but it can be useful to explicitly show the
 * tooltip directly on the page as well, for example when the user is tabbing around its fields.
 */
public final class TooltipLabel extends JBLabel {

  private static final String PROPERTY_FOCUS_OWNER = "focusOwner";

  public TooltipLabel() {
    final PropertyChangeListener focusListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getNewValue() instanceof JComponent)) {
          JComponent component = (JComponent)evt.getNewValue();
          TooltipLabel.super.setText(Strings.nullToEmpty(component.getToolTipText()));
        }
      }
    };

    addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
          return;
        }

        if (isShowing()) {
          KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(PROPERTY_FOCUS_OWNER, focusListener);
        }
        else {
          KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(PROPERTY_FOCUS_OWNER, focusListener);
        }
      }
    });

  }

  @Override
  public void setText(String text) {
    // Disabled. Text can only be set internally.
  }
}
