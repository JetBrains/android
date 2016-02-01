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

import javax.annotation.Nonnull;
import javax.swing.*;

public class EditReferenceFixture extends AbstractContainerFixture<EditReferenceFixture, Box, JComponentDriver> {

  public EditReferenceFixture(@Nonnull Robot robot, @Nonnull Box target) {
    super(EditReferenceFixture.class, robot, target);
  }

  @Override
  protected @Nonnull JComponentDriver createDriver(@Nonnull Robot robot) {
    return new JComponentDriver(robot);
  }

  public @Nonnull SwatchComponentFixture getSwatchComponent() {
    return new SwatchComponentFixture(robot(), robot().finder().findByType(target(), SwatchComponent.class, true));
  }
}
