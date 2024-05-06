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

import static com.android.tools.idea.uibuilder.NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel;

import com.android.SdkConstants.PreferenceTags;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PreferenceScreenDragHandlerLayoutTest extends PreferenceScreenTestCase {
  public void testCommit() {
    SyncNlModel model = buildModel();
    ScreenFixture screenFixture = new ScreenFixture(model).withScale(1);
    SyncLayoutlibSceneManager builder = getSyncLayoutlibSceneManagerForModel(model);
    builder.setIgnoreRenderRequests(true);
    Scene scene = builder.getScene();
    scene.buildDisplayList(new DisplayList(), 0);

    NlComponent screen = model.getComponents().get(0);
    List<NlComponent> actualCategoryChildren = screen.getChildren().get(1).getChildren();
    NlComponent preference = model.createComponent(XmlTagUtil.createTag(myModule.getProject(), "<CheckBoxPreference />"));

    List<NlComponent> expectedCategoryChildren = Arrays.asList(
      actualCategoryChildren.get(0),
      preference,
      actualCategoryChildren.get(1),
      actualCategoryChildren.get(2));

    DragHandler handler =
      new PreferenceScreenDragHandler(editor(screenFixture.getScreen()), new ViewGroupHandler(), scene.getSceneComponent(screen),
                                      Collections.singletonList(preference), DragType.MOVE);

    handler.update(180, 251, 0, SceneContext.get());
    handler.commit(360, 502, 0, InsertType.CREATE);

    assertContainsElements(expectedCategoryChildren, actualCategoryChildren);
  }

  @NotNull
  private SyncNlModel buildModel() {
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
