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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static junit.framework.TestCase.assertTrue;
import static org.fest.swing.awt.AWT.translate;

import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.editor.DesignerEditor;
import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentTreeFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WorkBenchLoadingPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.IssuePanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.MorphDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlDesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlRhsConfigToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlViewActionToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.HostPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.properties.PropertiesPanelFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.structure.BackNavigationComponent;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.util.SystemInfo;
import java.awt.AWTException;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JMenuItemFixture;
import org.fest.swing.fixture.JPanelFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Pause;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

/**
 * Fixture wrapping the the layout editor for a particular file
 * TODO(b/119869057): Split logic of NlEditorFixture into NlEditorFixture and NavEditorFixture
 */
public class NlEditorFixture extends ComponentFixture<NlEditorFixture, DesignerEditorPanel> {
  private final DesignSurfaceFixture<? extends DesignSurfaceFixture, ? extends DesignSurface<?>> myDesignSurfaceFixture;
  private PropertiesPanelFixture<NlPropertyItem> myPropertiesFixture;
  private NlPaletteFixture myPaletteFixture;
  private WorkBenchLoadingPanelFixture myLoadingPanelFixture;
  private final ComponentDragAndDrop myDragAndDrop;
  private java.awt.Robot myAwtRobot;

  public NlEditorFixture(@NotNull Robot robot, @NotNull DesignerEditor editor) {
    super(NlEditorFixture.class, robot, editor.getComponent());
    DesignSurface<?> surface = editor.getComponent().getSurface();
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

    myLoadingPanelFixture = new WorkBenchLoadingPanelFixture(robot, target().getWorkBench().getLoadingPanel());
  }

  @NotNull
  public SceneFixture getScene() {
    return myDesignSurfaceFixture.getScene();
  }

  @NotNull
  public NlEditorFixture waitForRenderToFinish() {
    waitForRenderToFinish(Wait.seconds(10));
    return this;
  }

  @NotNull
  public NlEditorFixture waitForRenderToFinish(@NotNull Wait wait) {
    myDesignSurfaceFixture.waitForRenderToFinish(wait);
    wait.expecting("WorkBench is showing").until(() -> !myLoadingPanelFixture.isLoading());
    // Fade out of the loading panel takes 500ms
    Pause.pause(1000);
    return this;
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return ((NlDesignSurfaceFixture)myDesignSurfaceFixture).findView(tag, occurrence);
  }

  @NotNull
  public List<NlComponent> getSelection() {
    return myDesignSurfaceFixture.getSelection();
  }

  public double getScale() {
    return myDesignSurfaceFixture.getScale();
  }

  public void zoomIn() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    robot().click(surface);

