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

import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public final class PreferenceCategoryDragHandlerTest extends PreferenceScreenTestCase {
  private NlGraphics myGraphics;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myGraphics = Mockito.mock(NlGraphics.class);
  }

  public void testPaint() {
    SyncNlModel model = model("model.xml", preferenceCategory(0, 162, 768, 65).id("@+id/category")).build();
    DragHandler handler = newPreferenceCategoryDragHandler(model, model.find("category"));

    handler.update(180, 90, 0, SceneContext.get());
    handler.paint(myGraphics);

    @AndroidDpCoordinate Rectangle bounds = new Rectangle(0, 81, 384, 34);

    Mockito.verify(myGraphics).useStyle(NlDrawingStyle.DROP_PREVIEW);
    Mockito.verify(myGraphics).drawBottomDp(bounds);

    Mockito.verify(myGraphics).useStyle(NlDrawingStyle.DROP_RECIPIENT);
    Mockito.verify(myGraphics).drawTopDp(bounds);
    Mockito.verify(myGraphics).drawLeftDp(bounds);
    Mockito.verify(myGraphics).drawRightDp(bounds);
  }

  public void testDrawDropRecipientLines() {
    SyncNlModel model = model("model.xml", preferenceCategory().id("@+id/category")).build();
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(model, model.find("category"));

    handler.update(180, 175, 0, SceneContext.get());
    handler.drawDropRecipientLines(myGraphics);

    @AndroidDpCoordinate Rectangle bounds = new Rectangle(0, 166, 384, 190);

    Mockito.verify(myGraphics).drawTopDp(bounds);
    Mockito.verify(myGraphics).drawLeftDp(bounds);
    Mockito.verify(myGraphics).drawRightDp(bounds);
    Mockito.verify(myGraphics).drawBottomDp(bounds);
  }

  public void testDrawDropZoneLinesPointerIsInSecondHalfOfFirstChild() {
    SyncNlModel model = model("model.xml", preferenceCategory().id("@+id/category")).build();
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(model, model.find("category"));

    handler.update(180, 240, 0, SceneContext.get());
    handler.drawDropZoneLines(myGraphics);

    List<SceneComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(myGraphics).drawTop(preferences.get(0).getNlComponent());
    Mockito.verify(myGraphics).drawTop(preferences.get(2).getNlComponent());
  }

  public void testDrawDropZoneLinesPointerIsInFirstHalfOfSecondChild() {
    SyncNlModel model = model("model.xml", preferenceCategory().id("@+id/category")).build();
    PreferenceGroupDragHandler handler = newPreferenceCategoryDragHandler(model, model.find("category"));

    handler.update(180, 265, 0, SceneContext.get());
    handler.drawDropZoneLines(myGraphics);

    List<SceneComponent> preferences = handler.myGroup.getChildren();

    Mockito.verify(myGraphics).drawTop(preferences.get(0).getNlComponent());
    Mockito.verify(myGraphics).drawTop(preferences.get(2).getNlComponent());
  }

  @NotNull
  private PreferenceGroupDragHandler newPreferenceCategoryDragHandler(@NotNull SyncNlModel model, @NotNull NlComponent category) {
    ScreenFixture screenFixture = new ScreenFixture(model).withScale(1);
    SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    manager.setIgnoreRenderRequests(true);
    Scene scene = manager.getScene();
    scene.buildDisplayList(new DisplayList(), 0);

    SceneComponent component = scene.getSceneComponent(category);
    return new PreferenceCategoryDragHandler(
      editor(screenFixture.getScreen()), new ViewGroupHandler(), component, Collections.singletonList(component.getNlComponent()),
      DragType.MOVE);
  }
}
