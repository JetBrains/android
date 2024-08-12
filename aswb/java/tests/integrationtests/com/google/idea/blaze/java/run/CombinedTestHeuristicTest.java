/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.TestTargetHeuristic;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.util.Collection;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link TestTargetHeuristic} combinations. */
@RunWith(JUnit4.class)
public class CombinedTestHeuristicTest extends BlazeIntegrationTestCase {

  @Before
  public final void doSetup() {
    BlazeProjectData blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));

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
  public void testSizeAndJUnit4Combination() {
    Collection<TargetInfo> targets =
        ImmutableList.of(
            createTarget("//foo:SmallJUnit3Tests", TestSize.SMALL),
            createTarget("//foo:MediumJUnit3Tests", TestSize.MEDIUM),
            createTarget("//foo:LargeJUnit3Tests", TestSize.LARGE),
            createTarget("//foo:SmallJUnit4Tests", TestSize.SMALL),
            createTarget("//foo:MediumJUnit4Tests", TestSize.MEDIUM),
            createTarget("//foo:LargeJUnit4Tests", TestSize.LARGE));

    PsiFile psiFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/lib/JavaTest.java"),
            "package com.google.lib;",
            "import org.junit.Test;",
            "import org.junit.runner.RunWith;",
            "import org.junit.runners.JUnit4;",
            "@RunWith(JUnit4.class)",
            "public class JavaTest {",
            "  @Test",
            "  public void testMethod1() {}",
            "  @Test",
            "  public void testMethod2() {}",
            "}");
    File source = new File(psiFile.getVirtualFile().getPath());

    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), psiFile, source, targets, TestSize.LARGE);
    assertThat(match.label).isEqualTo(Label.create("//foo:LargeJUnit4Tests"));

    match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), psiFile, source, targets, TestSize.MEDIUM);
    assertThat(match.label).isEqualTo(Label.create("//foo:MediumJUnit4Tests"));
  }

  private static TargetInfo createTarget(String label, TestSize size) {
    return TargetIdeInfo.builder()
        .setLabel(label)
        .setKind("java_test")
        .setTestInfo(TestIdeInfo.builder().setTestSize(size))
        .build()
        .toTargetInfo();
  }
}
