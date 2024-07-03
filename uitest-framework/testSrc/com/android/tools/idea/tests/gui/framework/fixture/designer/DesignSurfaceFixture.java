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

import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManagerUtilsKt;
import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public abstract class DesignSurfaceFixture<T extends DesignSurfaceFixture, Surface extends DesignSurface<?>>
  extends ComponentFixture<T, Surface> {
  private final JPanel myProgressPanel;

  public DesignSurfaceFixture(@NotNull Class<T> myClass, @NotNull Robot robot,
                              @NotNull Surface designSurface) {
    super(myClass, robot, designSurface);
    myProgressPanel = robot.finder().findByName(target(), "Layout Editor Progress Panel", JPanel.class, false);
  }

  public final void waitForRenderToFinish() {
    waitForRenderToFinish(Wait.seconds(20));
  }

  public void waitForRenderToFinish(@NotNull Wait wait) {
    wait.expecting("render to finish").until(() -> !myProgressPanel.isShowing());
  }

  public boolean hasRenderErrors() {
    Collection<SceneView> sceneViews = target().getSceneViews();
    for (SceneView sceneView : sceneViews) {
      if (LayoutlibSceneManagerUtilsKt.hasRenderErrors(sceneView)) {
        return true;
      }
    }
    return false;
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
    SceneView view = target().getFocusedSceneView();
    return view == null ? Collections.emptyList() : view.getSelectionModel().getSelection();
  }

  public double getScale() {
    return target().getZoomController().getScale();
  }

  /**
   * Clicks in the design surface on the point that corresponds to {@link AndroidCoordinate} (x, y)
   */
  public void doubleClick(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    SceneView view = target().getFocusedSceneView();
    Point point = new Point(Coordinates.getSwingX(view, x), Coordinates.getSwingY(view, y));
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
    SceneView sceneView = target().getFocusedSceneView();
    if (sceneView == null) {
      return Collections.emptyList();
    }

    return sceneView.getSceneManager().getModel().getTreeReader().flattenComponents()
      .map(this::createComponentFixture)
      .collect(Collectors.toList());
  }

  @NotNull
  public SceneFixture getScene() {
    return new SceneFixture(robot(), target().getScene());
  }

  @NotNull
  public List<SceneViewFixture> getAllSceneViews() {
    return target().getModels().stream()
      .map(model -> target().getSceneManager(model))
      .map(sceneManager -> sceneManager.getSceneView())
      .map(sceneView -> new SceneViewFixture(robot(), sceneView))
      .collect(Collectors.toList());
  }
}
