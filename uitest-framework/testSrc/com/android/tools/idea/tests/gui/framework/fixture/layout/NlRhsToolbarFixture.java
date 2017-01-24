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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.uibuilder.surface.PanZoomPanel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Locale;
import java.util.function.Predicate;

import static com.android.tools.idea.tests.gui.framework.GuiTests.clickPopupMenuItemMatching;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.android.tools.idea.uibuilder.surface.PanZoomPanel.TITLE;

/**
 * Fixture representing the configuration toolbar above an associated layout editor
 */
public class NlRhsToolbarFixture {
  private final Robot myRobot;
  private final ActionToolbar myToolBar;
  private final EditorDesignSurface mySurface;

  public NlRhsToolbarFixture(@NotNull Robot robot, @NotNull EditorDesignSurface surface, @NotNull ActionToolbar toolbar) {
    myRobot = robot;
    myToolBar = toolbar;
    mySurface = surface;
  }

  public void openPanZoomWindow() {
    ActionButton button = waitUntilShowing(myRobot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton component) {
        String text = component.getAction().getTemplatePresentation().getText();
        return text != null && text.contains("Pan and Zoom");
      }
    });
    new ActionButtonFixture(myRobot, button).click();
  }

  @NotNull
  private JButton findToolbarButton(@NotNull final String tooltip) {
    return waitUntilShowing(myRobot, Matchers.byTooltip(JButton.class, tooltip));
  }
}
