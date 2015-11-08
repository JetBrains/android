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
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.assertNotNull;

public class LibraryPropertiesDialogFixture extends IdeaDialogFixture<LibraryPropertiesDialog> {
  @NotNull private final String myLibraryName;
  @NotNull private final Project myProject;

  @NotNull
  public static LibraryPropertiesDialogFixture showPropertiesDialog(@NotNull Robot robot,
                                                                    @NotNull String libraryName,
                                                                    @NotNull final Project project) {
    Library found = null;
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    for (Library library : libraryTable.getLibraries()) {
      if (library.getName() != null && library.getName().startsWith(libraryName)) {
        found = library;
      }
    }
    assertNotNull("Failed to find library with name '" + libraryName + "'", found);

    final Library library = found;
    final Ref<LibraryPropertiesDialog> wrapperRef = new Ref<LibraryPropertiesDialog>();

    // Using invokeLater because the dialog is modal. Using GuiActionRunner will make the test block forever.
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        LibraryPropertiesDialog wrapper = new LibraryPropertiesDialog(project, library);
        wrapperRef.set(wrapper);
        wrapper.showAndGet();
      }
    });

    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (!"Library Properties".equals(dialog.getTitle()) || !dialog.isShowing()) {
          return false;
        }
        DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
        return wrapper == wrapperRef.get();
      }
    });

    return new LibraryPropertiesDialogFixture(robot, dialog, wrapperRef.get(), library, project);
  }

  protected LibraryPropertiesDialogFixture(@NotNull Robot robot,
                                           @NotNull JDialog target,
                                           @NotNull LibraryPropertiesDialog wrapper,
                                           @NotNull Library library,
                                           @NotNull Project project) {
    super(robot, target, wrapper);
    myLibraryName = nullToEmpty(library.getName());
    myProject = project;
  }

  @NotNull
  public LibraryPropertiesDialogFixture addAttachment(@NotNull File path) {
    final ActionButton addButton = robot().finder().find(target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        String toolTipText = button.getToolTipText();
        return button.isShowing() && isNotEmpty(toolTipText) && toolTipText.startsWith("Add");
      }
    });
    robot().moveMouse(addButton);
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        addButton.click();
      }
    });

    VirtualFile attachment = findFileByIoFile(path, true);
    FileChooserDialogFixture fileChooser = FileChooserDialogFixture.findDialog(robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        String title = dialog.getTitle();
        return dialog.isShowing() && isNotEmpty(title) && title.startsWith("Attach Files or Directories to Library");
      }
    });
    assertNotNull("Failed to find VirtualFile with path " + quote(path.getPath()), attachment);
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
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(myProject);
    Library library = libraryTable.getLibraryByName(myLibraryName);

    assertNotNull("Failed to find library " + quote(myLibraryName), library);
    return new LibraryFixture(library);
  }
}
