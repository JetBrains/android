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
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.HandlerTestFactory;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

import java.util.Collections;

import static com.android.SdkConstants.FQCN_LIST_VIEW;
import static com.android.SdkConstants.PreferenceTags.*;

final class PreferenceScreenTestFactory {
  private PreferenceScreenTestFactory() {
  }

  @NotNull
  static ViewEditor mockEditor() {
    ListView listView = Mockito.mock(ListView.class);
    Mockito.when(listView.getDividerHeight()).thenReturn(2);

    ViewInfo view = new ViewInfo(FQCN_LIST_VIEW, null, 0, 0, 0, 0, listView, null);

    ViewEditor editor = Mockito.mock(ViewEditor.class);
    Mockito.when(editor.getRootViews()).thenReturn(Collections.singletonList(view));

    return editor;
  }

  @NotNull
  static NlComponent newCheckBoxPreference(int x, int y, int width, int height) {
    NlComponent preference = HandlerTestFactory.newNlComponent(CHECK_BOX_PREFERENCE);
    preference.setBounds(x, y, width, height);

    return preference;
  }

  @NotNull
  static NlComponent newPreferenceCategory() {
    NlComponent category = newPreferenceCategory(0, 332, 768, 65);

    category.addChild(newCheckBoxPreference(0, 399, 768, 102));
    category.addChild(newCheckBoxPreference(0, 503, 768, 102));
    category.addChild(newCheckBoxPreference(0, 607, 768, 102));

    return category;
  }

  @NotNull
  static NlComponent newPreferenceCategory(int x, int y, int width, int height) {
    NlComponent category = HandlerTestFactory.newNlComponent(PREFERENCE_CATEGORY);
    category.setBounds(x, y, width, height);

    return category;
  }
}
