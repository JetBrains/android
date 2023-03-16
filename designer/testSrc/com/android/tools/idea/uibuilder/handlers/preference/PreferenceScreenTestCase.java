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

import android.widget.ListView;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.Collections;

import static com.android.SdkConstants.FQCN_LIST_VIEW;
import static com.android.SdkConstants.PreferenceTags.*;

abstract class PreferenceScreenTestCase extends LayoutTestCase {

  @Override
  @NotNull
  protected ViewEditor editor(@NotNull ScreenView screenView) {
    ListView list = Mockito.mock(ListView.class);
    Mockito.when(list.getDividerHeight()).thenReturn(2);

    ViewEditorImpl editor = new ViewEditorImpl(screenView);
    editor.setRootViews(Collections.singletonList(new ViewInfo(FQCN_LIST_VIEW, null, 0, 0, 0, 0, list, null, null)));

    return editor;
  }

  @NotNull
  ComponentDescriptor checkBoxPreference(@AndroidCoordinate int x,
                                         @AndroidCoordinate int y,
                                         @AndroidCoordinate int width,
                                         @AndroidCoordinate int height) {
    return component(CHECK_BOX_PREFERENCE).withBounds(x, y, width, height);
  }

  @NotNull
  ComponentDescriptor preferenceCategory() {
    return preferenceCategory(0, 332, 768, 65).unboundedChildren(
      checkBoxPreference(0, 399, 768, 102),
      checkBoxPreference(0, 503, 768, 102),
      checkBoxPreference(0, 607, 768, 102));
  }

  @NotNull
  ComponentDescriptor preferenceCategory(@AndroidCoordinate int x,
                                         @AndroidCoordinate int y,
                                         @AndroidCoordinate int width,
                                         @AndroidCoordinate int height) {
    return component(PREFERENCE_CATEGORY).withBounds(x, y, width, height);
  }

  @NotNull
  ComponentDescriptor preferenceScreen(@AndroidCoordinate int x,
                                       @AndroidCoordinate int y,
                                       @AndroidCoordinate int width,
                                       @AndroidCoordinate int height) {
    return component(PREFERENCE_SCREEN).withBounds(x, y, width, height);
  }
}
