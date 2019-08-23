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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.swing.timing.Pause.pause;

import com.android.SdkConstants;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.ui.JBPopupMenu;
import java.awt.Point;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.fixture.JMenuItemFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a view in the layout editor
 */
public class NlComponentFixture {
  private static final long TIMEOUT_FOR_WRITE_IN_SECONDS = 10;
  private static final long TIMEOUT_FOR_SCENE_COMPONENT_AMIMATION_SECONDS = 5;
  private final Robot myRobot;
  private final NlComponent myComponent;
  private final DesignSurface mySurface;
  private final ComponentDragAndDrop myDragAndDrop;
  private final ComponentDriver<DesignSurface> myComponentDriver;
  private final SceneComponent mySceneComponent;

  public NlComponentFixture(@NotNull Robot robot,
                            @NotNull NlComponent component,
                            @NotNull DesignSurface surface) {
    myRobot = robot;
    myComponent = component;
    mySceneComponent = surface.getScene().getSceneComponent(myComponent);
    mySurface = surface;
    myDragAndDrop = new ComponentDragAndDrop(myRobot);
    myComponentDriver = new ComponentDriver<>(myRobot);
  }

  /**
   * Returns the center point in panel coordinates
   */
  @NotNull
  public Point getMidPoint() {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getCenterX());
    int midY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getCenterY());
    return convertToViewport(midX, midY);
  }

  /**
   * Returns the right bottom point in panel coordinates
   */
  @NotNull
  private Point getRightBottomPoint(@NotNull SceneComponent component) {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int x = Coordinates.getSwingXDip(sceneView, component.getDrawX() + component.getDrawWidth());
    int y = Coordinates.getSwingYDip(sceneView, component.getDrawY() + component.getDrawHeight());
    return convertToViewport(x, y);
  }

  /**
   * Returns the bottom center point in panel coordinates
   */
  @NotNull
  private Point getBottomCenterPoint(@NotNull SceneComponent component) {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, component.getCenterX());
    int bottomY = Coordinates.getSwingYDip(sceneView, component.getDrawY() + component.getDrawHeight());
    return convertToViewport(midX, bottomY);
  }

  /**
   * Returns the top center point in panel coordinates
   */
  @NotNull
  public Point getTopCenterPoint() {
    return getTopCenterPoint(mySceneComponent);
  }

  /**
   * Returns the top center point in panel coordinates
   */
  @NotNull
  public Point getTopCenterPoint(@NotNull SceneComponent component) {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, component.getCenterX());
    int topY = Coordinates.getSwingYDip(sceneView, component.getDrawY());
    return convertToViewport(midX, topY);
  }

  /**
   * Returns the left center point in panel coordinates
   */
  @NotNull
  public Point getLeftCenterPoint() {
    return getLeftCenterPoint(mySceneComponent);
  }

  /**
   * Returns the left center point in panel coordinates
   */
  @NotNull
  private Point getLeftCenterPoint(@NotNull SceneComponent component) {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int leftX = Coordinates.getSwingXDip(sceneView, component.getDrawX());
    int midY = Coordinates.getSwingYDip(sceneView, component.getCenterY());
    return convertToViewport(leftX, midY);
  }

  /**
   * Returns the right center point in panel coordinates
   */
  @NotNull
  private Point getRightCenterPoint(@NotNull SceneComponent component) {
    SceneView sceneView = mySurface.getFocusedSceneView();
    int rightX = Coordinates.getSwingXDip(sceneView, component.getDrawX() + component.getDrawWidth());
    int midY = Coordinates.getSwingYDip(sceneView, component.getCenterY());
    return convertToViewport(rightX, midY);
  }

  public SceneComponent getSceneComponent() {
    return mySceneComponent;
  }

  @NotNull
  public NlComponentFixture resizeBy(int widthBy, int heightBy) {
    Point point = getRightBottomPoint(mySceneComponent);
    myDragAndDrop.drag(mySurface, point);
    myDragAndDrop.drop(mySurface, new Point(((int)point.getX()) + widthBy, ((int)point.getY()) + heightBy));
    pause(SceneComponent.ANIMATION_DURATION);
    return this;
  }

  @NotNull
  public NlComponentFixture moveBy(int xBy, int yBy) {
    Point point = getMidPoint();
    myDragAndDrop.drag(mySurface, point);
    myDragAndDrop.drop(mySurface, new Point(((int)point.getX()) + xBy, ((int)point.getY()) + yBy));
    pause(SceneComponent.ANIMATION_DURATION);
    return this;
  }

  @NotNull
  public int getWidth() {
    return mySceneComponent.getDrawWidth();
  }

  @NotNull
  public int getHeight() {
    return mySceneComponent.getDrawHeight();
  }

  // Note that this op behaves nothing. It is for testing purpose.
  @NotNull
  public NlComponentFixture createConstraintFromBottomToLeftOf(@NotNull NlComponentFixture destination) {
    myDragAndDrop.drag(mySurface, getBottomCenterPoint(mySceneComponent));
    myDragAndDrop.drop(mySurface, destination.getLeftCenterPoint(destination.mySceneComponent));

    // There is no possible connection here, so expect a popup, and just close it:
    JPopupMenuFixture menu = waitForPopup();
    menu.target().setVisible(false);
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToTopOf(@NotNull NlComponentFixture destination) {
    myDragAndDrop.drag(mySurface, getBottomCenterPoint(mySceneComponent));
    myDragAndDrop.drop(mySurface, destination.getTopCenterPoint(destination.mySceneComponent));
    waitForWrite(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, destination);
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToBottomOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentBottomCenterPoint = getBottomCenterPoint(parent);
    Point bottomCenterPoint = getBottomCenterPoint(mySceneComponent);
    myDragAndDrop.drag(mySurface, bottomCenterPoint);
    SceneView sceneView = mySurface.getFocusedSceneView();
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentBottomCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, "parent");
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromTopToTopOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentTopCenterPoint = getTopCenterPoint(parent);
    Point topCenterPoint = getTopCenterPoint(mySceneComponent);
    myDragAndDrop.drag(mySurface, topCenterPoint);
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentTopCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent");
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromLeftToLeftOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentLeftCenterPoint = getLeftCenterPoint(parent);
    Point leftCenterPoint = getLeftCenterPoint(mySceneComponent);
    myDragAndDrop.drag(mySurface, leftCenterPoint);
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentLeftCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent");
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromRightToRightOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentRightCenterPoint = getRightCenterPoint(parent);
    Point rightCenterPoint = getRightCenterPoint(mySceneComponent);
    myDragAndDrop.drag(mySurface, rightCenterPoint);
    SceneView sceneView = mySurface.getFocusedSceneView();
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentRightCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent");
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createBaselineConstraintWith(@NotNull NlComponentFixture destination) {
    SceneView sceneView = mySurface.getFocusedSceneView();

    // Find the position of the baseline target icon and click on it
    SceneComponent sceneComponent = sceneView.getScene().getSceneComponent(myComponent);
    myComponentDriver.click(mySurface, new Point(sceneComponent.getCenterX(), sceneComponent.getCenterY()));
    myComponentDriver.rightClick(mySurface);

    JPopupMenuFixture popupMenuFixture = new JPopupMenuFixture(myRobot, myRobot.findActivePopupMenu());
    popupMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Show Baseline".equals(component.getText());
      }
    }).click();

    Point sourceBaseline = getTopCenterPoint(mySceneComponent);
    sourceBaseline.translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(myComponent)));
    myDragAndDrop.drag(mySurface, sourceBaseline);
    Point destinationBaseline = destination.getTopCenterPoint(destination.mySceneComponent);
    destinationBaseline
      .translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(destination.getComponent())));
    myDragAndDrop.drop(mySurface, destinationBaseline);
    waitForWrite(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, destination);
    waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public ComponentAssistantFixture openComponentAssistant() {
    Point point = getMidPoint();
    myComponentDriver.click(mySurface, point);
    myRobot.click(mySurface, point, MouseButton.RIGHT_BUTTON, 1);

    JPopupMenuFixture popupMenuFixture = new JPopupMenuFixture(myRobot, myRobot.findActivePopupMenu());
    popupMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Set Sample Data".equals(component.getText());
      }
    }).click();

    return new ComponentAssistantFixture(myRobot, waitUntilFound(myRobot, null,
                                                                 Matchers.byName(JComponent.class,"Component Assistant")));
  }

  @NotNull
  public String getTextAttribute() {
    return myComponent.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
  }

  @NotNull
  public Object getViewObject() {
    return NlComponentHelperKt.getViewInfo(myComponent).getViewObject();
  }

  @NotNull
  public String getText() {
    try {
      return (String)getViewObject().getClass().getMethod("getText").invoke(getViewObject());
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Click in the middle of the view (typically selects it)
   */
  @NotNull
  public NlComponentFixture click() {
    myComponentDriver.click(mySurface, getMidPoint());
    return this;
  }

  public void doubleClick() {
    myRobot.click(mySurface, getMidPoint(), MouseButton.LEFT_BUTTON, 2);
  }

  /** Right clicks s in the middle of the view */
  public void rightClick() {
    // Can't use ComponentDriver -- need to both set button and where
    myRobot.click(mySurface, getMidPoint(), MouseButton.RIGHT_BUTTON, 1);
  }

  public void invokeContextMenuAction(@NotNull String actionLabel) {
    rightClick();
    new JMenuItemFixture(myRobot, GuiTests.waitUntilShowing(myRobot, Matchers.byText(JMenuItem.class, actionLabel))).click();
  }

  /** Converts from scrollable area coordinate system to viewpoint coordinate system */
  private Point convertToViewport(@SwingCoordinate int x, @SwingCoordinate int y) {
    return SwingUtilities.convertPoint(mySurface.getLayeredPane(), x, y, mySurface.getScrollPane().getViewport());
  }

  private void waitForWrite(@NotNull String attributeName, @NotNull NlComponentFixture destination) {
    waitForWrite(attributeName, SdkConstants.NEW_ID_PREFIX + destination.getComponent().getId());
  }

  private void waitForWrite(@NotNull String attributeName, @NotNull String expectedValue) {
    Wait.seconds(TIMEOUT_FOR_WRITE_IN_SECONDS)
        .expecting(String.format("%1$s = %2$s", expectedValue, myComponent.getAttribute(SdkConstants.AUTO_URI, attributeName)))
        .until(() -> expectedValue.equals(myComponent.getAttribute(SdkConstants.AUTO_URI, attributeName)));
  }

  private void waitForSceneComponentAnimation() {
    Wait.seconds(TIMEOUT_FOR_SCENE_COMPONENT_AMIMATION_SECONDS)
      .expecting("Expect SceneComponent Animation Finish")
      .until(() -> !mySceneComponent.isAnimating());
  }

  @NotNull
  private JPopupMenuFixture waitForPopup() {
    JBPopupMenu menu = waitUntilFound(myRobot, null, new GenericTypeMatcher<JBPopupMenu>(JBPopupMenu.class) {
      @Override
      protected boolean isMatching(@NotNull JBPopupMenu menu) {
        return "Connect to:".equals(menu.getLabel());
      }
    });
    return new JPopupMenuFixture(myRobot, menu);
  }

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }

  @NotNull
  public List<NlComponentFixture> getChildren() {
    if (myComponent.getChildCount() == 0) {
      return Collections.emptyList();
    }
    return myComponent.getChildren()
      .stream()
      .map(component -> new NlComponentFixture(myRobot, component, mySurface))
      .collect(Collectors.toList());
  }
}
