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

import com.android.tools.adtui.TooltipComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This class is needed by {@link TooltipComponent}, so that it can add itself to this container.
 * By doing so, {@link TooltipComponent} will have the same bounds as {@link ProfilerLayeredPane}
 * when used across profilers UI.
 */
public class ProfilerLayeredPane extends JLayeredPane {
  /**
   * Traverses up to the ProfilerLayeredPane and sets the cursor on it.
   *
   * @param container where traversal starts.
   * @param cursor    the cursor to set on the ProfilerLayeredPane.
   *
   * @return the ProfilerLayeredPane if found. Null otherwise.
   */
  @Nullable
  public static Container setCursorOnProfilerLayeredPane(Container container, Cursor cursor) {
    for (Container p = container; p != null; p = p.getParent()) {
      if (p instanceof ProfilerLayeredPane) {
        p.setCursor(cursor);
        return p;
      }
    }
    return null;
  }
}
