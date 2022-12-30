/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.util;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.util.ui.accessibility.ScreenReader;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.Arrays;
import javax.swing.JCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionToolbarUtil {

  // Avoid instantiation...
  private ActionToolbarUtil() {}

  /**
   * Make it possible to navigate the actions buttons from the keyboard.
   *
   * When ScreenReader.isActive() is false the action buttons are not focusable.
   * Override this when the buttons are added to the toolbar.
   */
  public static void makeToolbarNavigable(@NotNull ActionToolbar toolbar) {
    if (!ScreenReader.isActive()) {
      Arrays.stream(toolbar.getComponent().getComponents())
        .forEach(ActionToolbarUtil::makeActionNavigable);

      toolbar.getComponent().addContainerListener(new ContainerListener() {
        @Override
        public void componentAdded(@NotNull ContainerEvent event) {
          makeActionNavigable(event.getChild());
        }

        @Override
        public void componentRemoved(@NotNull ContainerEvent event) {
        }
      });
    }
  }

  private static void makeActionNavigable(@NotNull Component child) {
    if (child instanceof ActionButton || child instanceof JCheckBox) {
      child.setFocusable(true);
    }
  }

  @Nullable
  public static ActionButton findActionButton(@NotNull ActionToolbar toolbar, @NotNull AnAction action) {
    return (ActionButton)Arrays.stream(toolbar.getComponent().getComponents())
      .filter(child -> child instanceof ActionButton && ((ActionButton)child).getAction().equals(action))
      .findFirst()
      .orElse(null);
  }
}
