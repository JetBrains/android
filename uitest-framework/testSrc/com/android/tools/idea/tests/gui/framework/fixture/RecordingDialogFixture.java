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

import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.ContainerFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.google.common.truth.Truth.assertThat;

/** Fixture for {@link com.google.gct.testrecorder.ui.RecordingDialog}. */
public class RecordingDialogFixture extends ComponentFixture<RecordingDialogFixture, JDialog>
  implements ContainerFixture<JDialog>{

  @NotNull
  public static RecordingDialogFixture find(@NotNull Robot robot) {
    Dialog dialog = WindowFinder.findDialog(DialogMatcher.withTitle("Record Your Test").andShowing())
      .withTimeout(TimeUnit.SECONDS.toMillis(10)).using(robot)
      .target();
    assertThat(dialog).isInstanceOf(JDialog.class);
    return new RecordingDialogFixture(robot, (JDialog)dialog);
  }

  private RecordingDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(RecordingDialogFixture.class, robot, target);
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }
}

