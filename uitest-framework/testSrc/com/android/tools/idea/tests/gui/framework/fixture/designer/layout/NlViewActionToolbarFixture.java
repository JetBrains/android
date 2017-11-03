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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;


import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.JComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Fixture for the toolbar displaying the View action for the selected component
 */
public class NlViewActionToolbarFixture extends JComponentFixture<NlViewActionToolbarFixture, ActionToolbarImpl> {

  private NlViewActionToolbarFixture(@NotNull Robot robot, @NotNull ActionToolbarImpl toolbar) {
    super(NlViewActionToolbarFixture.class, robot, toolbar);
    Wait.seconds(1).expecting("View Actions toolbar to be showing").until(() -> toolbar.getComponent().isShowing());
  }

  @NotNull
  public ActionButtonFixture getButtonByIcon(@NotNull Icon icon) {
    return ActionButtonFixture.findByIcon(icon, robot(), target());
  }

  @NotNull
  public static NlViewActionToolbarFixture create(@NotNull NlEditorFixture nlEditorFixture) {
    Robot robot = nlEditorFixture.robot();
    return new NlViewActionToolbarFixture(robot, robot.finder().findByName("NlLayoutToolbar", ActionToolbarImpl.class, false));
  }
}
