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

import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlPaletteFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.AnchoredButton;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
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
    ActionToolbar toolbar = GuiTests.waitUntilShowing(myRobot, Matchers.byName(ActionToolbarImpl.class, "NlConfigToolbar"));
    return new NlConfigurationToolbarFixture<>(this, myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byType(NlDesignSurface.class)),
                                               toolbar);
  }

  @NotNull
  private NlPaletteFixture openPalette() {
    NlPaletteFixture palette;
    Container workBench = SwingUtilities.getAncestorOfClass(WorkBench.class, myDesignSurfaceFixture.target());

    try {
      // Check if the palette is already open
      palette = NlPaletteFixture.create(myRobot, workBench);
    }
    catch (ComponentLookupException e) {
      // The Palette was not showing. Open the palette by clicking on "Palette" tool window icon.
      new JToggleButtonFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byText(AnchoredButton.class, "Palette "))).click();
      palette = NlPaletteFixture.create(myRobot, workBench);
    }
    return palette;
  }

  @NotNull
  public NlPreviewFixture dragComponentToSurface(@NotNull String group, @NotNull String item) {
    openPalette().dragComponent(group, item);
    myDragAndDrop.drop(myDesignSurfaceFixture.target(), new Point(0, 0));
    return this;
  }

  public NlPreviewFixture waitForRenderToFinish() {
    waitUntilIsVisible();
    myDesignSurfaceFixture.waitForRenderToFinish();
    return this;
  }

  public NlPreviewFixture waitForRenderToFinishAndApplyComponentDimensions() {
    waitForRenderToFinish();
    Wait.seconds(3).expecting("render image to update model").until(this::allComponentsHaveHeight);
    return this;
  }

  private boolean allComponentsHaveHeight() {
    return getAllComponents().stream()
      .noneMatch(component -> component.getHeight() == 0);
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

  public void waitForScreenMode(@NotNull SceneMode mode) {
    Wait.seconds(1).expecting("the design surface to be in mode " + mode).until(() -> myDesignSurfaceFixture.isInScreenMode(mode));
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

    Dimension contentDimension = surface.getContentSize(null);
    return new Point(surface.getContentOriginX() , surface.getContentOriginY() + (contentDimension.height - contentDimension.width + 1) / 2);
  }

  @NotNull
  public String getAdaptiveIconPathDescription() {
    return myDesignSurfaceFixture.target().getAdaptiveIconShape().getPathDescription();
  }
}
