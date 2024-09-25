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
package com.google.idea.blaze.android.run.test.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.execution.Location;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeAndroidTestEventsHandler}. */
@RunWith(JUnit4.class)
public class BlazeAndroidTestEventsHandlerTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private final BlazeAndroidTestEventsHandler handler = new BlazeAndroidTestEventsHandler();

  @Before
  public final void doSetup() {
    // BlazeAndroidTestLocator calls JUnitUtil::isTestClass, which requires junit classes.
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;"
            + "public @interface RunWith {"
            + "    Class<? extends Runner> value();"
            + "}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test"), "package org.junit;", "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
  }

  @Test
  public void testSuiteLocationResolves() {
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "import org.junit.Test;",
            "import org.junit.runner.RunWith;",
            "import org.junit.runners.JUnit4;",
            "@RunWith(JUnit4.class)",
            "public class JavaClass {",
            "  @Test",
            "  public void testMethod() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    assertThat(javaClass).isNotNull();

    String url =
        handler.suiteLocationUrl(
            Label.create("//java/com/google/lib:JavaClass"), null, "JavaClass");
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(javaClass);
  }

  @Test
  public void testMethodLocationResolves() {
    PsiFile javaFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "import org.junit.Test;",
            "import org.junit.runner.RunWith;",
            "import org.junit.runners.JUnit4;",
            "@RunWith(JUnit4.class)",
            "public class JavaClass {",
            "  @Test",
            "  public void testMethod() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];
    PsiMethod method = javaClass.findMethodsByName("testMethod", false)[0];
    assertThat(method).isNotNull();

    String url =
        handler.testLocationUrl(
            Label.create("//java/com/google/lib:JavaClass"),
            null,
            null,
            "JavaClass-testMethod",
            "com.google.test.AndroidTestBase");
    Location<?> location = getLocation(url);
    assertThat(location.getPsiElement()).isEqualTo(method);
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
}
