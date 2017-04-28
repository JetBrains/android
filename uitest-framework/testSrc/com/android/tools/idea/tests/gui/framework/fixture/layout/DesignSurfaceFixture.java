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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.uibuilder.error.IssueView;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertTrue;

public class DesignSurfaceFixture extends ComponentFixture<DesignSurfaceFixture, NlDesignSurface> {
  private final JPanel myProgressPanel;
  private final IssuePanelFixture myIssuePanelFixture;

  public DesignSurfaceFixture(@NotNull Robot robot, @NotNull NlDesignSurface designSurface) {
    super(DesignSurfaceFixture.class, robot, designSurface);
    myProgressPanel = robot.finder().findByName(target(), "Layout Editor Progress Panel", JPanel.class, false);
    myIssuePanelFixture = new IssuePanelFixture(robot, designSurface.getIssuePanel());
  }

  public void waitForRenderToFinish(@NotNull Wait waitForRender) {
    waitForRender.expecting("render to finish").until(() -> !myProgressPanel.isVisible());
    waitForRender.expecting("render to finish").until(() -> {
      ScreenView screenView = target().getCurrentSceneView();
      if (screenView == null) {
        return false;
      }
      RenderResult result = screenView.getResult();
      if (result == null) {
        return false;
      }
      if (result.getLogger().hasErrors()) {
        return target().isShowing() && myIssuePanelFixture.hasRenderError();
      }
      return target().isShowing() && !myIssuePanelFixture.hasRenderError();
    });
    // Wait for the animation to finish
    pause(SceneComponent.ANIMATION_DURATION);
  }

  public boolean hasRenderErrors() {
    return myIssuePanelFixture.hasRenderError();
  }

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    Wait.seconds(1)
      .expecting("the error panel to contain: " + errorText)
      .until(() -> myIssuePanelFixture.containsText(errorText));
  }

  @Nullable
  public String getErrorText() {
    IssueView view = myIssuePanelFixture.target().getSelectedIssueView();
    if (view == null) {
      return null;
    }
    return view.getIssueDescription();
  }

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   *
   * @param tag        the view tag to search for, e.g. "Button" or "TextView"
   * @param occurrence the index of the occurrence of the tag, e.g. 0 for the first TextView in the layout
   */
  @NotNull
  public NlComponentFixture findView(@NotNull final String tag, int occurrence) {
    waitForRenderToFinish(Wait.seconds(5));

    ScreenView view = target().getCurrentSceneView();
    assert view != null;

    final NlModel model = view.getModel();
    final java.util.List<NlComponent> components = Lists.newArrayList();

    model.getComponents().forEach(component -> addComponents(tag, component, components));
    // Sort by visual order
    components.sort((component1, component2) -> {
      int delta = component1.y - component2.y;
      if (delta != -1) {
        return delta;
      }
      delta = component1.x - component2.x;
      if (delta != -1) {
        return delta;
      }
      // Unlikely
      return component1.getTag().getTextOffset() - component2.getTag().getTextOffset();
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    NlComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }

  public IssuePanelFixture getIssuePanelFixture() {
    return myIssuePanelFixture;
  }

  @NotNull
  private NlComponentFixture createComponentFixture(@NotNull NlComponent component) {
    return new NlComponentFixture(robot(), component, target());
  }

  private static void addComponents(@NotNull String tag, @NotNull NlComponent component, @NotNull List<NlComponent> components) {
    if (tag.equals(component.getTagName())) {
      components.add(component);
    }

    for (NlComponent child : component.getChildren()) {
      addComponents(tag, child, components);
    }
  }

  /**
   * Returns the views and all the children
   */
  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    ScreenView screenView = target().getCurrentSceneView();
    if (screenView == null) {
      return Collections.emptyList();
    }

    return screenView.getModel().flattenComponents()
      .map(this::createComponentFixture)
      .collect(Collectors.toList());
  }

  /**
   * Returns a list of the selected views
   */
  @NotNull
  public List<NlComponent> getSelection() {
    ScreenView view = target().getCurrentSceneView();
    return view == null ? Collections.emptyList() : view.getModel().getSelectionModel().getSelection();
  }

  public double getScale() {
    return target().getScale();
  }

  // Only applicable to NlDesignSurface
  public boolean isInScreenMode(@NotNull NlDesignSurface.ScreenMode mode) {
    return target().getScreenMode() == mode;
  }

  public void doubleClick(@NotNull Point point) {
    robot().click(target(), point, MouseButton.LEFT_BUTTON, 2);
  }

  public void drop(@NotNull Point point) {
    new ComponentDragAndDrop(robot()).drop(target(), point);
  }
}
