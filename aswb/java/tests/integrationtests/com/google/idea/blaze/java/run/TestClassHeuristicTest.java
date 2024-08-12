/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.psi.PsiFile;
import java.io.File;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link TestClassHeuristic}. */
@RunWith(JUnit4.class)
public class TestClassHeuristicTest extends BlazeIntegrationTestCase {

  @Test
  public void testMatchesQualifiedClassNames() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("java_test")
            .setJavaInfo(JavaIdeInfo.builder().setTestClass("com.google.lib.JavaClass"))
            .build()
            .toTargetInfo();
    assertThat(new TestClassHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isTrue();
  }

  @Test
  public void testDoesNotMatchDifferentClassName() {
    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("com/google/lib/JavaClass.java"),
            "package com.google.lib;",
            "public class JavaClass {}");
    File file = new File(psiFile.getVirtualFile().getPath());

    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("java_test")
            .setJavaInfo(JavaIdeInfo.builder().setTestClass("com.google.lib.OtherClass"))
            .build()
            .toTargetInfo();
    assertThat(new TestClassHeuristic().matchesSource(getProject(), target, psiFile, file, null))
        .isFalse();
  }
}
