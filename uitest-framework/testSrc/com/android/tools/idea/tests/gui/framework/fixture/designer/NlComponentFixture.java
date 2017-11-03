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

import com.android.SdkConstants;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JMenuItemFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

/**
 * Represents a view in the layout editor
 */
public class NlComponentFixture {
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
  private Point getMidPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getCenterX());
    int midY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getCenterY());
    return new Point(midX, midY);
  }

  /**
   * Returns the right bottom point in panel coordinates
   */
  @NotNull
  private Point getRightBottomPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int x = Coordinates.getSwingXDip(sceneView, mySceneComponent.getDrawX() + mySceneComponent.getDrawWidth());
    int y = Coordinates.getSwingYDip(sceneView, mySceneComponent.getDrawY() + mySceneComponent.getDrawHeight());
    return new Point(x, y);
  }

  /**
   * Returns the bottom center point in panel coordinates
   */
  @NotNull
  private Point getBottomCenterPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getCenterX());
    int bottomY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getDrawY() + mySceneComponent.getDrawHeight());
    return new Point(midX, bottomY);
  }

  /**
   * Returns the top center point in panel coordinates
   */
  @NotNull
  private Point getTopCenterPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int midX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getCenterX());
    int topY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getDrawY());
    return new Point(midX, topY);
  }

  /**
   * Returns the left center point in panel coordinates
   */
  @NotNull
  private Point getLeftCenterPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int leftX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getDrawX());
    int midY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getCenterY());
    return new Point(leftX, midY);
  }

  /**
   * Returns the right center point in panel coordinates
   */
  @NotNull
  private Point getRightCenterPoint() {
    SceneView sceneView = mySurface.getCurrentSceneView();
    int rightX = Coordinates.getSwingXDip(sceneView, mySceneComponent.getDrawX() + mySceneComponent.getDrawWidth());
    int midY = Coordinates.getSwingYDip(sceneView, mySceneComponent.getCenterY());
    return new Point(rightX, midY);
  }

  @NotNull
  public NlComponentFixture resizeBy(int widthBy, int heightBy) {
    Point point = getRightBottomPoint();
    myDragAndDrop.drag(mySurface, point);
    myDragAndDrop.drop(mySurface, new Point(((int)point.getX()) + widthBy, ((int)point.getY()) + heightBy));
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
    myDragAndDrop.drag(mySurface, getBottomCenterPoint());
    myDragAndDrop.drop(mySurface, destination.getLeftCenterPoint());
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToTopOf(@NotNull NlComponentFixture destination) {
    myDragAndDrop.drag(mySurface, getBottomCenterPoint());
    myDragAndDrop.drop(mySurface, destination.getTopCenterPoint());
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToBottomOfLayout() {
    Point bottomCenterPoint = getBottomCenterPoint();
    myDragAndDrop.drag(mySurface, bottomCenterPoint);
    SceneView sceneView = mySurface.getCurrentSceneView();
    myDragAndDrop.drop(mySurface, new Point(bottomCenterPoint.x, mySurface.getCurrentSceneView().getY() + sceneView.getSize().height));
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromTopToTopOfLayout() {
    Point topCenterPoint = getTopCenterPoint();
    myDragAndDrop.drag(mySurface, topCenterPoint);
    myDragAndDrop.drop(mySurface, new Point(topCenterPoint.x, mySurface.getCurrentSceneView().getY()));
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromLeftToLeftOfLayout() {
    Point leftCenterPoint = getLeftCenterPoint();
    myDragAndDrop.drag(mySurface, leftCenterPoint);
    myDragAndDrop.drop(mySurface, new Point(mySurface.getCurrentSceneView().getX(), leftCenterPoint.y));
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromRightToRightOfLayout() {
    Point rightCenterPoint = getRightCenterPoint();
    myDragAndDrop.drag(mySurface, rightCenterPoint);
    SceneView sceneView = mySurface.getCurrentSceneView();
    myDragAndDrop.drop(mySurface, new Point(sceneView.getX() + sceneView.getSize().width, rightCenterPoint.y));
    return this;
  }

  @NotNull
  public NlComponentFixture createBaselineConstraintWith(@NotNull NlComponentFixture destination) {
    String expectedTooltipText = "Edit Baseline";
    SceneView sceneView = mySurface.getCurrentSceneView();

    // Find the position of the baseline target icon and click on it
    SceneComponent sceneComponent = sceneView.getScene().getSceneComponent(myComponent);
    Target target = GuiQuery.getNonNull(() -> sceneComponent.getTargets().stream()
      .filter(t -> expectedTooltipText.equals(t.getToolTipText()))
      .findFirst().get());
    SceneContext context = SceneContext.get(sceneView);
    Point p = new Point(context.getSwingX(target.getCenterX()), context.getSwingY(target.getCenterY()));
    myComponentDriver.click(mySurface, p);

    Point sourceBaseline = getTopCenterPoint();
    sourceBaseline.translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(myComponent)) - 1);
    myDragAndDrop.drag(mySurface, sourceBaseline);
    Point destinationBaseline = destination.getTopCenterPoint();
    destinationBaseline
      .translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(destination.getComponent())) - 1);
    myDragAndDrop.drop(mySurface, destinationBaseline);
    return this;
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

  @NotNull
  public NlComponent getComponent() {
    return myComponent;
  }
}
