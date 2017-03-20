/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class FloatingActionButtonHandlerTest {
  private ViewHandler myHandler;

  @Before
  public void setUp() throws Exception {
    myHandler = new FloatingActionButtonHandler();
  }

  @Test
  public void testGetXml() {
    assertThat(myHandler.getXml(SdkConstants.FLOATING_ACTION_BUTTON, XmlType.COMPONENT_CREATION)).isEqualTo(
      "<android.support.design.widget.FloatingActionButton\n" +
      "    android:src=\"@android:drawable/ic_input_add\"\n" +
      "    android:layout_width=\"wrap_content\"\n" +
      "    android:layout_height=\"wrap_content\"\n" +
      "    android:clickable=\"true\" />\n");
  }

  @Test
  public void testGetPreviewXml() {
    assertThat(myHandler.getXml(SdkConstants.FLOATING_ACTION_BUTTON, XmlType.PREVIEW_ON_PALETTE)).isEqualTo(
      "<android.support.design.widget.FloatingActionButton\n" +
      "    android:src=\"@android:drawable/ic_input_add\"\n" +
      "    android:layout_width=\"wrap_content\"\n" +
      "    android:layout_height=\"wrap_content\"\n" +
      "    android:clickable=\"true\"\n" +
      "    app:elevation=\"0dp\" />\n");
  }
}
