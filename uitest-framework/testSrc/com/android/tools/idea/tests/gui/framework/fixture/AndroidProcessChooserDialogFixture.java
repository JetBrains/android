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
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.google.common.truth.Truth.assertThat;

/** Fixture for {@link com.android.tools.idea.run.editor.AndroidProcessChooserDialog}. */
public class AndroidProcessChooserDialogFixture extends ComponentFixture<AndroidProcessChooserDialogFixture, JDialog>
  implements ContainerFixture<JDialog> {

  @NotNull
  public static AndroidProcessChooserDialogFixture find(@NotNull Robot robot) {
    Dialog dialog = WindowFinder.findDialog(DialogMatcher.withTitle("Choose Process").andShowing())
                                .withTimeout(TimeUnit.SECONDS.toMillis(5)).using(robot)
                                .target();
    assertThat(dialog).isInstanceOf(JDialog.class);
    return new AndroidProcessChooserDialogFixture(robot, (JDialog)dialog);
  }

  private AndroidProcessChooserDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(AndroidProcessChooserDialogFixture.class, robot, target);
  }

  @NotNull
  public AndroidProcessChooserDialogFixture selectProcess() {
    JTree processTree = robot().finder().findByType(target(), JTree.class);
    JTreeFixture jTreeFixture = new JTreeFixture(robot(), processTree);
    Wait.seconds(120).expecting("process list to be populated").until(() -> processTree.getRowCount() == 2);
    jTreeFixture.selectRow(1);
    return this;
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }
}
