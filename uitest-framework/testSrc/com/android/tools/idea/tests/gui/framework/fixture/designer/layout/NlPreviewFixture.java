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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.AnchoredButton;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JToggleButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.fest.swing.awt.AWT.translate;

/**
 * Fixture for the layout editor preview window
 */
public class NlPreviewFixture extends ToolWindowFixture {
  private final NlDesignSurfaceFixture myDesignSurfaceFixture;
  private final ComponentDragAndDrop myDragAndDrop;
  private java.awt.Robot myAwtRobot;

  public NlPreviewFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Preview", project, robot);
    myDesignSurfaceFixture = new NlDesignSurfaceFixture(
      robot, GuiTests.waitUntilShowing(robot, Matchers.byName(NlDesignSurface.class, NlPreviewForm.PREVIEW_DESIGN_SURFACE)));
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  @NotNull
  public NlConfigurationToolbarFixture<NlPreviewFixture> getConfigToolbar() {
    ActionToolbar toolbar = myRobot.finder().findByName("NlConfigToolbar", ActionToolbarImpl.class, false);
    Wait.seconds(1).expecting("Configuration toolbar to be showing").until(() -> toolbar.getComponent().isShowing());
    return new NlConfigurationToolbarFixture<>(this, myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(NlDesignSurface.class)),
                                               toolbar);
  }

  @NotNull
  public NlPreviewFixture openPalette() {
    // Check if the palette is already open
    try {
      myRobot.finder().findByType(NlPaletteTreeGrid.class, true);
    }
    catch (ComponentLookupException e) {
      new JToggleButtonFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byText(AnchoredButton.class, "Palette "))).click();
    }

    return this;
  }

  @NotNull
  public NlPreviewFixture dragComponentToSurface(@NotNull String group, @NotNull String item) {
    openPalette();
    NlPaletteTreeGrid treeGrid = myRobot.finder().findByType(NlPaletteTreeGrid.class, true);
    Wait.seconds(5).expecting("the UI to be populated").until(() -> treeGrid.getCategoryList().getModel().getSize() > 0);
    new JListFixture(myRobot, treeGrid.getCategoryList()).selectItem(group);

    // Wait until the list has been expanded in UI (eliminating flakiness).
    JList list = GuiTests.waitUntilShowing(myRobot, treeGrid, Matchers.byName(JList.class, group));
    new JListFixture(myRobot, list).drag(item);
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

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    myDesignSurfaceFixture.waitForErrorPanelToContain(errorText);
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public List<NlComponent> getSelection() {
    return myDesignSurfaceFixture.getSelection();
  }

  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }

  /**
   * Switch to showing only the blueprint view.
   */
  public NlPreviewFixture showOnlyBlueprintView() {
    getConfigToolbar().selectBlueprint();
    return this;
  }

  public NlPreviewFixture waitForScreenMode(@NotNull NlDesignSurface.ScreenMode mode) {
    Wait.seconds(1).expecting("the design surface to be in mode " + mode).until(() -> myDesignSurfaceFixture.isInScreenMode(mode));
    return this;
  }

  /**
   * Returns an HEX string corresponding to the value of the color of the pixel at point p in the design surface reference frame
   */
  @NotNull
  public String getPixelColor(@NotNull Point p) {
    NlDesignSurface surface = myDesignSurfaceFixture.target();

    SceneView view = surface.getCurrentSceneView();
    assert view != null;

    Point centerLeftPoint = translate(surface, p.x, p.y);
    assert centerLeftPoint != null;

    if (myAwtRobot == null) {
      try {
        myAwtRobot = new java.awt.Robot();
      }
      catch (AWTException e) {
        e.printStackTrace();
      }
    }
    return Integer.toHexString(myAwtRobot.getPixelColor(centerLeftPoint.x, centerLeftPoint.y).getRGB());
  }

  @NotNull
  public Point getAdaptiveIconTopLeftCorner() {
    NlDesignSurface surface = myDesignSurfaceFixture.target();

    SceneView view = surface.getCurrentSceneView();
    assert view != null;
    return new Point(view.getX(), (surface.getHeight() - view.getSize().width + 1) / 2);
  }

  @NotNull
  public String getAdaptiveIconPathDescription() {
    return myDesignSurfaceFixture.target().getAdaptiveIconShape().getPathDescription();
  }
}
