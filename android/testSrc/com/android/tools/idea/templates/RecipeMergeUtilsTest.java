/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.util.SystemProperties;
import junit.framework.TestCase;

public class RecipeMergeUtilsTest extends TestCase {
  private final static String LINE_SEPARATOR = SystemProperties.getLineSeparator();

  public void testMergeGradleSettingsFileEmptyDst() throws RuntimeException {
    String src = "include ':a'\r\ninclude ':b'\n";
    String dst = "";
    String expected = "include ':a'" + LINE_SEPARATOR +
                      "include ':b'" + LINE_SEPARATOR;
    assertThat(RecipeMergeUtils.mergeGradleSettingsFile(src, dst)).isEqualTo(expected);
  }

  public void testMergeGradleSettingsFileEmptySrc() throws RuntimeException {
    String src = "";
    String dst = "include ':a'" + LINE_SEPARATOR +
                 "// Some comment" + LINE_SEPARATOR +
                 "   " + LINE_SEPARATOR +
                 "   " + LINE_SEPARATOR;
    assertThat(RecipeMergeUtils.mergeGradleSettingsFile(src, dst)).isEqualTo(dst);
  }

  public void testMergeGradleSettingsFileAlreadyInclude() throws RuntimeException {
    String src = "include ':b'" + LINE_SEPARATOR +
                 "include ':c'" + LINE_SEPARATOR;
    String dst = "include ':a'" + LINE_SEPARATOR +
                 "// Some comment" + LINE_SEPARATOR;
    String expected = dst + src;
    assertThat(RecipeMergeUtils.mergeGradleSettingsFile(src, dst)).isEqualTo(expected);
  }

  public void testMergeGradleSettingsFileNoNewLineComments() throws RuntimeException {
    String src = "include ':b'" + LINE_SEPARATOR +
                 "include ':c'" + LINE_SEPARATOR;
    String dst = "include ':a'" + LINE_SEPARATOR +
                 "/* Some comment" + LINE_SEPARATOR +
                 "  include ':notIncluded // This should not be used" + LINE_SEPARATOR +
                 "*/";
    String expected = dst + LINE_SEPARATOR + src;
    assertThat(RecipeMergeUtils.mergeGradleSettingsFile(src, dst)).isEqualTo(expected);
  }

  public void testMergeGradleSettingsFileNoIncludeInSrc() throws RuntimeException {
    String src = "Not valid input";
    String dst = "include ':a'" + LINE_SEPARATOR;
    try {
      RecipeMergeUtils.mergeGradleSettingsFile(src, dst);
      fail("No exception was caused for non include line.");
    }
    catch (RuntimeException runTimeException) {
      String expectedMessage = "When merging settings.gradle files, only include directives can be merged.";
      assertThat(runTimeException).hasMessageThat().isEqualTo(expectedMessage);
    }
  }
}
