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

import com.android.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A label which automatically listens to all focus events whenever it is part of the current UI
 * hierarchy, and sets its text to the focused component's tooltip, if any.
 *
 * Normally, tooltips are made visible by mouseover, but it can be useful to explicitly show the
 * tooltip directly on the page as well, for example when the user is tabbing around its fields.
 *
 * Important: often components can be complex, nested widgets, and the part of the component that
 * has the tooltip isn't the part that gets focus. As a result, when we can't find a tooltip on
 * a target component, we navigate up the hierarchy until we find one that does. You can limit
 * the scope of the search by calling {@link #setScope(Component)}.
 */
public final class TooltipLabel extends JBLabel {

  private static final String PROPERTY_FOCUS_OWNER = "focusOwner";
  @Nullable private Component myRootComponent;

  public TooltipLabel() {
    final PropertyChangeListener focusListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if ((evt.getNewValue() instanceof Component)) {
          Component component = (Component)evt.getNewValue();
          TooltipLabel.super.setText(Strings.nullToEmpty(getTooltip(component)));
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

  /**
   * Set a parent container which acts as a scope that prevent this class from crawling anywhere
   * outside of it.
   */
  public void setScope(@Nullable Component rootComponent) {
    myRootComponent = rootComponent;
  }

  private boolean isInScope(@NotNull Component component) {
    if (myRootComponent == null) {
      return true;
    }

    @Nullable Component currComponent = component;
    while (currComponent != null) {
      if (currComponent == myRootComponent) {
        return true;
      }
      currComponent = currComponent.getParent();
    }

    return false;
  }

  @VisibleForTesting // Unit testing Swing focus is not trivial, test this directly instead
  @Nullable
  String getTooltip(@NotNull Component component) {
    if (!isInScope(component)) {
      return null;
    }

    @Nullable Component currComponent = component;
    String tooltip = null;
    while (tooltip == null && currComponent != null) {
      if (currComponent instanceof JComponent) {
        tooltip = ((JComponent)currComponent).getToolTipText();
      }
      if (currComponent == myRootComponent) {
        break;
      }
      currComponent = currComponent.getParent();
    }
    return tooltip;
  }
}
