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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link BlazeJUnitTestFilterFlags}. The functionality that relies on
 * PsiElements, so can't go in the unit tests.
 */
@RunWith(JUnit4.class)
public class BlazeJUnitTestFilterFlagsIntegrationTest extends BlazeIntegrationTestCase {

  @Before
  public final void doSetup() {
    // required for IntelliJ to recognize annotations, JUnit version, etc.
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runner/RunWith.java"),
        "package org.junit.runner;"
            + "public @interface RunWith {"
            + "    Class<? extends Runner> value();"
            + "}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/Test.java"),
        "package org.junit;",
        "public @interface Test {}");
    workspace.createPsiFile(
        new WorkspacePath("org/junit/runners/JUnit4.java"),
        "package org.junit.runners;",
        "public class JUnit4 {}");
  }

  @Test
  public void testParameterizedMethods() {
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
            "  public void testMethod1() {}",
            "  @Test",
            "  public void testMethod2() {}",
            "}");
    PsiClass javaClass = ((PsiClassOwner) javaFile).getClasses()[0];

    PsiMethod method1 = javaClass.findMethodsByName("testMethod1", false)[0];
    Location<?> location1 =
        new PsiMemberParameterizedLocation(getProject(), method1, javaClass, "[param]");

    PsiMethod method2 = javaClass.findMethodsByName("testMethod2", false)[0];
    Location<?> location2 =
        new PsiMemberParameterizedLocation(getProject(), method2, javaClass, "[3]");

    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(
                ImmutableMap.of(javaClass, ImmutableList.of(location1, location2))))
        .isEqualTo("com.google.lib.JavaClass#(testMethod1\\[param\\]|testMethod2\\[3\\])$");
  }

  @Test
  public void testMultipleClassesWithParameterizedMethods() {
    PsiFile javaFile1 =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass1.java"),
            "package com.google.lib;",
            "import org.junit.Test;",
            "import org.junit.runner.RunWith;",
            "import org.junit.runners.JUnit4;",
            "@RunWith(JUnit4.class)",
            "public class JavaClass1 {",
            "  @Test",
            "  public void testMethod1() {}",
            "  @Test",
            "  public void testMethod2() {}",
            "}");
    PsiFile javaFile2 =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaClass2.java"),
            "package com.google.lib;",
            "import org.junit.Test;",
            "import org.junit.runner.RunWith;",
            "import org.junit.runners.JUnit4;",
            "@RunWith(JUnit4.class)",
            "public class JavaClass2 {",
            "  @Test",
            "  public void testMethod() {}",
            "}");
    PsiClass javaClass1 = ((PsiClassOwner) javaFile1).getClasses()[0];

    PsiMethod class1Method1 = javaClass1.findMethodsByName("testMethod1", false)[0];
    Location<?> class1Location1 =
        new PsiMemberParameterizedLocation(getProject(), class1Method1, javaClass1, "[param]");

    PsiMethod class1Method2 = javaClass1.findMethodsByName("testMethod2", false)[0];
    Location<?> class1Location2 =
        new PsiMemberParameterizedLocation(getProject(), class1Method2, javaClass1, "[3]");

    PsiClass javaClass2 = ((PsiClassOwner) javaFile2).getClasses()[0];
    PsiMethod class2Method = javaClass2.findMethodsByName("testMethod", false)[0];

    assertThat(
            BlazeJUnitTestFilterFlags.testFilterForClassesAndMethods(
                ImmutableMap.of(
                    javaClass1,
                    ImmutableList.of(class1Location1, class1Location2),
                    javaClass2,
                    ImmutableList.of(new PsiLocation<>(class2Method)))))
        .isEqualTo(
            Joiner.on('|')
                .join(
                    "com.google.lib.JavaClass1#(testMethod1\\[param\\]|testMethod2\\[3\\])$",
                    "com.google.lib.JavaClass2#testMethod$"));
  }
}
