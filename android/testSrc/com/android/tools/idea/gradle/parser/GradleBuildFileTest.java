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
package com.android.tools.idea.gradle.parser;

import com.android.SdkConstants;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.ActionRunner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.io.IOException;

public class GradleBuildFileTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
  }

  public void testGetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    assertEquals("17.0.0", file.getValue(GradleBuildFile.BuildSettingKey.BUILD_TOOLS_VERSION));
  }

  public void testNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertEquals(1, file.getValue(closure, GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION));
  }

  public void testCanParseSimpleValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    assertTrue(file.canParseValue(GradleBuildFile.BuildSettingKey.BUILD_TOOLS_VERSION));
  }

  public void testCantParseComplexValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    GrStatementOwner closure = file.getClosure("android/defaultConfig");
    assertFalse(file.canParseValue(closure, GradleBuildFile.BuildSettingKey.MIN_SDK_VERSION));
  }

  public void testSetTopLevelValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.setValue(GradleBuildFile.BuildSettingKey.BUILD_TOOLS_VERSION, "18.0.0");
      }
    });
    String expected = getSimpleTestFile().replaceAll("17.0.0", "18.0.0");
    assertContents(file, expected);
  }

  public void testSetNestedValue() throws Exception {
    final GradleBuildFile file = getTestFile(getSimpleTestFile());
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        GrStatementOwner closure = file.getClosure("android/defaultConfig");
        file.setValue(closure, GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION, 2);
      }
    });
    String expected = getSimpleTestFile().replaceAll("targetSdkVersion 1", "targetSdkVersion 2");
    assertContents(file, expected);
  }

  public void testCanParseValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.canParseValue(GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testCanParseNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.canParseValue(getDummyClosure(), GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetClosureChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getClosure("/");
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.getValue(getDummyClosure(), GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testSetValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION, 2);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testSetNestedValueChecksInitialization() {
    GradleBuildFile file = getBadGradleBuildFile();
    try {
      file.setValue(getDummyClosure(), GradleBuildFile.BuildSettingKey.TARGET_SDK_VERSION, 2);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetsPropertyFromRedundantBlock() throws Exception {
    GradleBuildFile file = getTestFile(
      "android {\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "}\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "}\n"
    );
    assertEquals(17, file.getValue(GradleBuildFile.BuildSettingKey.COMPILE_SDK_VERSION));
    assertEquals("17.0.0", file.getValue(GradleBuildFile.BuildSettingKey.BUILD_TOOLS_VERSION));
  }

  private static String getSimpleTestFile() throws IOException {
    return
      "buildscript {\n" +
      "    repositories {\n" +
      "        mavenCentral()\n" +
      "    }\n" +
      "    dependencies {\n" +
      "        classpath 'com.android.tools.build:gradle:0.5.+'\n" +
      "    }\n" +
      "}\n" +
      "apply plugin: 'android'\n" +
      "\n" +
      "repositories {\n" +
      "    mavenCentral()\n" +
      "}\n" +
      "\n" +
      "dependencies {\n" +
      "    compile 'com.android.support:support-v4:13.0.+'\n" +
      "}\n" +
      "\n" +
      "android {\n" +
      "    compileSdkVersion 17\n" +
      "    buildToolsVersion '17.0.0'\n" +
      "\n" +
      "    defaultConfig {\n" +
      "        minSdkVersion someCrazyMethodCall()\n" +
      "        targetSdkVersion 1\n" +
      "    }\n" +
      "}";
  }

  private GradleBuildFile getTestFile(String contents) throws IOException {
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_BUILD_GRADLE, contents));
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleBuildFile(vf, getProject());
  }

  private GradleBuildFile getBadGradleBuildFile() {
    // Use an intentionally invalid file path so that GradleBuildFile will remain uninitialized. This simulates the condition of
    // the PSI file not being parsed yet. GradleBuildFile will warn about the PSI file; this is expected.
    VirtualFile vf = LocalFileSystem.getInstance().getRoot();
    return new GradleBuildFile(vf, getProject());
  }

  private GrStatementOwner getDummyClosure() {
    return GroovyPsiElementFactory.getInstance(myProject).createClosureFromText("{}");
  }

  private void assertContents(GradleBuildFile file, String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }
}
