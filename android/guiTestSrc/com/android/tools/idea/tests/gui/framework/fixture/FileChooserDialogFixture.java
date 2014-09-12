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

import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ComponentFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.field;
import static org.fest.swing.timing.Pause.pause;
import static org.fest.util.Strings.quote;

public class FileChooserDialogFixture extends ComponentFixture<JDialog> {
  @NotNull
  public static FileChooserDialogFixture findOpenProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        return dialog.isShowing() && "Open Project".equals(dialog.getTitle());
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findImportProjectDialog(@NotNull Robot robot) {
    return findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        String title = dialog.getTitle();
        return dialog.isShowing() && title != null && title.startsWith("Select") && title.endsWith("Project to Import");
      }
    });
  }

  @NotNull
  public static FileChooserDialogFixture findDialog(@NotNull Robot robot, @NotNull GenericTypeMatcher<JDialog> matcher) {
    JDialog dialog = robot.finder().find(matcher);
    return new FileChooserDialogFixture(robot, dialog);
  }

  private FileChooserDialogFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(robot, target);
  }

  @NotNull
  public FileChooserDialogFixture select(@NotNull File file) {
    WeakReference<DialogWrapper> dialogWrapperRef = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {})
                                                                            .in(target).get();

    DialogWrapper dialogWrapper = dialogWrapperRef.get();
    assertThat(dialogWrapper).isInstanceOf(FileChooserDialogImpl.class);
    final FileSystemTreeImpl fileSystemTree = field("myFileSystemTree").ofType(FileSystemTreeImpl.class).in(dialogWrapper).get();

    final VirtualFile toSelect = findFileByIoFile(file, true);
    assertNotNull(toSelect);
    final AtomicBoolean fileSelected = new AtomicBoolean();

    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        fileSystemTree.select(toSelect, new Runnable() {
          @Override
          public void run() {
            fileSelected.set(true);
          }
        });
      }
    });

    pause(new Condition("File " + quote(file.getPath()) + " is selected") {
      @Override
      public boolean test() {
        return fileSelected.get();
      }
    });

    return this;
  }

  @NotNull
  public FileChooserDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }
}
