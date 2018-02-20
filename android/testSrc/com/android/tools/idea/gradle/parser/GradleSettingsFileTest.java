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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GradleSettingsFileTest extends IdeaTestCase {
  private Document myDocument;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDocument = null;
    // Kotlin plugin requests Groovy JDK if $ is found in the editor.
    IdeSdks.removeJdksOn(getTestRootDisposable());
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
    addModule(file, ":one");
    String expected = "include ':one'";
    assertContents(file, expected);
  }

  public void testAddModuleToExistingFile() throws Exception {
    final GradleSettingsFile file = getSimpleTestFile();
    addModule(file, ":four");
    String expected =
      "include ':one', ':two', ':four'\n" +
      "include ':three'\n" +
      "include callSomeMethod()";

    assertContents(file, expected);
  }

  /**
   * Module should be imported and its locations should be set to path relative to parent project root.
   */
  public void testAddModuleWithNondefaultLocationWithinProject() throws IOException {
    final String modulePath = "directory/module";
    final GradleSettingsFile file = getSimpleTestFile();
    File moduleLocation = new File(VfsUtilCore.virtualToIoFile(file.getFile().getParent()), modulePath);
    assertModuleWithNonDefaultLocation(file, ":mymodule", modulePath, moduleLocation);
  }

  /**
   * Module should be imported and its locations should be set to absolute path.
   */
  public void testAddModuleWithNondefaultLocationOutsideProject() throws IOException {
    final String modulePath = FileUtil.toSystemIndependentName(FileUtil.getTempDirectory()) + "/directory/module";
    File moduleLocation = new File(modulePath);
    final GradleSettingsFile file = getSimpleTestFile();
    assertModuleWithNonDefaultLocation(file, ":mymodule", modulePath, moduleLocation);
  }

  private void assertModuleWithNonDefaultLocation(final GradleSettingsFile file,
                                                  final String moduleName,
                                                  String expectedPath,
                                                  final File moduleLocation) throws IOException {
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.addModule(moduleName, moduleLocation);
      }
    });
    String expected =
      "include ':one', ':two', '%1$s'\n" +
      "include ':three'\n" +
      "include callSomeMethod()\n" +
      "project('%1$s').projectDir = new File('%2$s')";
    assertContents(file, String.format(expected, moduleName, expectedPath));
  }

  public void testAddModuleToLineContainingMethodCall() throws Exception {
    final GradleSettingsFile file = getMethodCallTestFile();
    addModule(file, ":one");
    String expected =
      "include callSomeMethod(), ':one'";

    assertContents(file, expected);
  }

  public void testRemovesFromLineWithMultipleModules() throws Exception {
    final GradleSettingsFile file = getSimpleTestFile();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
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
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.removeModule(":three");
      }
    });
    String expected =
      "include ':one', ':two'\n" +
      "include callSomeMethod()";

    assertContents(file, expected);
  }

  public void testRemovesMultipleEntries() throws Exception {
    final GradleSettingsFile file = getTestFile(
      "include ':one'\n" +
      "include ':one', ':two'"
    );
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.removeModule(":one");
      }
    });
    assertContents(file, "include ':two'");
  }

  public void testRemoveModuleAndLocation() throws IOException {
    final GradleSettingsFile file = getTestFile("include ':one', 'two'\n" +
                                          "project('two').projectDir = new File('modules/three')\n" +
                                          "project(':one').projectDir = new File('modules/four')");
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        file.removeModule("two");
      }
    });
    assertContents(file, "include ':one'\n" +
                         "project(':one').projectDir = new File('modules/four')");
  }

  public void testAddModuleStringChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.addModule("asdf", GradleUtil.getModuleDefaultPath(file.getFile(), ":asdf"));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testAddModuleChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.addModule(myModule);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testGetModulesChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.getModules();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testRemoveModuleStringChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.removeModule("asdf");
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testRemoveModuleChecksInitialization() {
    GradleSettingsFile file = getBadGradleSettingsFile();
    try {
      file.removeModule(myModule);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {}
  }

  public void testGetModulePath() throws IOException {
    GradleSettingsFile file = getTestFile("include ':one', 'two\n" +
                                          "project(':two').projectDir = new File('modules/three')\n"
    );
    Map<String, File> map = file.getModulesWithLocation();
    assertEquals(map.toString(), 2, map.size());
    assertEquals(new File("one"), map.get(":one"));
    assertEquals(new File("modules", "three"), map.get(":two"));
  }

  public void testRemoveModuleSpecifiedWithInclude() throws IOException {
    Collection<String> modules = Arrays.asList("one", "two", "three", "four", "five");
    final String body = getSettingsFileWithModules(modules);
    for (final String module : modules) {
      final GradleSettingsFile file = getTestFile(body);
      new WriteCommandAction<Object>(getProject(), file.getPsiFile()) {
        @Override
        protected void run(@NotNull Result<Object> result) throws Throwable {
          file.removeModule(module);
        }
      }.execute();
      Set<String> postDelete = ImmutableSet.copyOf(file.getModules());
      assertEquals(module + " was not deleted", modules.size() - 1, postDelete.size());
      assertFalse(module + " was not deleted", postDelete.contains(module));
      Predicate<String> notCurrentModule = Predicates.not(Predicates.equalTo(module));
      String expectedFileContents = getSettingsFileWithModules(Iterables.filter(modules, notCurrentModule));
      assertEquals(expectedFileContents, file.getPsiFile().getText());
    }
  }

  private static String getSettingsFileWithModules(Iterable<String> modules) {
    return "include \'" + Joiner.on("\'\ninclude \'").join(modules) + "\'\n";
  }

  private static void addModule(final GradleSettingsFile file, final String name) {
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        file.addModule(name, GradleUtil.getModuleDefaultPath(file.getFile().getParent(), name));
      }
    });
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

  private GradleSettingsFile getTestFile(String... contents) throws IOException {
    String fileContents = Joiner.on("\n").join(contents);
    VirtualFile vf = getVirtualFile(createTempFile(SdkConstants.FN_SETTINGS_GRADLE, fileContents));
    myDocument = FileDocumentManager.getInstance().getDocument(vf);
    return new GradleSettingsFile(vf, getProject());
  }

  private GradleSettingsFile getBadGradleSettingsFile() {
    // Use an intentionally invalid file path so that GradleSettingsFile will remain uninitialized. This simulates the condition of
    // the PSI file not being parsed yet. GradleSettingsFile will warn about the PSI file; this is expected.
    VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(FileUtil.getTempDirectory());
    assertNotNull(vf);
    return new GradleSettingsFile(vf, getProject());
  }

  private void assertContents(GradleSettingsFile file, String expected) throws IOException {
    PsiDocumentManager.getInstance(getProject()).commitDocument(myDocument);
    String actual = myDocument.getText();
    assertEquals(expected, actual);
  }
}
