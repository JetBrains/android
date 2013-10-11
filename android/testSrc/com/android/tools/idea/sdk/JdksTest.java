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

import com.android.tools.idea.AndroidTestCaseHelper;
import com.google.common.collect.Lists;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;

import java.util.List;

/**
 * Tests for {@link Jdks}.
 */
public class JdksTest extends IdeaTestCase {
  private String myJdk6HomePath;
  private String myJdk7HomePath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myJdk6HomePath = AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable("JAVA6_HOME");
    assertNotNull("Path to JDK 1.6 not set, please set JAVA6_HOME", myJdk6HomePath);

    myJdk7HomePath = AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable("JAVA7_HOME");
    assertNotNull("Path to JDK 1.7 not set, please set JAVA7_HOME", myJdk7HomePath);
  }

  public void testGetBestJdkHomePathWithLangLevel1dot6() {
    List<String> jdkHomePaths = Lists.newArrayList(myJdk6HomePath, myJdk7HomePath);
    String best = Jdks.getBestJdkHomePath(jdkHomePaths, LanguageLevel.JDK_1_6);
    assertTrue("Expected: " + myJdk6HomePath + ", actual: " + best, FileUtil.pathsEqual(myJdk6HomePath, best));
  }

  public void testGetBestJdkHomePathWithLangLevel1dot7() {
    List<String> jdkHomePaths = Lists.newArrayList(myJdk6HomePath, myJdk7HomePath);
    String best = Jdks.getBestJdkHomePath(jdkHomePaths, LanguageLevel.JDK_1_7);
    assertTrue("Expected: " + myJdk7HomePath + ", Actual: " + best, FileUtil.pathsEqual(myJdk7HomePath, best));
  }

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
