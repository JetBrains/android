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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.NamedColorUtil;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public class EmptyPanel extends JPanel {
  public EmptyPanel(@NotNull String text) {
    super(new BorderLayout());
    JBLabel emptyText = new JBLabel(text);
    emptyText.setForeground(NamedColorUtil.getInactiveTextColor());
    emptyText.setHorizontalAlignment(SwingConstants.CENTER);
    add(emptyText, BorderLayout.CENTER);
  }
}
