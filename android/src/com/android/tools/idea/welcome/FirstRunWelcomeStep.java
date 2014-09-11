/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.tools.idea.configurations.DeviceMenuAction.FormFactor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Welcome page for the first run wizard
 */
public final class FirstRunWelcomeStep extends FirstRunWizardStep {
  private JPanel myRoot;
  private JLabel myIcons;

  public FirstRunWelcomeStep() {
    super("Welcome");
    myIcons.setIcon(getFormFactorsImage(myIcons));
    setComponent(myRoot);
  }

  @Nullable
  private static Icon getFormFactorsImage(JComponent component) {
    BufferedImage image = null;
    Graphics2D graphics = null;
    int x = 0;
    for (FormFactor formFactor : FormFactor.values()) {
      Icon icon = formFactor.getLargeIcon();
      if (image == null) {
        //noinspection UndesirableClassUsage
        image = new BufferedImage(icon.getIconWidth() * FormFactor.values().length, icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        graphics = image.createGraphics();
      }
      icon.paintIcon(component, graphics, x, 0);
      x += icon.getIconWidth();
    }
    if (graphics != null) {
      graphics.dispose();
      return new ImageIcon(image);
    }
    else {
      return null;
    }
  }

  @Override
  public void init() {

  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    throw new IllegalStateException();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    // Doesn't matter
    return myIcons;
  }
}
