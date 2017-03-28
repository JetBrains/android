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
package com.android.tools.idea.uibuilder.handlers.preference;

import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public final class PreferenceCategoryDragHandlerTest {
  private NlGraphics myGraphics;

  @Before
  public void initGraphics() {
    myGraphics = Mockito.mock(NlGraphics.class);
  }

  @Test
  public void paint() {
    DragHandler handler = newPreferenceCategoryDragHandler(PreferenceScreenTestFactory.newPreferenceCategory(0, 162, 768, 65));

    handler.update(360, 180, 0);
    handler.paint(myGraphics);

    Rectangle bounds = new Rectangle(0, 162, 768, 67);

    Mockito.verify(myGraphics).useStyle(NlDrawingStyle.DROP_PREVIEW);
    Mockito.verify(myGraphics).drawBottom(bounds);

    Mockito.verify(myGraphics).useStyle(NlDrawingStyle.DROP_RECIPIENT);
    Mockito.verify(myGraphics).drawTop(bounds);
    Mockito.verify(myGraphics).drawLeft(bounds);
    Mockito.verify(myGraphics).drawRight(bounds);
  }

  @Test
  public void drawDropRecipientLines() {
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(PreferenceScreenTestFactory.newPreferenceCategory());

    handler.update(360, 350, 0);
    handler.drawDropRecipientLines(myGraphics);

    Rectangle bounds = new Rectangle(0, 332, 768, 379);

    Mockito.verify(myGraphics).drawTop(bounds);
    Mockito.verify(myGraphics).drawLeft(bounds);
    Mockito.verify(myGraphics).drawRight(bounds);
    Mockito.verify(myGraphics).drawBottom(bounds);
  }

  @Test
  public void drawDropZoneLinesPointerIsInSecondHalfOfFirstChild() {
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(PreferenceScreenTestFactory.newPreferenceCategory());

    handler.update(360, 480, 0);
    handler.drawDropZoneLines(myGraphics);

    List<NlComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(myGraphics).drawTop(preferences.get(0));
    Mockito.verify(myGraphics).drawTop(preferences.get(2));
  }

  @Test
  public void drawDropZoneLinesPointerIsInFirstHalfOfSecondChild() {
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(PreferenceScreenTestFactory.newPreferenceCategory());

    handler.update(360, 530, 0);
    handler.drawDropZoneLines(myGraphics);

    List<NlComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(myGraphics).drawTop(preferences.get(0));
    Mockito.verify(myGraphics).drawTop(preferences.get(2));
  }

  @NotNull
  private static PreferenceGroupDragHandler newPreferenceCategoryDragHandler(@NotNull NlComponent category) {
    ViewEditor editor = PreferenceScreenTestFactory.mockEditor();
    List<NlComponent> preferences = Collections.singletonList(Mockito.mock(NlComponent.class));

    return new PreferenceCategoryDragHandler(editor, new ViewGroupHandler(), category, preferences, DragType.MOVE);
  }
}