    Wait.seconds(10);
    ActionButton zoomInButton = waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "Zoom In".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    robot().focus(zoomInButton);
    robot().click(zoomInButton);
    Wait.seconds(10);
  }

  public void zoomInByShortcutKeys() {
    robot().pressAndReleaseKey(KeyEvent.VK_ADD, SystemInfo.isMac ? META_MASK : CTRL_MASK);
  }

  public void zoomOutByShortcutKeys() {
    robot().pressAndReleaseKey(KeyEvent.VK_MINUS, SystemInfo.isMac ? META_MASK : CTRL_MASK);
  }

  public void zoomtoFitByShortcutKeys() {
    robot().pressAndReleaseKey(KeyEvent.VK_SLASH, SystemInfo.isMac ? META_MASK : CTRL_MASK);
  }

  public void zoomto100PercentByShortcutKeys() {
    robot().pressAndReleaseKey(KeyEvent.VK_0, SystemInfo.isMac ? META_MASK : CTRL_MASK);
  }

  public boolean panButtonPresent() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    robot().click(surface);

    Wait.seconds(10);
    ActionButton panButton = waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "Pan screen (hold SPACE bar and drag)".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    return (panButton.isEnabled() && panButton.isShowing());
  }

  public void zoomOut() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    robot().click(surface);

    Wait.seconds(10);
    ActionButton zoomOutButton = waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "Zoom Out".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    robot().focus(zoomOutButton);
    robot().click(zoomOutButton);
    Wait.seconds(10);
  }

  /**
   * Waits for the design tab of the layout editor to either load (waiting for sync and indexing
   * to complete if necessary) or display an error message. Callers can check whether or not the
   * design editor is usable after this method completes by calling canInteractWithSurface().
   *
   * @see #canInteractWithSurface()
   */
  @NotNull
  public NlEditorFixture waitForSurfaceToLoad() {
    // A long timeout is necessary in case the IDE needs to perform indexing (since the design surface will not render
    // until sync and indexing are finished). We can't simply wait for background tasks to complete before calling
    // this method because there might be a delay between the sync and indexing steps that causes the wait to finish prematurely.
    Wait.seconds(90).expecting("Design surface finished loading").until(() -> {
      if (myLoadingPanelFixture.hasError()) {
        return true;
      }

      return myDesignSurfaceFixture.target().isShowing();
    });

    waitForRenderToFinish(Wait.seconds(90));

    return this;
  }

  public boolean canInteractWithSurface() {
    return !myLoadingPanelFixture.hasError() && myDesignSurfaceFixture.target().isShowing();
  }

  public void assertCanInteractWithSurface() {
    assertTrue(canInteractWithSurface());
  }

  @NotNull
  public DesignSurfaceFixture<? extends DesignSurfaceFixture, ? extends DesignSurface<?>>  getSurface() {
    return myDesignSurfaceFixture;
  }

  @NotNull
  public NavDesignSurfaceFixture getNavSurface() {
    return (NavDesignSurfaceFixture)myDesignSurfaceFixture;
  }

  @NotNull
  public NlConfigurationToolbarFixture<NlEditorFixture> getConfigToolbar() {
    ActionToolbar toolbar = GuiTests.waitUntilShowing(robot(), target(), Matchers.byName(ActionToolbarImpl.class, "NlConfigToolbar"));
    return new NlConfigurationToolbarFixture<>(this, robot(), myDesignSurfaceFixture.target(), toolbar);
  }

  @NotNull
  public NlViewActionToolbarFixture getComponentToolbar() {
    return NlViewActionToolbarFixture.create(this);
  }

  @NotNull
  public PropertiesPanelFixture<NlPropertyItem> getAttributesPanel() {
    if (myPropertiesFixture == null) {
      myPropertiesFixture = PropertiesPanelFixture.Companion.findPropertiesPanelInContainer(target(), robot());
    }
    return myPropertiesFixture;
  }

  @NotNull
  public NlRhsConfigToolbarFixture getRhsConfigToolbar() {
    ActionToolbarImpl toolbar =
      GuiTests.waitUntilShowing(robot(), target(), Matchers.byName(ActionToolbarImpl.class, "NlRhsConfigToolbar"));
    return new NlRhsConfigToolbarFixture(this, toolbar);
  }

  @NotNull
  public JTreeFixture getComponentTree() {
    JTreeFixture fixture = new JTreeFixture(robot(), (JTree)robot().finder().findByName(target(), "componentTree"));

    fixture.replaceCellReader((tree, value) -> ((NlComponent)value).getTagName());

    Wait.seconds(10)
      .expecting("component tree to be populated")
      .until(() -> fixture.target().getPathForRow(0) != null);

    return fixture;
  }

  @NotNull
  public JPanelFixture getBackNavigationPanel() {
    JPanel backNavPanel = GuiTests.waitUntilShowing(robot(),
                                                    Matchers.byName(JPanel.class, BackNavigationComponent.BACK_NAVIGATION_COMPONENT_NAME));
    return new JPanelFixture(robot(), backNavPanel);
  }

  @NotNull
  public NlPaletteFixture getPalette() {
    if (myPaletteFixture == null) {
      Wait.seconds(10).expecting("WorkBench is showing").until(() -> myDesignSurfaceFixture.target().isShowing());
      Container workBench = SwingUtilities.getAncestorOfClass(WorkBench.class, myDesignSurfaceFixture.target());
      myPaletteFixture = NlPaletteFixture.create(robot(), workBench);
    }
    return myPaletteFixture;
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String group, @NotNull String item, int relativeX, int relativeY) {
    getPalette().dragComponent(group, item);

    DesignSurface<?> target = myDesignSurfaceFixture.target();
    SceneView sceneView = target.getFocusedSceneView();

    myDragAndDrop
      .drop(target, new Point(sceneView.getX() + relativeX, sceneView.getY() + relativeY));

    // Wait for the button to settle. It sometimes moves after being dropped onto the canvas.
    robot().waitForIdle();
    return this;
  }

  @NotNull
  public NlEditorFixture dragComponentToSurface(@NotNull String group, @NotNull String item) {
    DesignSurface<?> target = myDesignSurfaceFixture.target();
    SceneView sceneView = target.getFocusedSceneView();

    dragComponentToSurface(group, item, sceneView.getScaledContentSize().width / 2, sceneView.getScaledContentSize().height / 2);
    return this;
  }

  /**
   * Moves the mouse to the resize corner of the screen view, and presses the left mouse button.
   * That starts the canvas resize interaction.
   *
   * @see #resizeToAndroidSize(int, int)
   * @see #endResizeInteraction()
   */
  @NotNull
  public NlEditorFixture startResizeInteraction() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    SceneView screenView = surface.getFocusedSceneView();

    Dimension size = screenView.getScaledContentSize();
    robot().pressMouse(surface, new Point(screenView.getX() + size.width + 24, screenView.getY() + size.height + 24));
    return this;
  }

  /**
   * Moves the mouse to resize the screen view to correspond to a device of size {@code (width, height)}, expressed in dp
   *
   * @see #startResizeInteraction()
   * @see #endResizeInteraction()
   */
  @NotNull
  public NlEditorFixture resizeToAndroidSize(@AndroidDpCoordinate int width, @AndroidDpCoordinate int height) {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    SceneView screenView = surface.getFocusedSceneView();

    robot().moveMouse(surface, Coordinates.getSwingXDip(screenView, width), Coordinates.getSwingYDip(screenView, height));
    return this;
  }

  /**
   * Releases left mouse button to end resize interaction.
   *
   * @see #startResizeInteraction()
   * @see #resizeToAndroidSize(int, int)
   */
  @NotNull
  public NlEditorFixture endResizeInteraction() {
    robot().releaseMouse(MouseButton.LEFT_BUTTON);
    return this;
  }

  /**
   * Ensures only the design view is being displayed, and zooms to fit.
   * Only applicable if {@code target()} is a {@link NlDesignSurface}.
   */
  @NotNull
  public NlEditorFixture showOnlyDesignView() {
    getConfigToolbar().selectDesign();
    return this;
  }

  /**
   * Ensures only the blueprint view is being displayed, and zooms to fit.
   * Only applicable if {@code target()} is a {@link NlDesignSurface}.
   */
  @NotNull
  public NlEditorFixture showOnlyBlueprintView() {
    getConfigToolbar().selectBlueprint();
    return this;
  }

  @NotNull
  public NlEditorFixture mouseWheelZoomIn(int amount) {
    robot().click(myDesignSurfaceFixture.target());
    robot().pressModifiers(InputEvent.CTRL_MASK);
    robot().rotateMouseWheel(myDesignSurfaceFixture.target(), amount);
    robot().releaseModifiers(InputEvent.CTRL_MASK);
    return this;
  }

  @NotNull
  public NlEditorFixture mouseWheelScroll(int amount) {
    robot().click(myDesignSurfaceFixture.target());
    robot().rotateMouseWheel(myDesignSurfaceFixture.target(), amount);
    return this;
  }

  public void dragMouseFromCenterWithModifier(int dx, int dy, @NotNull MouseButton mouseButton, int modifiers) {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    robot().moveMouse(surface);
    robot().pressModifiers(modifiers);
    robot().pressMouse(mouseButton);
    robot().moveMouse(surface, surface.getWidth() / 2 + dx, surface.getHeight() / 2 + dy);
    robot().releaseMouseButtons();
    robot().releaseModifiers(modifiers);
  }

  public void dragMouseFromCenterWithKeyCode(int dx, int dy, @NotNull MouseButton mouseButton, int keyCode) {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    robot().moveMouse(surface);
    robot().pressKey(keyCode);
    robot().pressMouse(mouseButton);
    robot().moveMouse(surface, surface.getWidth() / 2 + dx, surface.getHeight() / 2 + dy);
    robot().releaseMouseButtons();
    robot().releaseKey(keyCode);
  }

  @NotNull
  public Point getAdaptiveIconTopLeftCorner() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();

    SceneView view = surface.getFocusedSceneView();
    Dimension contentDimension = view.getScaledContentSize();
    // The square icon is placed in the center of a portrait ImageView, shift the y-axis to make the position same as icon's.
    int iconY = view.getY() + (contentDimension.height - contentDimension.width) / 2;
    // Offset the point to make sure it is fully inside the icon
    return new Point(view.getX() + 2, iconY + 2);
  }

  @NotNull
  public String getAdaptiveIconPathDescription() {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    return surface.getConfiguration().getAdaptiveShape().getPathDescription();
  }

  /**
   * Returns an HEX string corresponding to the value of the color of the pixel at a given point in the design surface reference frame.
   */
  @NotNull
  public String getPixelColor(@NotNull Point p) {
    DesignSurface<?> surface = myDesignSurfaceFixture.target();
    Point centerLeftPoint = translate(surface, p.x, p.y);

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
  public List<NlComponentFixture> getAllComponents() {
    return myDesignSurfaceFixture.getAllComponents();
  }

  @NotNull
  public List<SceneViewFixture> getAllSceneViews() {
    return myDesignSurfaceFixture.getAllSceneViews();
  }

  @NotNull
  public Point getScrollPosition() {
    return myDesignSurfaceFixture.target().getScrollPosition();
  }

  @NotNull
  public IssuePanelFixture getIssuePanel() {
    return myDesignSurfaceFixture.getIssuePanelFixture();
  }

  public void enlargeBottomComponentSplitter() {
    target().setIssuePanelProportion(0.2f);
  }

  @NotNull
  public MorphDialogFixture findMorphDialog() {
    return new MorphDialogFixture(robot());
  }

  /**
   * Returns the popup menu item for the provided component in the component tree
   */
  @NotNull
  public JPopupMenuFixture getTreePopupMenuItemForComponent(@NotNull NlComponent component) {
    return getComponentTree().showPopupMenuAt(buildTreePathTo(component));
  }

  /**
   * Build the string representation of the path to the provided component in the component tree
   */
  @NotNull
  private static String buildTreePathTo(NlComponent current) {
    StringBuilder builder = new StringBuilder(current.getTagDeprecated().getName());
    while ((current = current.getParent()) != null) {
      builder.insert(0, current.getTagDeprecated().getName() + "/");
      current = current.getParent();
    }
    return builder.toString();
  }

  public void invokeContextMenuAction(@NotNull String actionLabel) {
    new JMenuItemFixture(robot(), GuiTests.waitUntilShowing(robot(), Matchers.byText(JMenuItem.class, actionLabel))).click();
  }

  public HostPanelFixture hostPanel() {
    return HostPanelFixture.Companion.create(robot());
  }

  public ComponentTreeFixture navComponentTree() {
    JTable table = GuiTests.waitUntilShowing(robot(), Matchers.byName(JTable.class, "navComponentTree"));
    return new ComponentTreeFixture(robot(), table);
  }
}
