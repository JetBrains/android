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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;

import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.visual.VisualizationForm;
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import icons.StudioIcons;
import java.awt.event.KeyEvent;
import java.util.stream.Collectors;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

/**
 * Fixture for the Visualization tool window
 */
public class VisualizationFixture extends ToolWindowFixture {
  private final NlDesignSurfaceFixture myDesignSurfaceFixture;

  public VisualizationFixture(@NotNull Project project, @NotNull Robot robot) {
    super(VisualizationToolWindowFactory.TOOL_WINDOW_ID, project, robot);
    myDesignSurfaceFixture = new NlDesignSurfaceFixture(
      robot, GuiTests.waitUntilShowing(robot, null, Matchers.byName(NlDesignSurface.class, VisualizationForm.VISUALIZATION_DESIGN_SURFACE_NAME), 20));
  }

  public VisualizationFixture waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish(Wait.seconds(60));
    return this;
  }

  public void expandWindow() {
    myDesignSurfaceFixture.focus();
    pressControlKeyAndOtherKey(KeyEvent.VK_QUOTE);
  }

  private void pressControlKeyAndOtherKey(int keyEvent) {
    if (SystemInfo.isMac) {
      robot().pressKey(KeyEvent.CTRL_MASK);
      robot().pressKey(KeyEvent.SHIFT_MASK);
      robot().pressAndReleaseKey(keyEvent);
      robot().releaseKey(KeyEvent.CTRL_MASK);
      robot().releaseKey(KeyEvent.SHIFT_MASK);
    }
    else {
      robot().pressAndReleaseKey(keyEvent, KeyEvent.CTRL_MASK, KeyEvent.SHIFT_MASK);
    }
  }

  /**
   * Zoom to fit the screen
   */
  public void zoomToFit() {
    myDesignSurfaceFixture.target().getZoomController().zoomToFit();
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  /**
   * To use the Zoom In feature from the Pan button
   */
  public void zoomIn() {
    myDesignSurfaceFixture.target().getZoomController().zoom(ZoomType.IN);
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  /**
   * To use the Zoom out feature from the Pan button
   */
  public void zoomOut() {
    myDesignSurfaceFixture.target().getZoomController().zoom((ZoomType.OUT));
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  /**
   * To zoom to the 100% or 1:1
   */
  public  void zoomToActual() {
    myDesignSurfaceFixture.target().getZoomController().zoom(ZoomType.ACTUAL);
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  public void clickZoomButton(String buttonName) {
    ActionButton button = waitUntilShowingAndEnabled(robot(), myDesignSurfaceFixture.target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return buttonName.equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    robot().focus(button);
    robot().click(button);
    Wait.seconds(10);
  }

  public double getScale() {
    return myDesignSurfaceFixture.getScale();
  }

  public boolean panButtonPresent() {
    ActionButton panButton = waitUntilShowingAndEnabled(robot(), myDesignSurfaceFixture.target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "Pan screen (hold SPACE bar and drag)".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    return (panButton.isEnabled() && panButton.isShowing());
  }

  public int getRowNumber() {
    return myDesignSurfaceFixture.getAllSceneViews().stream().map(sceneView -> sceneView.getTopLeft().y).collect(Collectors.toSet()).size();
  }

  @NotNull
  public String getCurrentFileName() {
    return myDesignSurfaceFixture.target().getModel().getVirtualFile().getName();
  }

  public void openProblemsPanel() {
    ActionButtonFixture.findByIcon(StudioIcons.Common.WARNING_INLINE, myRobot, myToolWindow.getComponent()).click();
  }
}
