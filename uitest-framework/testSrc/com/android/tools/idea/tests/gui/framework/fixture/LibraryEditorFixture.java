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

import com.intellij.openapi.vfs.VfsUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * <p>Fixture for working with the {@link LibraryEditorForm} that shows up
 * for adding debug symbols for native libraries. This form also shows up
 * when mapping path to source code given in the debug symbols to the local
 * filesystem paths containing the same source.</p>
 * Created by afwang on 7/17/17.
 */
public class LibraryEditorFixture extends EditorFixture {

  private final Container libraryContainer;

  private LibraryEditorFixture(IdeFrameFixture frame, Container target) {
    super(frame.robot(), frame);
    this.libraryContainer = target;
  }

  public static LibraryEditorFixture find(@NotNull IdeFrameFixture ideFrame) {
    Container target = ideFrame.robot().finder().findByName(ideFrame.target(), "nativeLibraryDebugSymbolsContainer", JPanel.class, true);
    return new LibraryEditorFixture(ideFrame, target);
  }

  @NotNull
  public LibraryEditorFixture addDebugSymbols(@NotNull File debugSymbols) {
    JButton addButton = robot.finder().findByName(libraryContainer, "addDebugSymbolsButton", JButton.class, true);
    new JButtonFixture(robot, addButton).click();

    FileChooserDialogFixture debugSymbolsDialog = FileChooserDialogFixture.findDialog(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@Nonnull JDialog component) {
        return "Debug Symbols".equals(component.getTitle());
      }
    });

    debugSymbolsDialog.select(VfsUtil.findFileByIoFile(debugSymbols, true));
    debugSymbolsDialog.clickOk();
    debugSymbolsDialog.waitUntilNotShowing();
    return this;
  }
}
