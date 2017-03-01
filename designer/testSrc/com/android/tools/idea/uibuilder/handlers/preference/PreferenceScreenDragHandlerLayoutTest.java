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

import com.android.SdkConstants.PreferenceTags;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneBuilder;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.TemporarySceneComponent;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PreferenceScreenDragHandlerLayoutTest extends PreferenceScreenTestCase {
  public void testCommit() {
    NlModel model = buildModel();
    ScreenFixture screenFixture = surface().screen(model).withScale(1);
    LayoutlibSceneBuilder builder = new LayoutlibSceneBuilder(model, screenFixture.getScreen());
    Scene scene = builder.build();
    scene.buildDisplayList(new DisplayList(), 0);

    NlComponent screen = model.getComponents().get(0);
    List<NlComponent> actualCategoryChildren = screen.getChildren().get(1).getChildren();
    NlComponent preference = new NlComponent(model, NlModel.createTag(myModule.getProject(), "<CheckBoxPreference />"));

    List<NlComponent> expectedCategoryChildren = Arrays.asList(
      actualCategoryChildren.get(0),
      preference,
      actualCategoryChildren.get(1),
      actualCategoryChildren.get(2));

    DragHandler handler =
      new PreferenceScreenDragHandler(editor(screenFixture.getScreen()), new ViewGroupHandler(), scene.getSceneComponent(screen),
                                      Collections.singletonList(builder.createTemporaryComponent(preference)), DragType.MOVE);

    handler.update(180, 251, 0);
    handler.commit(360, 502, 0, InsertType.CREATE);

    assertEquals(expectedCategoryChildren, actualCategoryChildren);
  }

  @NotNull
  private NlModel buildModel() {
    ComponentDescriptor screen = component(PreferenceTags.PREFERENCE_SCREEN)
      .withBounds(0, 162, 768, 755)
      .children(
        checkBoxPreference(0, 162, 768, 168),
        component(PreferenceTags.PREFERENCE_CATEGORY)
          .withBounds(0, 332, 768, 65)
          .unboundedChildren(
            checkBoxPreference(0, 399, 768, 102),
            checkBoxPreference(0, 503, 768, 102),
            checkBoxPreference(0, 607, 768, 102)),
        checkBoxPreference(0, 711, 768, 102),
        checkBoxPreference(0, 815, 768, 102));

    return model("pref_general.xml", screen)
      .build();
  }
}
