/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static com.google.common.truth.Truth.assertThat;

public class AndroidDocumentationProviderTest extends AndroidTestCase {
  private String readTestFile(String path) throws IOException {
    VirtualFile virtualFile = myFixture.copyFileToProject("javadoc/classes/" + path, path);
    return VfsUtilCore.loadText(virtualFile);
  }

  public void testExternalFilterOldFormat() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    // Copied from SDK docs v23 rev 1
    String input = readTestFile("oldPoint.html");
    String output = readTestFile("oldPointSummary.html");
    String url = "http://developer.android.com/reference/android/graphics/Point.html";
    checkFilter(url, input, output);
  }

  public void testExternalFilterNewFormat() throws Exception {
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    // Downloaded July 2016 with curl -o <output> https://developer.android.com/reference/android/graphics/Point.html
    String input = readTestFile("newPoint.html");
    String output = readTestFile("newPointSummary.html");
    String url = "http://developer.android.com/reference/android/graphics/Point.html";
    checkFilter(url, input, output);
  }

  public void checkFilter(String url, String input, String expected) throws Exception {
    AndroidDocumentationProvider.MyDocExternalFilter filter = new AndroidDocumentationProvider.MyDocExternalFilter(getProject());
    StringBuilder builder = new StringBuilder(1000);
    BufferedReader reader = new BufferedReader(new StringReader(input));
    filter.doBuildFromStream(url, reader, builder);
    assertThat(builder.toString()).isEqualTo(StringUtil.convertLineSeparators(expected));
  }
}