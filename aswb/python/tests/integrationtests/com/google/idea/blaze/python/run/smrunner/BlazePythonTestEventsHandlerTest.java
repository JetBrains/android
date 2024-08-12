/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.execution.Location;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazePythonTestEventsHandler}. */
@RunWith(JUnit4.class)
public class BlazePythonTestEventsHandlerTest extends BlazeIntegrationTestCase {

  private final BlazePythonTestEventsHandler handler = new BlazePythonTestEventsHandler();

  @Test
  public void testSuiteLocationResolves() {
    PsiFile file =
        workspace.createPsiFile(
            new WorkspacePath("lib/app_unittest.py"),
            "class AppUnitTest:",
            "  def function(self):",
            "    return");
    PyClass pyClass = PsiUtils.findFirstChildOfClassRecursive(file, PyClass.class);
    assertThat(pyClass).isNotNull();

    String url = handler.suiteLocationUrl(Label.create("//lib:app_unittest"), null, "AppUnitTest");
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(pyClass);
  }

  @Test
  public void testFunctionLocationOldFormatResolves() {
    PsiFile file =
        workspace.createPsiFile(
            new WorkspacePath("lib/app_unittest.py"),
            "class AppUnitTest:",
            "  def testApp(self):",
            "    return");
    PyClass pyClass = PsiUtils.findFirstChildOfClassRecursive(file, PyClass.class);
    PyFunction function = pyClass.findMethodByName("testApp", false, null);
    assertThat(function).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//lib:app_unittest"), null, null, "__main__.AppUnitTest.testApp", null);
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(function);
  }

  @Test
  public void testFunctionLocationResolves() {
    PsiFile file =
        workspace.createPsiFile(
            new WorkspacePath("lib/app_unittest.py"),
            "class AppUnitTest:",
            "  def testApp(self):",
            "    return");
    PyClass pyClass = PsiUtils.findFirstChildOfClassRecursive(file, PyClass.class);
    PyFunction function = pyClass.findMethodByName("testApp", false, null);
    assertThat(function).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//lib:app_unittest"), null, null, "__main__.AppUnitTest::testApp", null);
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(function);
  }

  @Test
  public void testFunctionWithoutMainPrefixResolves() {
    PsiFile file =
        workspace.createPsiFile(
            new WorkspacePath("lib/app/app_unittest.py"),
            "class AppUnitTest:",
            "  def testApp(self):",
            "    return");
    PyClass pyClass = PsiUtils.findFirstChildOfClassRecursive(file, PyClass.class);
    PyFunction function = pyClass.findMethodByName("testApp", false, null);
    assertThat(function).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//lib/app:app_unittest"),
            null,
            null,
            "lib.app.AppUnitTest.testApp",
            null);
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(function);
  }

  @Nullable
  private Location<?> getLocation(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    String path = VirtualFileManager.extractPath(url);
    if (protocol == null) {
      return null;
    }
    return Iterables.getFirst(
        handler
            .getTestLocator()
            .getLocation(protocol, path, getProject(), GlobalSearchScope.allScope(getProject())),
        null);
  }

  @Test
  public void testDisplayNameClassTest() {
    String testName =
        handler.testDisplayName(
            Label.create("//lib:app_unittest"),
            Kind.fromRuleName("py_test"),
            "__main__.PythonModule.testDisplayName");
    assertThat(testName).isEqualTo("testDisplayName");
  }

  @Test
  public void testDisplayNameParameterizedTest() {
    String testName =
        handler.testDisplayName(
            Label.create("//lib:app_unittest"),
            Kind.fromRuleName("py_test"),
            "testParameterized(1, 2, 3)");
    assertThat(testName).isEqualTo("testParameterized(1, 2, 3)");
  }

  @Test
  public void testDisplayNameParameterizedTestWithDot() {
    String testName =
        handler.testDisplayName(
            Label.create("//lib:app_unittest"),
            Kind.fromRuleName("py_test"),
            "testParameterized('file.txt')");
    assertThat(testName).isEqualTo("testParameterized('file.txt')");
  }

  @Test
  public void testDisplayNameFallback() {
    String testName =
        handler.testDisplayName(
            Label.create("//lib:app_unittest"),
            Kind.fromRuleName("py_test"),
            "testWithNoDotOrBracket");
    assertThat(testName).isEqualTo("testWithNoDotOrBracket");
  }
}
