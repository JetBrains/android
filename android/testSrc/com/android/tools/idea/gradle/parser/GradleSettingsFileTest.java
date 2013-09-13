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
import com.google.common.collect.Iterables;
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
import java.util.Arrays;

public class GradleSettingsFileTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
  }

  public void testGetModules() throws Exception {
    GradleSettingsFile file = getSimpleTestFile();
    String[] expected = new String[] {
      ":one", ":two", ":three"
    };
    assert Arrays.equals(expected, Iterables.toArray(file.getModules(), String.class));
  }

  public void testAddModuleToEmptyFile() throws Exception {
    final GradleSettingsFile file = getEmptyTestFile();
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.addModule(":one");
      }
    });
    String expected = "include ':one'";
    assertContents(file, expected);
  }

  public void testAddModuleToExistingFile() throws Exception {
    final GradleSettingsFile file = getSimpleTestFile();
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.addModule(":four");
      }
    });
    String expected =
      "include ':one', ':two', ':four'\n" +
      "include ':three'\n" +
      "include callSomeMethod()";

    assertContents(file, expected);
  }

  public void testAddModuleToLineContainingMethodCall() throws Exception {
    final GradleSettingsFile file = getMethodCallTestFile();
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.addModule(":one");
      }
    });
    String expected =
      "include callSomeMethod(), ':one'";

    assertContents(file, expected);
  }

  public void testRemovesFromLineWithMultipleModules() throws Exception {
    final GradleSettingsFile file = getSimpleTestFile();
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.removeModule(":two");
      }
    });
    String expected =
      "include ':one'\n" +
      "include ':three'\n" +
      "include callSomeMethod()";

    assertContents(file, expected);
  }

  public void testRemovesEntireLine() throws Exception {
    final GradleSettingsFile file = getSimpleTestFile();
    ActionRunner.runInsideWriteAction(new ActionRunner.InterruptibleRunnable() {
      @Override
      public void run() throws Exception {
        file.removeModule(":three");
      }
    });
    String expected =
      "include ':one', ':two'\n" +
      "include callSomeMethod()";

    assertContents(file, expected);
  }

  public void testRemovesMultipleEntries() throws Exception {
    GradleSettingsFile file = getTestFile(
      "include ':one'\n" +
      "include ':one', ':two'"
    );
    file.removeModule(":one");
    assertContents(file, "include ':two'");
  }

  public void testAddModuleStringChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.addModule("asdf");
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testAddModuleChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.addModule(myModule);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testGetModulesChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.getModules();
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testRemoveModuleStringChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.removeModule("asdf");
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  public void testRemoveModuleChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.removeModule(myModule);
    } catch (IllegalStateException e) {
      // expected
      return;
    }
    fail("Failed to get expected IllegalStateException");
  }

  private GradleSettingsFile getSimpleTestFile() throws IOException {
    String contents =
      "include ':one', ':two'\n" +
      "include ':three'\n" +
      "include callSomeMethod()";
    return getTestFile(contents);
  }

  private GradleSettingsFile getMethodCallTestFile() throws IOException {
    String contents =
      "include callSomeMethod()";
    return getTestFile(contents);
  }

  private GradleSettingsFile getEmptyTestFile() throws IOException {
    return getTestFile("");
  }

  private GradleSettingsFile getTestFile(String contents) throws IOException {
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_SETTINGS_GRADLE, contents));
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleSettingsFile(vf, getProject());
  }

  private GradleSettingsFile getBadGradleSettingsFile() {
    // Use an intentionally invalid file path so that GradleSettingsFile will remain uninitialized. This simulates the condition of
    // the PSI file not being parsed yet. GradleSettingsFile will warn about the PSI file; this is expected.
    VirtualFile vf = LocalFileSystem.getInstance().getRoot();
    return new GradleSettingsFile(vf, getProject());
  }

  private void assertContents(GradleSettingsFile file, String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }
}
