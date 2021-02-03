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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class FileChooserDialogFixture extends IdeaDialogFixture<FileChooserDialogImpl> {

  @NotNull
  public static FileChooserDialogFixture findDialog(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new FileChooserDialogFixture(robot, find(robot, FileChooserDialogImpl.class, matcher));
  }

  @NotNull
  public static FileChooserDialogFixture findDialog(@NotNull Robot robot, @NotNull final String dialogTitle) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class, true) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return dialogTitle.equals(component.getTitle());
      }
    });
  }

  private FileChooserDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<FileChooserDialogImpl> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public FileChooserDialogFixture select(@NotNull final VirtualFile file) {
    JTextField pathTextField =
      GuiTests.waitUntilShowing(robot(), target(), Matchers.byName(JTextField.class, "FileChooserDialogImpl.myPathTextField"));
    new JTextComponentFixture(robot(), pathTextField).setText(file.getPath());
    return this;
  }

  @NotNull
  public FileChooserDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

  @NotNull
  public FileChooserDialogFixture clickOkAndWaitToClose() {
    clickOk();
    Wait.seconds(5).expecting("OK button clicked && disappear").until(() -> GuiQuery.getNonNull(() -> !target().isShowing()));

    return this;
  }
}
