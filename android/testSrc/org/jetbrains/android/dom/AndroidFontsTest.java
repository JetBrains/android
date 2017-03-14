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
package org.jetbrains.android.dom;

public class AndroidFontsTest extends AndroidDomTestCase {

  public AndroidFontsTest() throws Exception {
    super("dom/font");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyFileToProject("FakeFont.txt", "res/font/Lobster.ttf");
    copyFileToProject("FakeFont.txt", "res/font/FancyFont.ttf");
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/layout/" + testFileName;
  }

  public void testValueCompletion1() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "@font/FancyFont", "@font/Lobster");
  }

  public void testValueCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "@string/hello", "@string/hello1");
  }

  public void testValueCompletion3() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "sans-serif", "sans-serif-condensed", "sans-serif-smallcaps");
  }

  public void testValueCompletion4() throws Throwable {
    doTestCompletion();
  }
}
