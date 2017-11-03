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

import com.android.tools.idea.uibuilder.SyncLayoutlibSceneManager;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.PreferenceTags.PREFERENCE_CATEGORY;
import static com.android.SdkConstants.PreferenceTags.PREFERENCE_SCREEN;

public final class PreferenceScreenDragHandlerTest extends PreferenceScreenTestCase {
  public void testUpdate() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());

    handler.update(180, 345, 0);
    assertEquals(PREFERENCE_CATEGORY, handler.myGroup.getNlComponent().getTagName());

    handler.update(180, 356, 0);
    assertEquals(PREFERENCE_SCREEN, handler.myGroup.getNlComponent().getTagName());

    handler.update(180, 370, 0);
    assertEquals(PREFERENCE_SCREEN, handler.myGroup.getNlComponent().getTagName());
  }

  public void testUpdateAdjacentCategories() {
    SyncNlModel model = model("model.xml", preferenceScreen(0, 162, 768, 548).children(
      checkBoxPreference(0, 162, 768, 102),
      preferenceCategory(0, 266, 768, 65)
        .id("@+id/category1")
        .unboundedChildren(checkBoxPreference(0, 333, 768, 102)),
      preferenceCategory(0, 437, 768, 65)
        .id("@+id/category2")
        .unboundedChildren(checkBoxPreference(0, 504, 768, 102)),
      checkBoxPreference(0, 608, 768, 102))).build();

    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(model);

    handler.update(180, 205, 0);
    assertEquals(model.find("category1"), handler.myGroup.getNlComponent());

    handler.update(180, 230, 0);
    assertEquals(model.find("category2"), handler.myGroup.getNlComponent());
  }

  public void testDrawDropPreviewLine() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(180, 345, 0);
    handler.drawDropPreviewLine(graphics);

    handler.update(180, 370, 0);
    handler.drawDropPreviewLine(graphics);

    Mockito.verify(graphics).drawBottomDp(new Rectangle(0, 304, 384, 52));
    Mockito.verify(graphics).drawTopDp(new Rectangle(0, 356, 384, 52));
  }

  public void testDrawDropRecipientLines() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(180, 251, 0);
    handler.drawDropRecipientLines(graphics);

    Rectangle bounds = new Rectangle(0, 166, 384, 190);

    Mockito.verify(graphics).drawTopDp(bounds);
    Mockito.verify(graphics).drawLeftDp(bounds);
    Mockito.verify(graphics).drawRightDp(bounds);
    Mockito.verify(graphics).drawBottomDp(bounds);
  }

  public void testDrawDropZoneLinesPointerIsBetweenFirstAndSecondChildren() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(180, 251, 0);
    handler.drawDropZoneLines(graphics);

    List<SceneComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(graphics).drawTop(preferences.get(0).getNlComponent());
    Mockito.verify(graphics).drawTop(preferences.get(2).getNlComponent());
  }

  public void testDrawDropZoneLinesPointerIsBetweenSecondAndThirdChildren() {
    PreferenceGroupDragHandler handler = newPreferenceScreenDragHandler(newPreferenceScreen());
    NlGraphics graphics = Mockito.mock(NlGraphics.class);

    handler.update(180, 303, 0);
    handler.drawDropZoneLines(graphics);

    List<SceneComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(graphics).drawTop(preferences.get(0).getNlComponent());
    Mockito.verify(graphics).drawTop(preferences.get(1).getNlComponent());
  }

  @NotNull
  private SyncNlModel newPreferenceScreen() {
    return model("model.xml",
                 preferenceScreen(0, 162, 768, 755).children(
                   checkBoxPreference(0, 162, 768, 168),
                   preferenceCategory(),
                   checkBoxPreference(0, 711, 768, 102),
                   checkBoxPreference(0, 815, 768, 102))).build();
  }

  @NotNull
  private PreferenceGroupDragHandler newPreferenceScreenDragHandler(@NotNull SyncNlModel model) {
    ScreenFixture screenFixture = new ScreenFixture(model).withScale(1);
    Scene scene = new SyncLayoutlibSceneManager(model).build();
    scene.buildDisplayList(new DisplayList(), 0);

    ViewEditor editor = editor(screenFixture.getScreen());
    SceneComponent component = scene.getRoot();
    return new PreferenceScreenDragHandler(editor, new ViewGroupHandler(), component, Collections.singletonList(component.getNlComponent()), DragType.MOVE);
  }
}
