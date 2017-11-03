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

import com.android.tools.idea.editors.theme.ActivityChooser;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class ActivityChooserFixture extends IdeaDialogFixture<ActivityChooser> {

  private ActivityChooserFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ActivityChooser> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public static ActivityChooserFixture find(@NotNull Robot robot) {
    return new ActivityChooserFixture(robot, find(robot, ActivityChooser.class));
  }

  public ActivityChooserFixture clickOk() {
    findAndClickOkButton(this);
    waitUntilNotShowing(); // Mac dialogs have an animation, wait until it hides
    return this;
  }
}
