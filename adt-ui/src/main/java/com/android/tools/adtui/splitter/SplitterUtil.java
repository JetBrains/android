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
package com.android.tools.adtui.splitter;

import java.awt.Dimension;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Utilities for {@link com.intellij.openapi.ui.ThreeComponentsSplitter}.
 */
public final class SplitterUtil {

  public static void setMinimumWidth(@NotNull JComponent component, int minWidth) {
    Dimension minimumSize = component.getMinimumSize();
    minimumSize.width = Math.max(minimumSize.width, minWidth);
    component.setMinimumSize(minimumSize);
  }

  public static void setMinimumHeight(@NotNull JComponent component, int minHeight) {
    Dimension minimumSize = component.getMinimumSize();
    minimumSize.height = Math.max(minimumSize.height, minHeight);
    component.setMinimumSize(minimumSize);
  }
}
