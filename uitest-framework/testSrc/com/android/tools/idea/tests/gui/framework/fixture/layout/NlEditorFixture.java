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

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, NlEditorPanel> {
  private final IdeFrameFixture myFrame;
  private final DesignSurfaceFixture myDesignSurfaceFixture;
  private NlPropertyInspectorFixture myPropertyFixture;
  private ComponentDragAndDrop myDragAndDrop;

  public NlEditorFixture(@NotNull Robot robot, @NotNull IdeFrameFixture frame, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    myFrame = frame;
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, frame, editor.getComponent().getSurface());
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  public NlEditorFixture waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish();
    return this;
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public void requireSelection(@NotNull List<NlComponentFixture> components) {
    myDesignSurfaceFixture.requireSelection(components);
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public boolean errorPanelContains(@NotNull String errorText) {
    return myDesignSurfaceFixture.errorPanelContains(errorText);
  }

  @NotNull
  public NlPropertyInspectorFixture getPropertyInspector() {
    if (myPropertyFixture == null) {
      myPropertyFixture = new NlPropertyInspectorFixture(robot(), NlPropertyInspectorFixture.create(robot()));
    }
    return myPropertyFixture;
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String path) {
    JTree tree = robot().finder().findByName("Palette Tree", JTree.class, true);
    new JTreeFixture(robot(), tree).drag(path);
    myDragAndDrop.drop(myDesignSurfaceFixture.target(), new Point(0, 0));
    return this;
  }

  /**
   * Moves the mouse to the resize corner of the screen view, and presses the left mouse button.
   * That starts the canvas resize interaction.
   *
   * @see #resizeToAndroidSize(int, int)
   * @see #endResizeInteraction()
   */
  public NlEditorFixture startResizeInteraction() {
    DesignSurface surface = myDesignSurfaceFixture.target();
    ScreenView screenView = surface.getCurrentScreenView();
    Dimension size = screenView.getSize();
    robot().pressMouse(surface, new Point(screenView.getX() + size.width + 24, screenView.getY() + size.height + 24));
    return this;
  }

  /**
   * Moves the mouse to resize the screen view to correspond to a device of size {@code (width, height)}, expressed in dp
   *
   * @see #startResizeInteraction()
   * @see #endResizeInteraction()
   */
  public NlEditorFixture resizeToAndroidSize(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height) {
    DesignSurface surface = myDesignSurfaceFixture.target();
    ScreenView screenView = surface.getCurrentScreenView();
    robot().moveMouse(surface, Coordinates.getSwingXDip(screenView, width), Coordinates.getSwingYDip(screenView, height));
    return this;
  }

  /**
   * Releases left mouse button to end resize interaction.
   *
   * @see #startResizeInteraction()
   * @see #resizeToAndroidSize(int, int)
   */
  public NlEditorFixture endResizeInteraction() {
    robot().releaseMouse(MouseButton.LEFT_BUTTON);
    return this;
  }

  /**
   * Ensures only the design view is being displayed.
   */
  public NlEditorFixture showOnlyDesignView() {
    DesignSurface surface = myDesignSurfaceFixture.target();
    if (surface.getScreenMode() != DesignSurface.ScreenMode.SCREEN_ONLY) {
      getConfigToolbar().showDesign();
    }
    return this;
  }

  @NotNull
  public NlConfigurationToolbarFixture getConfigToolbar() {
    ActionToolbar toolbar = robot().finder().findByName(target(), "NlConfigToolbar", ActionToolbarImpl.class);
    return new NlConfigurationToolbarFixture(robot(), myDesignSurfaceFixture.target(), toolbar);
  }

  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }
}
