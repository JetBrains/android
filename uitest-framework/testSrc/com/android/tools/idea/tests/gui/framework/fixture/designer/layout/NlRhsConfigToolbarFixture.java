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
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;

/**
 * Fixture representing the right hand side configuration in the first line toolbar above an associated layout editor
 */
public class NlRhsConfigToolbarFixture {

  @NotNull private final NlEditorFixture myNlEditorFixture;
  @NotNull private final ActionToolbar myToolBar;

  public NlRhsConfigToolbarFixture(@NotNull NlEditorFixture nlEditorFixture, @NotNull ActionToolbar toolbar) {
    myNlEditorFixture = nlEditorFixture;
    myToolBar = toolbar;
  }

  public void zoomToFit() {
    Robot robot = myNlEditorFixture.robot();
    ActionButton zoomToFit =
      waitUntilShowingAndEnabled(robot, myToolBar.getComponent(), Matchers.byTooltip(ActionButton.class, "Zoom to Fit Screen (0)"));
    new ActionButtonFixture(robot, zoomToFit).click();
  }
}
