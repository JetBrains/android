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
import com.android.tools.idea.uibuilder.api.XmlType;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class TextInputLayoutHandlerTest {

  @Test
  public void testGetXml() {
    @Language("XML")
    String expected = "<android.support.design.widget.TextInputLayout\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"wrap_content\">\n" +
                      "\n" +
                      "    <android.support.design.widget.TextInputEditText\n" +
                      "        android:layout_width=\"match_parent\"\n" +
                      "        android:layout_height=\"wrap_content\"\n" +
                      "        android:hint=\"hint\" />\n" +
                      "</android.support.design.widget.TextInputLayout>\n";
    TextInputLayoutHandler handler = new TextInputLayoutHandler();
    assertThat(handler.getXml(SdkConstants.TEXT_INPUT_LAYOUT, XmlType.COMPONENT_CREATION)).isEqualTo(expected);
  }
}
