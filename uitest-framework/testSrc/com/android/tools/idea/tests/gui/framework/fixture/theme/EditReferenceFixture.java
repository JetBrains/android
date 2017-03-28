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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.swing.ui.SwatchComponent;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.fixture.AbstractContainerFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EditReferenceFixture extends AbstractContainerFixture<EditReferenceFixture, Box, JComponentDriver> {

  public EditReferenceFixture(@NotNull Robot robot, @NotNull Box target) {
    super(EditReferenceFixture.class, robot, target);
  }

  @Override
  protected @NotNull JComponentDriver createDriver(@NotNull Robot robot) {
    return new JComponentDriver(robot);
  }

  public @NotNull SwatchComponentFixture getSwatchComponent() {
    return new SwatchComponentFixture(robot(), robot().finder().findByType(target(), SwatchComponent.class, true));
  }
}
