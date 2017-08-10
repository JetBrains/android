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
package com.android.tools.idea.tests.gui.framework.fixture.designer;

import com.android.tools.adtui.treegrid.TreeGrid;
import com.android.tools.idea.common.editor.NlEditor;
import com.android.tools.idea.common.editor.NlEditorPanel;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceDirectoryDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.*;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.palette.NlPaletteTreeGrid;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.structure.BackNavigationComponent;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, NlEditorPanel> {
  private final DesignSurfaceFixture<? extends DesignSurfaceFixture, ? extends DesignSurface> myDesignSurfaceFixture;
  private NlPropertyInspectorFixture myPropertyFixture;
  private final ComponentDragAndDrop myDragAndDrop;

  public NlEditorFixture(@NotNull Robot robot, @NotNull NlEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    DesignSurface surface = editor.getComponent().getSurface();
    if (surface instanceof NlDesignSurface) {
      myDesignSurfaceFixture = new NlDesignSurfaceFixture(robot, (NlDesignSurface)surface);
    }
    else if (surface instanceof NavDesignSurface) {
      myDesignSurfaceFixture = new NavDesignSurfaceFixture(robot, (NavDesignSurface)surface);
    }
    else {
      throw new RuntimeException("Unsupported DesignSurface type " + surface.getClass().getName());
    }
    myDragAndDrop = new ComponentDragAndDrop(robot);
  }

  public NlEditorFixture waitForRenderToFinish() {
    myDesignSurfaceFixture.waitForRenderToFinish();
    return this;
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return ((NlDesignSurfaceFixture)myDesignSurfaceFixture).findView(tag, occurrence);
  }

  public List<NlComponent> getSelection() {
    return myDesignSurfaceFixture.getSelection();
  }

  public double getScale() {
    return myDesignSurfaceFixture.getScale();
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public void waitForErrorPanelToContain(@NotNull String errorText) {
    myDesignSurfaceFixture.waitForErrorPanelToContain(errorText);
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

  @NotNull
  public DesignSurfaceFixture getSurface() {
    return myDesignSurfaceFixture;
  }

  @NotNull
  public NlConfigurationToolbarFixture<NlEditorFixture> getConfigToolbar() {
    ActionToolbar toolbar = robot().finder().findByName(target(), "NlConfigToolbar", ActionToolbarImpl.class);
    return new NlConfigurationToolbarFixture<>(this, robot(), myDesignSurfaceFixture.target(), toolbar);
  }

  @NotNull
  public NlViewActionToolbarFixture getComponentToolbar() {
    return NlViewActionToolbarFixture.create(this);
  }

  @NotNull
  public CreateResourceDirectoryDialogFixture getSelectResourceDirectoryDialog() {
    return new CreateResourceDirectoryDialogFixture(robot());
  }

  @NotNull
  public NlPropertyInspectorFixture getPropertiesPanel() {
    if (myPropertyFixture == null) {
      myPropertyFixture = new NlPropertyInspectorFixture(robot(), NlPropertyInspectorFixture.create(robot()));
    }
    return myPropertyFixture;
  }

  public NlRhsToolbarFixture getRhsToolbar() {
    ActionToolbarImpl toolbar = robot().finder().findByName(target(), "NlRhsToolbar", ActionToolbarImpl.class);
    return new NlRhsToolbarFixture(this, toolbar);
  }

  @NotNull
  public JTreeFixture getComponentTree() {
    JTreeFixture fixture = new JTreeFixture(robot(), (JTree)robot().finder().findByName(target(), "componentTree"));

    fixture.replaceCellReader((tree, value) -> {
      assert value != null;
      return ((NlComponent)value).getTagName();
    });

    Wait.seconds(10)
      .expecting("component tree to be populated")
      .until(() -> fixture.target().getPathForRow(0) != null);

    return fixture;
  }

  public JPanelFixture getBackNavigationPanel() {
    return new JPanelFixture(robot(), BackNavigationComponent.BACK_NAVIGATION_COMPONENT_NAME);
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String group, @NotNull String item) {
    NlPaletteTreeGrid treeGrid = robot().finder().findByType(NlPaletteTreeGrid.class, true);
    new JListFixture(robot(), treeGrid.getCategoryList()).selectItem(group);

    // Wait until the list has been expanded in UI (eliminating flakiness).
    JList list = GuiTests.waitUntilShowing(robot(), treeGrid, Matchers.byName(JList.class, group));
    new JListFixture(robot(), list).drag(item);
    DesignSurface target = myDesignSurfaceFixture.target();
    SceneView sceneView = target.getCurrentSceneView();
    assert sceneView != null;

    myDragAndDrop
      .drop(target, new Point(sceneView.getX() + sceneView.getSize().width / 2, sceneView.getY() + sceneView.getSize().height / 2));
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
    SceneView screenView = surface.getCurrentSceneView();
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
    DesignSurface surface = myDesignSurfaceFixture.target();
    SceneView screenView = surface.getCurrentSceneView();
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
    getConfigToolbar().selectDesign();
    return this;
  }

  /**
   * Ensures only the blueprint view is being displayed.
   * Only applicable if {@code target()} is a {@link NlDesignSurface}.
   */
  public NlEditorFixture showOnlyBlueprintView() {
    getConfigToolbar().selectBlueprint();
    return this;
  }

  public NlEditorFixture mouseWheelZoomIn(int amount) {
    robot().click(myDesignSurfaceFixture.target());
    robot().pressModifiers(InputEvent.CTRL_MASK);
    robot().rotateMouseWheel(myDesignSurfaceFixture.target(), amount);
    robot().releaseModifiers(InputEvent.CTRL_MASK);
    return this;
  }

  public NlEditorFixture mouseWheelScroll(int amount) {
    robot().click(myDesignSurfaceFixture.target());
    robot().rotateMouseWheel(myDesignSurfaceFixture.target(), amount);
    return this;
  }

  public NlEditorFixture dragMouseFromCenter(int dx, int dy, MouseButton mouseButton, int modifiers) {
    DesignSurface surface = myDesignSurfaceFixture.target();
    robot().moveMouse(surface);
    robot().pressModifiers(modifiers);
    robot().pressMouse(mouseButton);
    robot().moveMouse(surface, surface.getWidth() / 2 + dx, surface.getHeight() / 2 + dy);
    robot().releaseMouseButtons();
    robot().releaseModifiers(modifiers);
    return this;
  }

  @NotNull
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }

  public Point getScrollPosition() {
    return myDesignSurfaceFixture.target().getScrollPosition();
  }

  public IssuePanelFixture getIssuePanel() {
    return myDesignSurfaceFixture.getIssuePanelFixture();
  }

  public MorphDialogFixture openMorphDialogForComponent(NlComponentFixture componentFixture) {
    componentFixture.invokeContextMenuAction("Morph View...");
    return new MorphDialogFixture(robot());
  }
}
