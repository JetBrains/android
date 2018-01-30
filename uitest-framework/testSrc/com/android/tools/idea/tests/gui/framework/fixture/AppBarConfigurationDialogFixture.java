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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.handlers.ui.AppBarConfigurationDialog;
import com.intellij.ui.components.JBLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.DialogFixture;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;

public class AppBarConfigurationDialogFixture extends DialogFixture {
  private AppBarConfigurationDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  @NotNull
  public static AppBarConfigurationDialogFixture find(@NotNull Robot robot) {
    return new AppBarConfigurationDialogFixture(robot,
                                                robot.finder().find(Matchers.byType(AppBarConfigurationDialog.class).andIsShowing()));
  }

  @NotNull
  public AppBarConfigurationDialogFixture clickOk() {
    findAndClickButton(this, "OK");
    return this;
  }

  @NotNull
  public AppBarConfigurationDialogFixture clickCancel() {
    findAndClickButton(this, "Cancel");
    return this;
  }

  @NotNull
  public AppBarConfigurationDialogFixture waitForPreview() {
    waitForBackgroundTasks(robot());
    waitUntilShowing(robot(),
                   Matchers.byName(JBLabel.class, "CollapsedPreview").and(new GenericTypeMatcher<JBLabel>(JBLabel.class) {
                     @Override
                     protected boolean isMatching(@Nonnull JBLabel component) {
                       return component.getIcon() != null;
                     }
                   }));
    return this;
  }
}
