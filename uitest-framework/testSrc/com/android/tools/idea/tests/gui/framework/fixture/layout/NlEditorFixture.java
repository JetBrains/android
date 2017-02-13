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

import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.editor.NlEditorPanel;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, NlEditorPanel> {
  private final DesignSurfaceFixture myDesignSurfaceFixture;
  private NlPropertyInspectorFixture myPropertyFixture;
  private ComponentDragAndDrop myDragAndDrop;

  public NlEditorFixture(@NotNull Robot robot, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, (NlDesignSurface)editor.getComponent().getSurface());
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  public NlEditorFixture waitForRenderToFinish() {
    return waitForRenderToFinish(Wait.seconds(5));
  }

  public NlEditorFixture waitForRenderToFinish(@NotNull Wait waitForRender) {
    myDesignSurfaceFixture.waitForRenderToFinish(waitForRender);
    return this;
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }

  public List<NlComponent> getSelection() {
    return myDesignSurfaceFixture.getSelection();
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    myDesignSurfaceFixture.waitForErrorPanelToContain(errorText);
  }

  @Nullable
  public String getErrorText() {
    return myDesignSurfaceFixture.getErrorText();
  }

  @NotNull
  public JListFixture getPaletteItemList(int i) {
    Robot robot = robot();

    @SuppressWarnings("unchecked")
    TreeGrid<Palette.Item> grid = (TreeGrid<Palette.Item>)robot.finder().findByName(target(), "itemTreeGrid");

    JListFixture fixture = new JListFixture(robot, grid.getLists().get(i));
    fixture.replaceCellReader((list, listIndex) -> ((Palette.Item)list.getModel().getElementAt(listIndex)).getTitle());

    return fixture;
  }

  @Nullable
  public JListFixture getSelectedItemList() {
    Robot robot = robot();

    @SuppressWarnings("unchecked")
    TreeGrid<Palette.Item> grid = (TreeGrid<Palette.Item>)robot.finder().findByName(target(), "itemTreeGrid");

    JList<Palette.Item> selectedList = grid.getSelectedList();
    if (selectedList == null) {
      return null;
    }
    JListFixture fixture = new JListFixture(robot, selectedList);
    fixture.replaceCellReader((list, listIndex) -> ((Palette.Item)list.getModel().getElementAt(listIndex)).getTitle());

    return fixture;
  }

  @NotNull
  public NlConfigurationToolbarFixture getConfigToolbar() {
    ActionToolbar toolbar = robot().finder().findByName(target(), "NlConfigToolbar", ActionToolbarImpl.class);
    return new NlConfigurationToolbarFixture(robot(), myDesignSurfaceFixture.target(), toolbar);
  }

  @NotNull
  public NlPropertyInspectorFixture getPropertyInspector() {
    if (myPropertyFixture == null) {
      myPropertyFixture = new NlPropertyInspectorFixture(robot(), NlPropertyInspectorFixture.create(robot()));
    }
    return myPropertyFixture;
  }

  public NlRhsToolbarFixture getRhsToolbar() {
    ActionToolbarImpl toolbar = robot().finder().findByName(target(), "NlRhsToolbar", ActionToolbarImpl.class);
    return new NlRhsToolbarFixture(robot(), myDesignSurfaceFixture.target(), toolbar);
  }

  @NotNull
  public JTreeFixture getComponentTree() {
    JTreeFixture fixture = new JTreeFixture(robot(), (JTree)robot().finder().findByName(target(), "componentTree"));

    fixture.replaceCellReader((tree, value) -> {
      assert value != null;
      return ((NlComponent)value).getTagName();
    });

    return fixture;
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String group, @NotNull String item) {
    NlPaletteTreeGrid treeGrid = robot().finder().findByType(NlPaletteTreeGrid.class, true);
    new JListFixture(robot(), treeGrid.getCategoryList()).selectItem(group);

    // Wait until the list has been expanded in UI (eliminating flakiness).
    JList list = GuiTests.waitUntilShowing(robot(), treeGrid, Matchers.byName(JList.class, group));
    new JListFixture(robot(), list).drag(item);
    NlDesignSurface target = myDesignSurfaceFixture.target();
    myDragAndDrop.drop(target, new Point(target.getWidth() / 2, target.getHeight() / 2));
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
    NlDesignSurface surface = myDesignSurfaceFixture.target();
    ScreenView screenView = surface.getCurrentSceneView();
    assert screenView != null;

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
    NlDesignSurface surface = myDesignSurfaceFixture.target();
    ScreenView screenView = surface.getCurrentSceneView();
    assert screenView != null;

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
   * Only applicable if {@code target()} is a {@link NlDesignSurface}.
   */
  public NlEditorFixture showOnlyDesignView() {
    NlDesignSurface surface = myDesignSurfaceFixture.target();
    if (surface.getScreenMode() != NlDesignSurface.ScreenMode.SCREEN_ONLY) {
      getConfigToolbar().showDesign();
    }
    return this;
  }

  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }
}
