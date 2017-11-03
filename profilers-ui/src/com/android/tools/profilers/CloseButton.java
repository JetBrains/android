/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.ui.InplaceButton;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Close button with style customized to share across profilers.
 */
public class CloseButton extends InplaceButton {

  private static final int CLOSE_BUTTON_SIZE = JBUI.scale(24); // Icon is 16x16. This gives it some padding, so it doesn't touch the border.

  public CloseButton(@Nullable ActionListener actionListener) {
    super(new IconButton("Close", AllIcons.Ide.Notification.Close, AllIcons.Ide.Notification.CloseHover), actionListener);
    setPreferredSize(new Dimension(CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE));
    setMinimumSize(getPreferredSize()); // Prevent layout phase from squishing this button
  }
}
