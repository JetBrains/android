/*
 * Copyright (C) 2018 The Android Open Source Project
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

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all tutorial views.
 *
 * TODO: Investigate further default layout properties or behaviors to add.
 */
public abstract class CardViewPanel extends JPanel {

  // Used by child classes for button handling.
  protected ActionListener myListener;

  public CardViewPanel(@NotNull ActionListener listener) {
    super(new BorderLayout(0, 0));
    setBorder(BorderFactory.createEmptyBorder());
    myListener = listener;
    setOpaque(false);
  }
}
