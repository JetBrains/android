/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;

public final class Buttons {
  private Buttons() {
  }

  public static @NotNull AbstractButton newIconButton(@NotNull Icon icon) {
    Dimension size = new JBDimension(22, 22);

    AbstractButton button = new JButton(icon);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setMaximumSize(size);
    button.setMinimumSize(size);
    button.setPreferredSize(size);

    return button;
  }
}
