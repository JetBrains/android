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
package com.android.tools.idea.tests.gui.framework.fixture.designer;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.IssuePanelFixture;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DesignSurfaceFixture<T extends DesignSurfaceFixture, Surface extends DesignSurface>
  extends ComponentFixture<T, Surface> {
  private final JPanel myProgressPanel;
  private final IssuePanelFixture myIssuePanelFixture;

  public DesignSurfaceFixture(@NotNull Class<T> myClass, @NotNull Robot robot,
                              @NotNull Surface designSurface) {
    super(myClass, robot, designSurface);
    myProgressPanel = robot.finder().findByName(target(), "Layout Editor Progress Panel", JPanel.class, false);
    myIssuePanelFixture = new IssuePanelFixture(robot, designSurface.getIssuePanel());
  }

  public void waitForRenderToFinish() {
    Wait.seconds(10).expecting("render to finish").until(() -> !myProgressPanel.isVisible());
  }

  public boolean hasRenderErrors() {
    return myIssuePanelFixture.hasRenderError();
  }

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    Wait.seconds(1)
      .expecting("the error panel to contain: " + errorText)
      .until(() -> myIssuePanelFixture.containsText(errorText));
  }

  public IssuePanelFixture getIssuePanelFixture() {
    return myIssuePanelFixture;
  }

  @NotNull
  protected NlComponentFixture createComponentFixture(@NotNull NlComponent component) {
    return new NlComponentFixture(robot(), component, target());
  }

  /**
   * Returns a list of the selected views
   */
  @NotNull
  public List<NlComponent> getSelection() {
    SceneView view = target().getCurrentSceneView();
    return view == null ? Collections.emptyList() : view.getModel().getSelectionModel().getSelection();
  }

  public double getScale() {
    return target().getScale();
  }

  public void doubleClick(@NotNull Point point) {
    robot().click(target(), point, MouseButton.LEFT_BUTTON, 2);
  }

  public void drop(@NotNull Point point) {
    new ComponentDragAndDrop(robot()).drop(target(), point);
  }

  /**
   * Returns the views and all the children
   */
  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    SceneView sceneView = target().getCurrentSceneView();
    if (sceneView == null) {
      return Collections.emptyList();
    }

    return sceneView.getModel().flattenComponents()
      .map(this::createComponentFixture)
      .collect(Collectors.toList());
  }
}
