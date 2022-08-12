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

import static org.fest.swing.timing.Pause.pause;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.util.ui.JBUI;
import java.awt.Point;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JMenuItem;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a view in the layout editor
 */
public class NlComponentFixture {
  private static final long TIMEOUT_FOR_WRITE_IN_SECONDS = 10;
  private static final int MINIMUM_ANCHOR_GAP = JBUI.scale(6) * 2; // Based on DrawAnchor.java
  private final Robot myRobot;
  private final NlComponent myComponent;
  private final DesignSurface<?> mySurface;
  private final ComponentDragAndDrop myDragAndDrop;
  private final SceneComponent mySceneComponent;

  public NlComponentFixture(@NotNull Robot robot,
                            @NotNull NlComponent component,
                            @NotNull DesignSurface<?> surface) {
    myRobot = robot;
    myComponent = component;
    mySceneComponent = surface.getScene().getSceneComponent(myComponent);
    mySurface = surface;
    myDragAndDrop = new ComponentDragAndDrop(myRobot);
  }

  @Nullable
  public SceneComponentFixture getSceneComponent() {
    return mySurface.getModels().stream()
      .map(model -> mySurface.getSceneManager(model).getScene())
      .map(scene -> new SceneFixture(myRobot, scene))
      .map(scene -> scene.findSceneComponentByNlComponent(myComponent))
      .filter(Objects::nonNull)
      .findFirst().orElse(null);
  }

  @NotNull
  public NlComponentFixture resizeBy(int widthBy, int heightBy) {
    Point point = getSceneComponent().getRightBottomPoint();
    myDragAndDrop.drag(mySurface, point);
    myDragAndDrop.drop(mySurface, new Point(((int)point.getX()) + widthBy, ((int)point.getY()) + heightBy));
    pause(SceneComponent.ANIMATION_DURATION);
    return this;
  }

  @NotNull
  public NlComponentFixture moveBy(int xBy, int yBy) {
    Point point = getSceneComponent().getMidPoint();
    myDragAndDrop.drag(mySurface, point);
    myDragAndDrop.drop(mySurface, new Point(((int)point.getX()) + xBy, ((int)point.getY()) + yBy));
    pause(SceneComponent.ANIMATION_DURATION);
    return this;
  }

  // Note that this op behaves nothing. It is for testing purpose.
  @NotNull
  public NlComponentFixture createConstraintFromBottomToLeftOf(@NotNull NlComponentFixture destination) {
    myDragAndDrop.drag(mySurface, getSceneComponent().getBottomCenterPoint());
    myDragAndDrop.drop(mySurface, destination.getSceneComponent().getLeftCenterPoint());

    // There is no possible connection here, so expect a popup, and just close it:
    //JPopupMenuFixture menu = getSceneComponent().waitForConnectToPopup();
    //menu.target().setVisible(false);
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToTopOf(@NotNull NlComponentFixture destination) {
    getSceneComponent().click();
    myDragAndDrop.drag(mySurface, getSceneComponent().getBottomCenterPoint());
    myDragAndDrop.drop(mySurface, destination.getSceneComponent().getTopCenterPoint());
    waitForWrite(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF, destination);
    getSceneComponent().waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromBottomToBottomOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentBottomCenterPoint = getSceneComponent().getParent().getBottomCenterPoint();
    Point bottomCenterPoint = getSceneComponent().getBottomCenterPoint();
    getSceneComponent().click();
    myDragAndDrop.drag(mySurface, bottomCenterPoint);
    SceneView sceneView = mySurface.getFocusedSceneView();
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentBottomCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, "parent");
    getSceneComponent().waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromTopToTopOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentTopCenterPoint = getSceneComponent().getParent().getTopCenterPoint();
    Point topCenterPoint = getSceneComponent().getTopCenterPoint();
    getSceneComponent().click();
    myDragAndDrop.drag(mySurface, topCenterPoint);
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentTopCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent");
    getSceneComponent().waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromLeftToLeftOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentLeftCenterPoint = getSceneComponent().getParent().getLeftCenterPoint();
    Point leftCenterPoint = getSceneComponent().getLeftCenterPoint();
    getSceneComponent().click();
    myDragAndDrop.drag(mySurface, leftCenterPoint);
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentLeftCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_START_TO_START_OF, "parent");
    getSceneComponent().waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createConstraintFromRightToRightOfLayout() {
    SceneComponent parent = mySceneComponent.getParent();
    if (parent == null) {
      throw new IllegalStateException("Root component create constraint to its parent");
    }
    Point parentRightCenterPoint = getSceneComponent().getParent().getRightCenterPoint();
    Point rightCenterPoint = getSceneComponent().getRightCenterPoint();
    getSceneComponent().click();
    myDragAndDrop.drag(mySurface, rightCenterPoint);
    // Drop the constraint beyond the limit of the scene view to ensure it connects to the parent layout
    // and not a component that would be sitting right on the edge
    myDragAndDrop.drop(mySurface, parentRightCenterPoint);
    waitForWrite(SdkConstants.ATTR_LAYOUT_END_TO_END_OF, "parent");
    getSceneComponent().waitForSceneComponentAnimation();
    return this;
  }

  @NotNull
  public NlComponentFixture createBaselineConstraintWith(@NotNull NlComponentFixture destination) {
    getSceneComponent().rightClick();
    JPopupMenuFixture popupMenuFixture = new JPopupMenuFixture(myRobot, myRobot.findActivePopupMenu());
    popupMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Show Baseline".equals(component.getText());
      }
    }).click();

    Point sourceBaseline = getSceneComponent().getTopCenterPoint();
    SceneView sceneView = mySurface.getFocusedSceneView();
    sourceBaseline.translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(myComponent)));
    myDragAndDrop.drag(mySurface, sourceBaseline);
    Point destinationBaseline = destination.getSceneComponent().getTopCenterPoint();
    destinationBaseline
      .translate(0, Coordinates.getSwingDimension(sceneView, NlComponentHelperKt.getBaseline(destination.getComponent())));
    myDragAndDrop.drop(mySurface, destinationBaseline);
    waitForWrite(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF, destination);
    getSceneComponent().waitForSceneComponentAnimation();
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


  private void waitForWrite(@NotNull String attributeName, @NotNull NlComponentFixture destination) {
    waitForWrite(attributeName, SdkConstants.NEW_ID_PREFIX + destination.getComponent().getId());
  }

  private void waitForWrite(@NotNull String attributeName, @NotNull String expectedValue) {
    Wait.seconds(TIMEOUT_FOR_WRITE_IN_SECONDS)
        .expecting(String.format("%1$s = %2$s", expectedValue, myComponent.getAttribute(SdkConstants.AUTO_URI, attributeName)))
        .until(() -> expectedValue.equals(myComponent.getAttribute(SdkConstants.AUTO_URI, attributeName)));
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
