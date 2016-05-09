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
package com.android.tools.idea.assistant.view;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Base class for all tutorial views.
 *
 * TODO: Investigate further default layout properties or behaviors to add.
 * TODO: Investigate migrating display properties to a form.
 */
public abstract class CardViewPanel extends JPanel {

  // Used by child classes for button handling.
  protected ActionListener myListener;

  public CardViewPanel(@NotNull ActionListener listener) {
    super(new BorderLayout());
    setBorder(BorderFactory.createEmptyBorder());
    myListener = listener;
    setOpaque(false);
    BorderLayout layout = (BorderLayout)getLayout();
    layout.setVgap(0);
    layout.setHgap(0);
  }
}
