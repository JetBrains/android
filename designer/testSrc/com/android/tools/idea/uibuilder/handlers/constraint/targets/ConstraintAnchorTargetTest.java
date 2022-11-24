/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.targets;

import com.android.SdkConstants;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.ScenePicker;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.scene.target.AnchorTarget;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.scene.SceneTest;
import com.android.tools.idea.uibuilder.scene.decorator.DecoratorUtilities;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import static com.android.SdkConstants.BUTTON;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;

public class ConstraintAnchorTargetTest extends SceneTest {

  public void testCenteringComponentWithSibling() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease("button1", AnchorTarget.Type.LEFT);
    myInteraction.mouseDown("button2", AnchorTarget.Type.RIGHT);
    myInteraction.mouseRelease("button1", AnchorTarget.Type.RIGHT);
    myScreen.get("@id/button2").expectXml("<Button\n" +
                                          "        android:id=\"@id/button2\"\n" +
                                          "        app:layout_constraintEnd_toEndOf=\"@+id/button1\"\n" +
                                          "        app:layout_constraintStart_toStartOf=\"@+id/button1\"\n" +
                                          "        tools:layout_editor_absoluteY=\"15dp\" />");
  }

  public void testConnectToParentWhenDraggingToExpandingArea() {
    SceneComponent inner = myScene.getSceneComponent("inner");
    SceneComponent textView = myScene.getSceneComponent("textView");

    myInteraction.select("textView", true);

    // Drag left anchor of textView to top-left sides of nested constraint layout. It shouldn't connect to any edge of parent.
    myInteraction.mouseDown("textView", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease(inner.getDrawX() - 50, inner.getDrawY() - 50);
    assertNull(textView.getNlComponent().getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF));

    // Drag left anchor of textView to left side of nested constraint layout.
    myInteraction.mouseDown("textView", AnchorTarget.Type.LEFT);
    myInteraction.mouseRelease(inner.getDrawX() - 50, inner.getCenterY());
    assertEquals(SdkConstants.ATTR_PARENT,
                 textView.getNlComponent().getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_START_TO_START_OF));

    // Drag top anchor of textView to bottom side of nested constraint layout.
    myInteraction.mouseDown("textView", AnchorTarget.Type.TOP);
    myInteraction.mouseRelease(inner.getDrawX(), inner.getCenterY() + 50);
    assertEquals(SdkConstants.ATTR_PARENT,
                 textView.getNlComponent().getAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF));
  }

  public void testRenderDuringDragging() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseDrag(90, 90);

    // The connection between mouse and AnchorTarget should be inside DisplayList.
    Optional<DrawCommand> command =
      myInteraction.getDisplayList().getCommands().stream().filter(it -> it instanceof DisplayList.Connection).findAny();
    assertTrue(command.isPresent());
  }

  public void testCancelWhenDragging() {
    AnchorTarget target = AnchorTarget.findAnchorTarget(myScene.getSceneComponent("button1"), AnchorTarget.Type.RIGHT);
    final float endX = target.getCenterX();
    final float endY = target.getCenterY();

    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    // Drag connection to button1 right anchor.
    myInteraction.mouseDrag(endX, endY);
    ConstraintAnchorTarget leftAnchor =
      (ConstraintAnchorTarget)AnchorTarget.findAnchorTarget(myScene.getSceneComponent("button2"), AnchorTarget.Type.LEFT);

    assertTrue(leftAnchor.isConnected());

    myInteraction.mouseCancel(endX, endY);

    assertFalse(leftAnchor.isConnected());
    // No change in actual xml.
    myScreen.get("@id/button2").expectXml("<Button\n" + "    android:id=\"@id/button2\"/>");
  }

  public void testCancelAnchorWhenCreating() {
    myInteraction.select("button2", true);
    myInteraction.mouseDown("button2", AnchorTarget.Type.LEFT);
    myInteraction.mouseCancel(500, 500);

    myScreen.get("@id/button2").expectXml("<Button\n" +
                                          "    android:id=\"@id/button2\"/>");
  }

  public void testBaselineTargetPosition() {
    SceneComponent button2 = myScene.getSceneComponent("button2");
    AnchorTarget baselineTarget = AnchorTarget.findAnchorTarget(button2, AnchorTarget.Type.BASELINE);

    // Unit of SceneComponent is Integer and unit of Target is double. They may have up to 0.5d differences.
    assertEquals(button2.getCenterX(), baselineTarget.getCenterX(), 0.5);
    assertEquals(button2.getDrawY() + button2.getBaseline(), baselineTarget.getCenterY(), 0.5);
  }

  public void testEdgeAnchorSize() {
    SceneComponent inner = myScene.getSceneComponent("inner");
    SceneView sceneView = myScene.getSceneManager().getSceneView();

    int swingX1 = Coordinates.getSwingXDip(sceneView, inner.getDrawX());
    int swingY1 = Coordinates.getSwingXDip(sceneView, inner.getDrawY());
    int swingX2 = Coordinates.getSwingXDip(sceneView, inner.getDrawX() + inner.getDrawWidth());
    int swingY2 = Coordinates.getSwingXDip(sceneView, inner.getDrawY() + inner.getDrawHeight());

    // edge anchor can only be hit when dragging normal anchor.
    // Simulate dragging left anchor of textView to test sizes of horizontal edge anchors.
    myInteraction.select("textView", true);
    myInteraction.mouseDown("textView", AnchorTarget.Type.LEFT);
    myInteraction.mouseDrag(110, 110);

    {
      AnchorTarget left = createEdgeAnchorTarget(inner, AnchorTarget.Type.LEFT);
      Point[] leftHitPoints = {
        new Point(swingX1, swingY1 + 1),
        new Point(swingX1, swingY2 - 1),
      };
      Point[] leftNonHitPoint = {
        new Point(swingX1, swingY1 - 1),
        new Point(swingX1, swingY2 + 1),
      };
      testAnchorSize(left, leftHitPoints, leftNonHitPoint);
    }

    {
      AnchorTarget right = createEdgeAnchorTarget(inner, AnchorTarget.Type.RIGHT);
      Point[] rightHitPoints = {
        new Point(swingX2, swingY1 + 1),
        new Point(swingX2, swingY2 - 1),
      };
      Point[] rightNonHitPoint = {
        new Point(swingX2, swingY1 - 1),
        new Point(swingX2, swingY2 + 1),
      };
      testAnchorSize(right, rightHitPoints, rightNonHitPoint);
    }

    // Now simulate dragging top anchor of textView to test sizes of vertical edge anchors.
    myInteraction.mouseRelease(110, 110);
    myInteraction.mouseDown("textView", AnchorTarget.Type.TOP);
    myInteraction.mouseDrag(110, 110);

    {
      AnchorTarget top = createEdgeAnchorTarget(inner, AnchorTarget.Type.TOP);
      Point[] topHitPoints = {
        new Point(swingX1 + 1, swingY1),
        new Point(swingX2 - 1, swingY1),
      };
      Point[] topNonHitPoint = {
        new Point(swingX1 - 1, swingY1),
        new Point(swingX2 + 1, swingY1),
      };
      testAnchorSize(top, topHitPoints, topNonHitPoint);
    }

    {
      AnchorTarget bottom = createEdgeAnchorTarget(inner, AnchorTarget.Type.BOTTOM);
      Point[] bottomHitPoints = {
        new Point(swingX1 + 1, swingY2),
        new Point(swingX2 - 1, swingY2),
      };
      Point[] bottomNonHitPoint = {
        new Point(swingX1 - 1, swingY2),
        new Point(swingX2 + 1, swingY2),
      };
      testAnchorSize(bottom, bottomHitPoints, bottomNonHitPoint);
    }
  }

  private AnchorTarget createEdgeAnchorTarget(@NotNull SceneComponent component, AnchorTarget.Type type) {
    ConstraintAnchorTarget target = new ConstraintAnchorTarget(type, true);
    target.setComponent(component);
    target.layout(SceneContext.get(mySceneManager.getSceneView()),
                  component.getDrawX(),
                  component.getDrawY(),
                  component.getDrawX() + component.getDrawWidth(),
                  component.getDrawY() + component.getDrawHeight());
    return target;
  }

  private void testAnchorSize(AnchorTarget anchorTarget, Point[] hitPoints, Point[] nonHitPoints) {
    ScenePicker picker = new ScenePicker();
    ScenePicker.HitElementListener hitListener = Mockito.mock(ScenePicker.HitElementListener.class);
    picker.setSelectListener(hitListener);

    anchorTarget.addHit(SceneContext.get(myScene.getSceneManager().getSceneView()), picker, 0);

    for (Point p : nonHitPoints) {
      picker.find(p.x, p.y);
      Mockito.verify(hitListener, Mockito.times(0)).over(anchorTarget, 0d);
    }

    int hitCount = 0;
    for (Point p : hitPoints) {
      hitCount++;
      picker.find(p.x, p.y);
      Mockito.verify(hitListener, Mockito.times(hitCount)).over(anchorTarget, 0d);
    }
  }

  public void testHoverOnAnchor() {
    myInteraction.select("inner", true);
    SceneComponent inner = myScene.getSceneComponent("inner");

    AnchorTarget leftAnchor = inner.getTargets().stream()
      .filter(t -> (t instanceof AnchorTarget))
      .map(t -> ((AnchorTarget) t))
      .filter(t -> t.getType() == AnchorTarget.Type.LEFT && !t.isEdge())
      .toArray(AnchorTarget[]::new)[0];

    // Try to hover on Anchor
    myScene.mouseHover(SceneContext.get(mySceneManager.getSceneView()), (int) leftAnchor.getCenterX(), (int) leftAnchor.getCenterY(), 0);
    assertTrue(leftAnchor.isMouseHovered());

    // Move mouse out to SceneView. Should not have any hovered Target.
    myScene.mouseHover(SceneContext.get(mySceneManager.getSceneView()), -2, -2, 0);
    myScene.getSceneComponents().stream()
      .flatMap(component -> component.getTargets().stream())
      .forEach(target -> assertFalse(target.isMouseHovered()));
  }

  public void testCannotHoverEdgeAnchor() {
    myInteraction.select("inner", true);
    SceneComponent inner = myScene.getSceneComponent("inner");

    AnchorTarget leftEdgeAnchor = inner.getTargets().stream()
      .filter(t -> (t instanceof AnchorTarget))
      .map(t -> ((AnchorTarget) t))
      .filter(t -> t.getType() == AnchorTarget.Type.LEFT && t.isEdge())
      .toArray(AnchorTarget[]::new)[0];

    // Try to hover on edge
    myScene.mouseHover(SceneContext.get(mySceneManager.getSceneView()), inner.getDrawX(), inner.getDrawY() + 5, 0);
    assertFalse(leftEdgeAnchor.isMouseHovered());
    myScene.mouseHover(SceneContext.get(mySceneManager.getSceneView()), inner.getDrawX(), inner.getDrawY() + inner.getDrawHeight() - 5, 0);
    assertFalse(leftEdgeAnchor.isMouseHovered());
  }

  public void testDisableIllegalDestinationAnchor() {
    SceneComponent root = myScene.getSceneComponent("root");
    SceneComponent button1 = myScene.getSceneComponent("button1");
    SceneComponent button2 = myScene.getSceneComponent("button2");
    SceneComponent inner = myScene.getSceneComponent("inner");
    SceneComponent textView = myScene.getSceneComponent("textView");

    DisplayList displayList = new DisplayList();


    // Step1: testing dragging anchor of textView
    myInteraction.select("textView", true);
    myInteraction.mouseDown("textView", AnchorTarget.Type.LEFT);
    myInteraction.mouseDrag(90, 90);

    // root, button1, and button2 don't render anything.
    renderAnchorTargetsToDisplayList(root, displayList);
    renderAnchorTargetsToDisplayList(button1, displayList);
    renderAnchorTargetsToDisplayList(button2, displayList);
    assertEmpty(displayList.getCommands());

    // we don't render edge Anchors.
    renderAnchorTargetsToDisplayList(inner, displayList);
    assertEmpty(displayList.getCommands());
    myInteraction.mouseRelease(90, 90);

    // Step2: test dragging anchor of button1
    myInteraction.select("button1", true);
    myInteraction.mouseDown("button1", AnchorTarget.Type.LEFT);
    myInteraction.mouseDrag(90, 90);

    // TextView render nothing because button1 cannot create constraint with it.
    renderAnchorTargetsToDisplayList(textView, displayList);
    assertEmpty(displayList.getCommands());

    // we don't render edge Anchors.
    renderAnchorTargetsToDisplayList(root, displayList);
    assertEmpty(displayList.getCommands());

    // button2 render 2 Anchors: left and right
    renderAnchorTargetsToDisplayList(button2, displayList);
    assertSize(2, displayList.getCommands());
    displayList.clear();

    // inner constraint layout render 2 circle anchors because it is sibling of button1. button1 can create constraint with it.
    renderAnchorTargetsToDisplayList(inner, displayList);
    assertSize(2, displayList.getCommands());
    displayList.clear();
    myInteraction.mouseRelease(90, 90);

    // Step3: test dragging baseline Anchor of button1
    myInteraction.select("button1", true);
    // toggle baseline.
    myInteraction.performViewAction(button1, target -> target instanceof BaseLineToggleViewAction);
    myInteraction.mouseDown("button1", AnchorTarget.Type.BASELINE);
    myInteraction.mouseDrag(90, 90);

    // textView doesn't render any Anchor.
    renderAnchorTargetsToDisplayList(textView, displayList);
    assertEmpty(displayList.getCommands());

    // button2 render one Anchor, which is Baseline Anchor
    renderAnchorTargetsToDisplayList(button2, displayList);
    assertSize(1, displayList.getCommands());

    myInteraction.mouseRelease(90, 90);
  }

  public void testClickOnConnectedAnchor() {
    myInteraction.select("button3", true);
    myInteraction.mouseDown("button3", AnchorTarget.Type.TOP);
    ConstraintAnchorTarget target =
      (ConstraintAnchorTarget)AnchorTarget.findAnchorTarget(myScene.getSceneComponent("button3"), AnchorTarget.Type.TOP);

    assertTrue(target.isConnected());
    myInteraction.mouseRelease("button3", AnchorTarget.Type.TOP);
    assertTrue(target.isConnected());

    myInteraction.setModifiersEx(AdtUiUtils.getActionMask());
    myInteraction.mouseDown("button3", AnchorTarget.Type.TOP);
    myInteraction.mouseRelease("button3", AnchorTarget.Type.TOP);
    assertFalse(target.isConnected());
  }

  private void renderAnchorTargetsToDisplayList(@NotNull SceneComponent component, @NotNull DisplayList displayList) {
    SceneContext context = SceneContext.get(mySceneManager.getSceneView());
    component.getTargets()
      .stream()
      .filter(it -> it instanceof AnchorTarget)
      .forEach(it -> it.render(displayList, context));
  }

  public void testCannotDragEdgeConstraint() {
    myInteraction.select("textView", true);
    myInteraction.mouseDown("inner", AnchorTarget.Type.TOP);
    myInteraction.mouseDrag(90, 90);

    SceneComponent inner = myScene.getSceneComponent("inner");
    AnchorTarget topTarget = AnchorTarget.findAnchorTarget(inner, AnchorTarget.Type.TOP);

    assertNotNull(topTarget);
    assertNotSame(topTarget, myScene.getInteractingTarget());
  }

  public void testTryingToConnectWithNullId() {
    AnchorTarget target = AnchorTarget.findAnchorTarget(myScene.getSceneComponent("button2"), AnchorTarget.Type.TOP);
    float targetX = target.getCenterX();
    float targetY = target.getCenterY();

    myInteraction.select("button2", true);
    // Button2 has a connection from a component with a null id.
    myInteraction.mouseDown("button2", AnchorTarget.Type.TOP);
    myInteraction.mouseDrag(targetX, targetY + 1);

    // Should be able to connect to button 1.
    assertNotNull(DecoratorUtilities.getTryingToConnectState(myScene.getSceneComponent("button1").getNlComponent()));
  }

  public void testTooltipWithDeleteHint() {
    SceneComponent component = myScene.getSceneComponent("button3");
    component.getTargets().stream().filter(e -> e instanceof AnchorTarget).forEach(target -> {
      assertInstanceOf(target, ConstraintAnchorTarget.class);

      ConstraintAnchorTarget constraintTarget = (ConstraintAnchorTarget) target;
      if (constraintTarget.isConnected()) {
        String toolTip = constraintTarget.getToolTipText();

        // Should match 'Delete ....(ctrl+Click)' or 'Delete ...(unicodeChar+Click)'
        assertTrue(toolTip.matches("Delete.+[(](\\w+|.)[+]Click[)]"));
      }
    });

  }

  public void testCannotBeClickedByRightClickEvent() {
    SceneComponent inner = myScene.getSceneComponent("inner");
    ScenePicker picker = new ScenePicker();
    ScenePicker.HitElementListener listener = Mockito.mock(ScenePicker.HitElementListener.class);
    picker.setSelectListener(listener);

    ConstraintAnchorTarget topEdge = new ConstraintAnchorTarget(AnchorTarget.Type.TOP, true);
    topEdge.addHit(myScreen.getScreen().getContext(), picker, InputEvent.BUTTON3_DOWN_MASK);
    picker.find(inner.getCenterX(), inner.getDrawY() - 200);
    Mockito.verify(listener, Mockito.never()).over(ArgumentMatchers.eq(topEdge), ArgumentMatchers.anyDouble());

    ConstraintAnchorTarget leftEdge = new ConstraintAnchorTarget(AnchorTarget.Type.LEFT, true);
    leftEdge.addHit(myScreen.getScreen().getContext(), picker, InputEvent.BUTTON3_DOWN_MASK);
    picker.find(inner.getDrawX() - 200, inner.getCenterY());
    Mockito.verify(listener, Mockito.never()).over(ArgumentMatchers.eq(leftEdge), ArgumentMatchers.anyDouble());
  }

  @Override
  public ModelBuilder createModel() {
    return model("model.xml", component(CONSTRAINT_LAYOUT.defaultName())
      .id("@+id/root")
      .withBounds(0, 0, 1000, 1000)
      .children(component(BUTTON)
                  .id("@+id/button1")
                  .withBounds(10, 10, 10, 10),
                component(BUTTON)
                  .id("@id/button2")
                  .withBounds(10, 30, 10, 10),
                component(BUTTON)
                  .id("@+id/button3")
                  .withBounds(30, 40, 10, 10)
                  .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "@id/root")
                  .withAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_MARGIN_TOP, "20dp"),
                component(BUTTON)
                  // Button with no id defined.
                  .withBounds(10, 40, 10, 10)
                  .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF, "@id/button2"),
                 component(CONSTRAINT_LAYOUT.defaultName())
                  .id("@+id/inner")
                  .withBounds(200, 200, 200, 200)
                  .width("100dp")
                  .height("100dp")
                  .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "100dp")
                  .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "100dp")
                  .children(
                    component(TEXT_VIEW)
                      .id("@+id/textView")
                      .withBounds(300, 300, 100, 50)
                      .width("50dp")
                      .height("25dp")
                      .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, "50dp")
                      .withAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, "50dp")
      ))
    );
  }
}
