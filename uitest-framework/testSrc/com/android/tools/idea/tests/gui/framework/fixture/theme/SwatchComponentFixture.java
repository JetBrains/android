/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.android.tools.adtui.ui.ClickableLabel;
import com.android.tools.idea.ui.resourcechooser.ResourceSwatchComponent;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SwatchComponentFixture extends JComponentFixture<SwatchComponentFixture, ResourceSwatchComponent> {
  public SwatchComponentFixture(Robot robot, ResourceSwatchComponent target) {
    super(SwatchComponentFixture.class, robot, target);
  }

  @NotNull
  public static SwatchComponentFixture find(@NotNull Robot robot, @NotNull Container target) {
    return new SwatchComponentFixture(robot, robot.finder().findByType(target, ResourceSwatchComponent.class));
  }

  @NotNull
  public JButtonFixture getSwatchButton() {
    return new JButtonFixture(robot(), robot().finder().findByType(target(), ClickableLabel.class));
  }

  @NotNull
  public String getText() {
    return target().getText();
  }

  public boolean hasWarningIcon() {
    return target().hasWarningIcon();
  }
}
