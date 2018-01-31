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
package com.android.tools.adtui.stdui.menu;

import com.android.tools.adtui.stdui.StandardColors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.PopupMenuUI;
import javax.swing.plaf.UIResource;
import java.awt.*;

public class CommonPopupMenuUI extends PopupMenuUI {
  @Override
  public void installUI(@NotNull JComponent component) {
    super.installUI(component);

    Border border = component.getBorder();
    if (border == null || border instanceof UIResource) {
      component.setBorder(new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));
    }

    Color background = component.getBackground();
    if (background == null || background instanceof UIResource) {
      component.setBackground(new ColorUIResource(StandardColors.MENU_BACKGROUND_COLOR));
    }

    Color foreground = component.getForeground();
    if (foreground == null || foreground instanceof UIResource) {
      component.setForeground(new ColorUIResource(StandardColors.TEXT_COLOR));
    }
  }
}
