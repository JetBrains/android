/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.library.LibraryPropertiesDialog;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.junit.Assert.assertNotNull;

public class LibraryPropertiesDialogFixture extends IdeaDialogFixture<LibraryPropertiesDialog> {
  @NotNull private final String myLibraryName;
  @NotNull private final Project myProject;

  @NotNull
  protected static LibraryPropertiesDialogFixture find(@NotNull Robot robot, @NotNull String libraryName, @NotNull Project project) {
    return new LibraryPropertiesDialogFixture(robot, find(robot, LibraryPropertiesDialog.class), libraryName, project);
  }

  private LibraryPropertiesDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<LibraryPropertiesDialog> wrapper,
                                           @NotNull String libraryName, @NotNull Project project) {
    super(robot, wrapper);
    myLibraryName = libraryName;
    myProject = project;
  }

  @NotNull
  public LibraryPropertiesDialogFixture addAttachment(@NotNull File path) {
    ActionButton addButton = GuiTests.waitUntilShowing(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "Add".equals(button.getAction().getTemplatePresentation().getDescription());
      }
    });
    new ActionButtonFixture(robot(), addButton).click();

    VirtualFile attachment = findFileByIoFile(path, true);
    FileChooserDialogFixture fileChooser = FileChooserDialogFixture.findDialog(robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String title = dialog.getTitle();
        return isNotEmpty(title) && title.startsWith("Attach Files or Directories to Library");
      }
    });
    assertNotNull("Failed to find VirtualFile with path '" + path.getPath() + "'", attachment);
    fileChooser.select(attachment)
               .clickOk();
    return this;
  }

  @NotNull
  public LibraryPropertiesDialogFixture clickOk() {
    findAndClickOkButton(this);
    LibraryPropertiesDialog dialog = this.getDialogWrapper();
    dialog.applyChanges();
    return this;
  }

  @NotNull
  public LibraryFixture getLibrary() {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    Library library = libraryTable.getLibraryByName(myLibraryName);

    assertNotNull("Failed to find library '" + myLibraryName + "'", library);
    return new LibraryFixture(library);
  }
}
