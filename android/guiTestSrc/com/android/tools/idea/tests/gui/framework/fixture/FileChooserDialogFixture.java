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

import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertNotNull;

public class FileChooserDialogFixture extends IdeaDialogFixture<FileChooserDialogImpl> {
  @NotNull
  public static FileChooserDialogFixture findOpenProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Open File or Project".equals(dialog.getTitle());
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findImportProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String title = dialog.getTitle();
        return title != null && title.startsWith("Select") && title.endsWith("Project to Import");
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findDialog(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new FileChooserDialogFixture(robot, find(robot, FileChooserDialogImpl.class, matcher));
  }

  private FileChooserDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<FileChooserDialogImpl> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public FileChooserDialogFixture select(@NotNull final VirtualFile file) {
    final FileSystemTreeImpl fileSystemTree = field("myFileSystemTree").ofType(FileSystemTreeImpl.class)
                                                                       .in(getDialogWrapper())
                                                                       .get();
    assertNotNull(fileSystemTree);
    fileSystemTree.showHiddens(true); // Windows: Default temporary folder (../AppData/..) is hidden.

    final AtomicBoolean fileSelected = new AtomicBoolean();
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        fileSystemTree.select(file, new Runnable() {
          @Override
          public void run() {
            fileSelected.set(true);
          }
        });
      }
    });

    Wait.minutes(2).expecting("file " + quote(file.getPath()) + " to be selected").until(fileSelected::get);

    return this;
  }

  @NotNull
  public FileChooserDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }
}
