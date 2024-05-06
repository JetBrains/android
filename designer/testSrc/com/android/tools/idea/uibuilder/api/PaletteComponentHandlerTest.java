/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api;

import com.android.support.AndroidxName;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.android.AndroidXConstants.CARD_VIEW;
import static com.android.AndroidXConstants.CLASS_BROWSE_FRAGMENT;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER;
import static com.android.AndroidXConstants.CLASS_FLOATING_ACTION_BUTTON;
import static com.android.AndroidXConstants.NESTED_SCROLL_VIEW;
import static com.android.AndroidXConstants.RECYCLER_VIEW;
import static com.android.AndroidXConstants.TOOLBAR_V7;
import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class PaletteComponentHandlerTest {
  PaletteComponentHandler handler = new PaletteComponentHandler() {};

  @Test
  public void testGetGradleCoordinateId() {
    checkGradleCoordinateId(NESTED_SCROLL_VIEW, SUPPORT_LIB_ARTIFACT, ANDROIDX_SUPPORT_LIB_ARTIFACT);
    checkGradleCoordinateId(CLASS_CONSTRAINT_LAYOUT_BARRIER, CONSTRAINT_LAYOUT_LIB_ARTIFACT, ANDROIDX_CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    checkGradleCoordinateId(CLASS_BROWSE_FRAGMENT, LEANBACK_V17_ARTIFACT, ANDROIDX_LEANBACK_ARTIFACT);
    checkGradleCoordinateId(CLASS_FLOATING_ACTION_BUTTON, DESIGN_LIB_ARTIFACT, ANDROIDX_MATERIAL_ARTIFACT);
    // Note: RecyclerViewHandler is overriding the default and will return RECYCLER_VIEW_LIB_ARTIFACT for non AndroidX controls
    checkGradleCoordinateId(RECYCLER_VIEW, APPCOMPAT_LIB_ARTIFACT, ANDROIDX_RECYCLER_VIEW_ARTIFACT);
    // Note: CardViewHandler is overriding the default and will return CARD_VIEW_LIB_ARTIFACT for non AndroidX controls
    checkGradleCoordinateId(CARD_VIEW, APPCOMPAT_LIB_ARTIFACT, ANDROIDX_CARD_VIEW_ARTIFACT);
    checkGradleCoordinateId(TOOLBAR_V7, APPCOMPAT_LIB_ARTIFACT, ANDROIDX_APPCOMPAT_LIB_ARTIFACT);
  }

  private void checkGradleCoordinateId(@NotNull AndroidxName name, @NotNull String expectedOld, @NotNull String expectedNew) {
    assertThat(handler.getGradleCoordinateId(name.oldName()).toString()).isEqualTo(expectedOld);
    assertThat(handler.getGradleCoordinateId(name.newName()).toString()).isEqualTo(expectedNew);
  }
}
