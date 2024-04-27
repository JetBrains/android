/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.AndroidXConstants.CARD_VIEW;
import static com.android.SdkConstants.ANDROIDX_CARD_VIEW_ARTIFACT;
import static com.android.SdkConstants.CARD_VIEW_LIB_ARTIFACT;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class CardViewHandlerTest {

  @Test
  public void testGetGradleCoordinateId() {
    CardViewHandler handler = new CardViewHandler();
    assertThat(handler.getGradleCoordinateId(CARD_VIEW.oldName()).toString()).isEqualTo(CARD_VIEW_LIB_ARTIFACT);
    assertThat(handler.getGradleCoordinateId(CARD_VIEW.newName()).toString()).isEqualTo(ANDROIDX_CARD_VIEW_ARTIFACT);
  }
}