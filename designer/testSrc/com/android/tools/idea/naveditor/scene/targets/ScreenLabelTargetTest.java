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

import com.android.tools.idea.naveditor.scene.draw.DrawScreenLabel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.sherpa.drawing.ColorSet;
import junit.framework.TestCase;

import java.awt.*;
import java.util.ArrayList;

import static org.mockito.Mockito.*;

public class ScreenLabelTargetTest extends TestCase {
  private static int X = 10;
  private static int Y = 5;
  private static String TEXT = "TEXT";
  private static Color FRAMES = Color.RED;
  private static Color SELECTEDFRAMES = Color.GREEN;
  private static Color HIGHLIGHTEDFRAMES = Color.BLUE;

  private ColorSet myColorSet;
  private SceneContext mySceneContext;
  private SceneComponent mySceneComponent;
  private NlComponent myNlComponent;
  private ScreenLabelTarget myScreenLabelTarget;

  private void setup() {
    myNlComponent = mock(NlComponent.class);
    when(myNlComponent.getId()).thenReturn(TEXT);

    myColorSet = mock(ColorSet.class);
    when(myColorSet.getFrames()).thenReturn(FRAMES);
    when(myColorSet.getSelectedFrames()).thenReturn(SELECTEDFRAMES);
    when(myColorSet.getHighlightedFrames()).thenReturn(HIGHLIGHTEDFRAMES);

    mySceneContext = mock(SceneContext.class);
    when(mySceneContext.getColorSet()).thenReturn(myColorSet);
    when(mySceneContext.getSwingX(anyFloat())).thenReturn(X);
    when(mySceneContext.getSwingY(anyFloat())).thenReturn(Y);
    when(mySceneContext.getScale()).thenReturn(1.0);

    mySceneComponent = mock(SceneComponent.class);
    when(mySceneComponent.isSelected()).thenReturn(false);
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.NORMAL);
    when(mySceneComponent.getNlComponent()).thenReturn(myNlComponent);

    myScreenLabelTarget = new ScreenLabelTarget(mySceneComponent);
  }

  public void testScreenLabelTarget() {
    setup();
    verifyDrawCommands(FRAMES, ScreenLabelTarget.FONT_SIZE);

    // hover over component
    when(mySceneComponent.getDrawState()).thenReturn(SceneComponent.DrawState.HOVER);
    verifyDrawCommands(HIGHLIGHTEDFRAMES, ScreenLabelTarget.FONT_SIZE);

    // select component
    when(mySceneComponent.isSelected()).thenReturn(true);
    verifyDrawCommands(SELECTEDFRAMES, ScreenLabelTarget.FONT_SIZE);

    // zoom out
    when(mySceneContext.getScale()).thenReturn(2.0);
    verifyDrawCommands(SELECTEDFRAMES, (int)(ScreenLabelTarget.FONT_SIZE * 2.0));

    // zoom in
    when(mySceneContext.getScale()).thenReturn(1.0);
    verifyDrawCommands(SELECTEDFRAMES, (ScreenLabelTarget.FONT_SIZE));
  }

  private void verifyDrawCommands(Color color, int fontSize) {
    verifyDrawCommands(
      new DrawScreenLabel(X, Y, color, new Font(ScreenLabelTarget.FONT_NAME, ScreenLabelTarget.FONT_STYLE, fontSize), TEXT));
  }

  private void verifyDrawCommands(DrawCommand drawCommand) {
    DisplayList displayList = new DisplayList();
    myScreenLabelTarget.render(displayList, mySceneContext);
    ArrayList<DrawCommand> list = displayList.getCommands();
    assertEquals(list.size(), 1);
    assertEquals(drawCommand.serialize(), list.get(0).serialize());
  }
}
