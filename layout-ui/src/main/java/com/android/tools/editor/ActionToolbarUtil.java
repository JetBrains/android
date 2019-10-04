/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.editor;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.util.ui.accessibility.ScreenReader;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import org.jetbrains.annotations.NotNull;

public class ActionToolbarUtil {

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
      toolbar.getComponent().addContainerListener(new ContainerListener() {
        @Override
        public void componentAdded(@NotNull ContainerEvent event) {
          Component child = event.getChild();
          if (child instanceof ActionButton) {
            child.setFocusable(true);
          }
        }

        @Override
        public void componentRemoved(@NotNull ContainerEvent event) {
        }
      });
    }
  }
}
