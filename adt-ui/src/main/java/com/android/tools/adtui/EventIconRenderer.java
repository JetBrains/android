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
package com.android.tools.adtui;

import com.android.tools.adtui.model.event.EventAction;
import com.android.tools.adtui.model.event.SimpleEventType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class EventIconRenderer implements SimpleEventRenderer {

  @NotNull private Icon myLightThemeIcon;
  @NotNull private Icon myDarkThemeIcon;
  @NotNull private int myIconWidth;

  @NotNull
  private static Icon load(String path) {
    return IconLoader.getIcon(path, EventIconRenderer.class);
  }

  public EventIconRenderer(String lightTheme, String darkTheme) {
    this(load(lightTheme), load(darkTheme));
  }

  EventIconRenderer(@NotNull Icon lightIcon, @NotNull Icon darkIcon) {
    myLightThemeIcon = lightIcon;
    myDarkThemeIcon = darkIcon;
    assert myLightThemeIcon.getIconWidth() == myDarkThemeIcon.getIconWidth() : "Icons of both themes should have the same width.";
    myIconWidth = myDarkThemeIcon.getIconWidth();
  }

  @Override
  public void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length, EventAction<SimpleEventType> notUsedData) {
    Icon icon = UIUtil.isUnderDarcula() ? myDarkThemeIcon : myLightThemeIcon;
    AffineTransform originalTransform = g2d.getTransform();
    g2d.setTransform(transform);
    icon.paintIcon(parent, g2d, -myIconWidth / 2, 0);
    g2d.setTransform(originalTransform);
  }
}
