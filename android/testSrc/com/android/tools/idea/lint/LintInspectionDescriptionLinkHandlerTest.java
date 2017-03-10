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
package com.android.tools.idea.lint;

import com.intellij.openapi.editor.Editor;
import junit.framework.TestCase;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class LintInspectionDescriptionLinkHandlerTest extends TestCase {
  public void test() {
    Editor editor = mock(Editor.class);
    LintInspectionDescriptionLinkHandler handler = new LintInspectionDescriptionLinkHandler();

    assertThat(handler.getDescription("_unknown_", editor)).isNull();

    String issueExplanation = "You should set an icon for the application as whole because there is no default. " +
                              "This attribute must be set as a reference to a drawable resource containing the image " +
                              "(for example <code>@drawable/icon</code>)." +
                              "<br><br>More info:<br><a href=\"" +
                              "http://developer.android.com/tools/publishing/preparing.html#publishing-configure" +
                              "\">http://developer.android.com/tools/publishing/preparing.html#publishing-configure</a><br>";
    assertThat(handler.getDescription("MissingApplicationIcon", editor)).isEqualTo(issueExplanation);
  }
}