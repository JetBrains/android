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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.HyperlinkLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Fixture to work with {@link LibraryEditorForm} to add debug symbols.
 */
public class LibraryEditorFixture extends EditorFixture {

  private final Container libraryContainer;

  private LibraryEditorFixture(IdeFrameFixture frame, Container target) {
    super(frame.robot(), frame);
    this.libraryContainer = target;
  }

  public static LibraryEditorFixture find(@NotNull IdeFrameFixture ideFrame) {
    Container target = ideFrame
      .robot()
      .finder()
      .findByName(
        ideFrame.target(),
        "libraryEditorForm",
        JPanel.class,
        true);
    return new LibraryEditorFixture(ideFrame, target);
  }

  @NotNull
  public LibraryEditorFixture addDebugSymbols(@NotNull File debugSymbols) {
    JButton addButton = robot
      .finder()
      .findByName(
        libraryContainer,
        "addDebugSymbolsButton",
        JButton.class,
        true);
    JButtonFixture jButtonFixture = new JButtonFixture(robot, addButton);

    Wait.seconds(5)
      .expecting("add button is enabled")
      .until(jButtonFixture::isEnabled);

    jButtonFixture.click();

    Wait.seconds(20)
      .expecting("file chooser dialog to open")
      .until(() -> FileChooserDialogFixture.findDialog(robot, "Debug Symbols").isEnabled());

    FileChooserDialogFixture.findDialog(robot, "Debug Symbols")
        .select(VfsUtil.findFileByIoFile(debugSymbols, true))
        .clickOk()
        .waitUntilNotShowing();

    return this;
  }

  @NotNull
  public PathMappingTableFixture getPathMappings() {
    return PathMappingTableFixture.find(robot, libraryContainer);
  }

  @NotNull
  public LibraryEditorFixture applyChanges() {
    Wait.seconds(5)
      .expecting("apply changes label to appear")
      .until(() -> getApplyChangesLabel(robot, libraryContainer) != null);

    new HyperlinkLabelFixture(robot, getApplyChangesLabel(robot, libraryContainer)).clickLink("Apply Changes");
    return this;
  }

  @Nullable
  private static HyperlinkLabel getApplyChangesLabel(@NotNull Robot robot, @NotNull Container container) {
    try {
      return robot.finder()
        .findByName(
          container,
          "applyChangesLabel",
          HyperlinkLabel.class,
          true);
    } catch (ComponentLookupException didNotFind) {
      return null;
    }
  }

  @Nullable
  private static JLabel getPathMappingsLabel(@NotNull Robot robot, @NotNull Container container) {
    try {
      return robot.finder()
        .findByName(
          container,
          "pathMappingsLabel",
          JLabel.class,
          true);
    } catch (ComponentLookupException didNotFind) {
      return null;
    }
  }
}
