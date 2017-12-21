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
package com.android.tools.idea.tests.gui.framework.fixture.wizard;

import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class WizardStepFixture<T extends WizardDialogFixture> implements ContainerFixture<Container> {
  @NotNull private final Container mytarget;
  @NotNull private final T myWizard;

  public WizardStepFixture(@NotNull T wizard) {
    this(wizard, wizard.target());
  }

  public WizardStepFixture(@NotNull T wizard, @NotNull Container target) {
    myWizard = wizard;
    mytarget = target;
  }

  @NotNull
  public T wizard() {
    return myWizard;
  }

  @NotNull
  @Override
  public Container target() {
    return mytarget;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myWizard.robot();
  }
}
