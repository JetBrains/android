/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gfxtrace;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class EditAtomDialogFixture implements ContainerFixture<JOptionPane> {

  public static EditAtomDialogFixture find(GfxTraceFixture gfxTraceFixture) {
    JOptionPane jOptionPane = GuiTests.waitUntilShowing(gfxTraceFixture.robot(), Matchers.byType(JOptionPane.class));
    if (jOptionPane.getLocationOnScreen().equals(new Point())) {
      // dirty hack to get around a bug in X/AWT where in rare cases a timing issue causes a event from X to misinform AWT about the location of the dialog
      SwingUtilities.getAncestorOfClass(JDialog.class, jOptionPane).setLocation(10, 10);
    }
    return new EditAtomDialogFixture(gfxTraceFixture, jOptionPane);
  }

  private final GfxTraceFixture myGfxTraceFixture;
  private final JOptionPane myOptionPane;
  private final Robot myRobot;

  private EditAtomDialogFixture(@NotNull GfxTraceFixture gfxTraceFixture, @NotNull JOptionPane jOptionPane) {
    myGfxTraceFixture = gfxTraceFixture;
    myOptionPane = jOptionPane;
    myRobot = gfxTraceFixture.robot();
  }

  @NotNull
  public GfxTraceFixture clickCancel() {
    GuiTests.findAndClickCancelButton(this);
    Wait.seconds(1).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myGfxTraceFixture;
  }

  @NotNull
  @Override
  public JOptionPane target() {
    return myOptionPane;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myRobot;
  }
}
