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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.AnchoredButton;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JToggleButtonFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Fixture for the layout editor preview window
 */
public class NlPreviewFixture extends ToolWindowFixture {
  private final DesignSurfaceFixture myDesignSurfaceFixture;
  private ComponentDragAndDrop myDragAndDrop;

  public NlPreviewFixture(@NotNull Project project, @NotNull IdeFrameFixture frame, @NotNull Robot robot) {
    super("Preview", project, robot);
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, frame, GuiTests.waitUntilShowing(robot, Matchers.byType(DesignSurface.class)));
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  @NotNull
  public NlConfigurationToolbarFixture getConfigToolbar() {
    ActionToolbar toolbar = myRobot.finder().findByName("NlConfigToolbar", ActionToolbarImpl.class, false);
    Wait.seconds(1).expecting("Configuration toolbar to be showing").until(() -> toolbar.getComponent().isShowing());
    return new NlConfigurationToolbarFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(DesignSurface.class)), toolbar);
  }

  @NotNull
  public NlPreviewFixture openPalette() {
    // Check if the palette is already open
    try {
      myRobot.finder().findByName("Palette Tree", JTree.class, true);
    } catch (ComponentLookupException e) {
      new JToggleButtonFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byText(AnchoredButton.class, "Palette"))).click();
    }

    return this;
  }

  @NotNull
  public NlPreviewFixture dragComponentToSurface(@NotNull String path) {
    openPalette();
    JTree tree = myRobot.finder().findByName("Palette Tree", JTree.class, true);
    new JTreeFixture(myRobot, tree).drag(path);
    myDragAndDrop.drop(myDesignSurfaceFixture.target(), new Point(0, 0));
    return this;
  }

  public NlPreviewFixture waitForRenderToFinish() {
    waitUntilIsVisible();
    myDesignSurfaceFixture.waitForRenderToFinish();
    return this;
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public boolean errorPanelContains(@NotNull String errorText) {
    return myDesignSurfaceFixture.errorPanelContains(errorText);
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public void requireSelection(@NotNull List<NlComponentFixture> components) {
    myDesignSurfaceFixture.requireSelection(components);
  }

  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }
}
