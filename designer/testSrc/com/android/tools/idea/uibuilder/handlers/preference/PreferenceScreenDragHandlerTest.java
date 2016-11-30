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

import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.handlers.HandlerTestFactory;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;
import static com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN;
import static org.junit.Assert.assertEquals;

public final class PreferenceScreenDragHandlerTest {
  @Test
  public void update() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());

    handler.update(360, 690, 0);
    assertEquals(PREFERENCE_CATEGORY, handler.myGroup.getTagName());

    handler.update(360, 711, 0);
    assertEquals(PREFERENCE_SCREEN, handler.myGroup.getTagName());

    handler.update(360, 712, 0);
    assertEquals(PREFERENCE_SCREEN, handler.myGroup.getTagName());

    handler.update(360, 740, 0);
    assertEquals(PREFERENCE_SCREEN, handler.myGroup.getTagName());
  }

  @Test
  public void updateAdjacentCategories() {
    NlComponent category1 = PreferenceScreenTestFactory.newPreferenceCategory(0, 266, 768, 65);
    category1.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 333, 768, 102));

    NlComponent category2 = PreferenceScreenTestFactory.newPreferenceCategory(0, 437, 768, 65);
    category2.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 504, 768, 102));

    NlComponent screen = newPreferenceScreen(0, 162, 768, 548);

    screen.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 162, 768, 102));
    screen.addChild(category1);
    screen.addChild(category2);
    screen.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 608, 768, 102));

    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(screen);

    handler.update(360, 410, 0);
    assertEquals(category1, handler.myGroup);

    handler.update(360, 460, 0);
    assertEquals(category2, handler.myGroup);
  }

  @Test
  public void drawDropPreviewLine() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(360, 690, 0);
    handler.drawDropPreviewLine(graphics);

    handler.update(360, 740, 0);
    handler.drawDropPreviewLine(graphics);

    Mockito.verify(graphics).drawBottom(new Rectangle(0, 607, 768, 104));
    Mockito.verify(graphics).drawTop(new Rectangle(0, 711, 768, 104));
  }

  @Test
  public void drawDropRecipientLines() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(360, 502, 0);
    handler.drawDropRecipientLines(graphics);

    Rectangle bounds = new Rectangle(0, 332, 768, 379);

    Mockito.verify(graphics).drawTop(bounds);
    Mockito.verify(graphics).drawLeft(bounds);
    Mockito.verify(graphics).drawRight(bounds);
    Mockito.verify(graphics).drawBottom(bounds);
  }

  @Test
  public void drawDropZoneLinesPointerIsBetweenFirstAndSecondChildren() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(360, 502, 0);
    handler.drawDropZoneLines(graphics);

    List<NlComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(graphics).drawTop(preferences.get(0));
    Mockito.verify(graphics).drawTop(preferences.get(2));
  }

  @Test
  public void drawDropZoneLinesPointerIsBetweenSecondAndThirdChildren() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(360, 606, 0);
    handler.drawDropZoneLines(graphics);

    List<NlComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(graphics).drawTop(preferences.get(0));
    Mockito.verify(graphics).drawTop(preferences.get(1));
  }

  @NotNull
  private static NlComponent newPreferenceScreen() {
    NlComponent screen = newPreferenceScreen(0, 162, 768, 755);

    screen.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 162, 768, 168));
    screen.addChild(PreferenceScreenTestFactory.newPreferenceCategory());
    screen.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 711, 768, 102));
    screen.addChild(PreferenceScreenTestFactory.newCheckBoxPreference(0, 815, 768, 102));

    return screen;
  }

  @NotNull
  @SuppressWarnings("SameParameterValue")
  private static NlComponent newPreferenceScreen(int x, int y, int width, int height) {
    NlComponent screen = HandlerTestFactory.newNlComponent(PREFERENCE_SCREEN);
    screen.setBounds(x, y, width, height);

    return screen;
  }

  @NotNull
  private static PreferenceGroupDragHandler newPreferenceScreenDragHandler(@NotNull NlComponent group) {
    ViewEditor editor = PreferenceScreenTestFactory.mockEditor();
    List<NlComponent> preferences = Collections.singletonList(Mockito.mock(NlComponent.class));

    return new PreferenceScreenDragHandler(editor, new ViewGroupHandler(), group, preferences, DragType.MOVE);
  }
}
