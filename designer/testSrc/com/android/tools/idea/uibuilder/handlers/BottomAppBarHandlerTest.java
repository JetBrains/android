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
package com.android.tools.idea.uibuilder.handlers;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import org.junit.Test;

public class BottomAppBarHandlerTest {

  @Test
  public void testGetXml() {
    String expected =
      "<com.google.android.material.bottomappbar.BottomAppBar\n" +
      "    android:layout_width=\"match_parent\"\n" +
      "    android:layout_height=\"wrap_content\"\n" +
      "    style=\"@style/Widget.MaterialComponents.BottomAppBar.Colored\"\n" +
      "    android:layout_gravity=\"bottom\" />\n";
    ViewHandler handler = new BottomAppBarHandler();
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.COMPONENT_CREATION)).isEqualTo(expected);
    assertThat(handler.getXml(SdkConstants.BOTTOM_APP_BAR, XmlType.DRAG_PREVIEW)).isEqualTo(expected);
  }
}
