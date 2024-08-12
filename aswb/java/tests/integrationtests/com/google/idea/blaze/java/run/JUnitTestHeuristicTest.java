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
package com.google.idea.blaze.java.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import java.io.File;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link JUnitTestHeuristic}. */
@RunWith(JUnit4.class)
public class JUnitTestHeuristicTest extends BlazeIntegrationTestCase {

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
  public void testMatchesJunit4Annotation() {
    PsiFile psiFile =
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
    File file = new File(psiFile.getVirtualFile().getPath());
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:AllJUnit4Tests")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(new JUnitTestHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testIgnoresJunit4AnnotationIfTargetNameDoesNotMatch() {
    PsiFile psiFile =
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
    File file = new File(psiFile.getVirtualFile().getPath());
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:unrelatedName")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(new JUnitTestHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();
  }

  @Test
  public void testNonJavaFileDoesNotMatch() {
    PsiFile psiFile = workspace.createPsiFile(new WorkspacePath("foo/script_test.sh"));
    File file = new File(psiFile.getVirtualFile().getPath());
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:unrelatedName")
            .setKind("sh_test")
            .build()
            .toTargetInfo();
    assertThat(new JUnitTestHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();
  }

  @Test
  public void testNullPsiFileDoesNotMatch() {
    File file = new File("foo/script_test.sh");
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:unrelatedName")
            .setKind("sh_test")
            .build()
            .toTargetInfo();
    assertThat(new JUnitTestHeuristic().matchesSource(getProject(), target, null, file, null))
        .isFalse();
  }

  @Test
  public void testJunit4SourceDoesNotMatchJunit3TargetName() {
    PsiFile psiFile =
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
    File file = new File(psiFile.getVirtualFile().getPath());
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:AllJUnit3Tests")
            .setKind("java_test")
            .build()
            .toTargetInfo();
    assertThat(new JUnitTestHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();
  }
}
