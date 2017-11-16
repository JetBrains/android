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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandle;
import com.android.tools.idea.naveditor.scene.draw.DrawActionHandleDrag;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.ColorSet;
import com.intellij.ui.JBColor;
import junit.framework.TestCase;

import java.awt.*;
import java.util.ArrayList;

import static org.mockito.Mockito.*;

public class ActionHandleTargetTest extends TestCase {
  private static final int X = 10;
  private static final int Y = 5;
  private static final int DRAGX = X + 20;
  private static final int DRAGY = Y - 10;
  private static final Color FRAMES = JBColor.RED;
  private static final Color SELECTEDFRAMES = JBColor.GREEN;
  private static final Color HIGHLIGHTEDFRAMES = JBColor.BLUE;
  private static final Color BACKGROUND = JBColor.WHITE;

  private SceneContext mySceneContext;
  private SceneComponent mySceneComponent;
  private ActionHandleTarget myActionHandleTarget;

  private void setup() {
    ColorSet colorSet = mock(ColorSet.class);
    when(colorSet.getFrames()).thenReturn(FRAMES);
    when(colorSet.getSelectedFrames()).thenReturn(SELECTEDFRAMES);
    when(colorSet.getHighlightedFrames()).thenReturn(HIGHLIGHTEDFRAMES);
    when(colorSet.getBackground()).thenReturn(BACKGROUND);

    mySceneContext = mock(SceneContext.class);
    when(mySceneContext.getColorSet()).thenReturn(colorSet);
    when(mySceneContext.getSwingX(anyInt())).thenReturn(X);
    when(mySceneContext.getSwingY(anyInt())).thenReturn(Y);
    when(mySceneContext.getSwingDimension(anyInt())).thenAnswer(i -> i.getArguments()[0]);

    mySceneComponent = mock(SceneComponent.class);
    when(mySceneComponent.getScene()).thenReturn(mock(Scene.class));
    when(mySceneComponent.isSelected()).thenReturn(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);

    myActionHandleTarget = new ActionHandleTarget(mySceneComponent);
  }

  public void testActionHandle() {
    setup();
    verifyDrawCommands(0, 0, FRAMES, 0);

    int smallDuration = ActionHandleTarget.MAX_DURATION
                        * (ActionHandleTarget.LARGE_RADIUS - ActionHandleTarget.SMALL_RADIUS) / ActionHandleTarget.SMALL_RADIUS;

    // hover over component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(0, ActionHandleTarget.SMALL_RADIUS, FRAMES, ActionHandleTarget.MAX_DURATION);

    // hover over target
    myActionHandleTarget.setMouseHovered(true);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, ActionHandleTarget.LARGE_RADIUS, FRAMES, smallDuration);

    // move away from target
    myActionHandleTarget.setMouseHovered(false);
    verifyDrawCommands(ActionHandleTarget.LARGE_RADIUS, ActionHandleTarget.SMALL_RADIUS, FRAMES, smallDuration);

    // move away from component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, 0, FRAMES, ActionHandleTarget.MAX_DURATION);

    // hover over component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(0, ActionHandleTarget.SMALL_RADIUS, FRAMES, ActionHandleTarget.MAX_DURATION);

    // select component
    when(mySceneComponent.isSelected()).thenReturn(true);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, ActionHandleTarget.SMALL_RADIUS, SELECTEDFRAMES, 0);

    // hover over target
    myActionHandleTarget.setMouseHovered(true);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, ActionHandleTarget.LARGE_RADIUS, SELECTEDFRAMES, smallDuration);

    // mouse down and drag
    myActionHandleTarget.mouseDown(X, Y);
    myActionHandleTarget.mouseDrag(DRAGX, DRAGY, null);
    verifyDrawCommands();

    // mouse release
    myActionHandleTarget.setMouseHovered(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    myActionHandleTarget.mouseRelease(DRAGX, DRAGY, null);
    verifyDrawCommands(ActionHandleTarget.LARGE_RADIUS, ActionHandleTarget.SMALL_RADIUS, SELECTEDFRAMES, smallDuration);

    // hover over target
    myActionHandleTarget.setMouseHovered(true);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, ActionHandleTarget.LARGE_RADIUS, SELECTEDFRAMES, smallDuration);

    // move away from component and target
    myActionHandleTarget.setMouseHovered(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    verifyDrawCommands(ActionHandleTarget.LARGE_RADIUS, ActionHandleTarget.SMALL_RADIUS, SELECTEDFRAMES, smallDuration);

    // deselect component
    when(mySceneComponent.isSelected()).thenReturn(false);
    verifyDrawCommands(ActionHandleTarget.SMALL_RADIUS, 0, FRAMES, ActionHandleTarget.MAX_DURATION);
  }

  private void verifyDrawCommands(int initialRadius, int finalRadius, Color borderColor, int duration) {
    verifyDrawCommands(new DrawActionHandle(X, Y, initialRadius, finalRadius, borderColor, duration));
  }

  private void verifyDrawCommands() {
    verifyDrawCommands(new DrawActionHandleDrag(X, Y, ActionHandleTarget.LARGE_RADIUS));
  }

  private void verifyDrawCommands(DrawCommand drawCommand) {
    DisplayList displayList = new DisplayList();
    myActionHandleTarget.render(displayList, mySceneContext);
    ArrayList<DrawCommand> list = displayList.getCommands();
    assertEquals(list.size(), 1);
    assertEquals(drawCommand.serialize(), list.get(0).serialize());
  }
}
