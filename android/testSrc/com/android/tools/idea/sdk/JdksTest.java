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
package com.android.tools.idea.sdk;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Tests for {@link Jdks}.
 */
public class JdksTest extends IdeaTestCase {

  // These tests verify that LanguageLevel#isAtLeast does what we think it does (this is IntelliJ code.) Leaving these tests here as a way
  // ensure that regressions are not introduced later.
  public void testHasMatchingLangLevelWithLangLevel1dot6AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_6));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_7));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk6() {
    assertFalse(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_6, LanguageLevel.JDK_1_7));
  }
}
