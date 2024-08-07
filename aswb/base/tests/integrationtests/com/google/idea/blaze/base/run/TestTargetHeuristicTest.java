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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import java.io.File;
import java.time.Instant;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link TestTargetHeuristic}. */
@RunWith(JUnit4.class)
public class TestTargetHeuristicTest extends BlazeIntegrationTestCase {

  @Before
  public final void doSetup() {
    BlazeProjectData blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
  }

  @Test
  public void testTestSizeMatched() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//foo:test1")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test2")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testTargetSourcesMatched() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//foo:test1")
                .setKind("sh_test")
                .addSource(sourceRoot("java/com/bar/OtherTest.java"))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test2")
                .setKind("sh_test")
                .addSource(sourceRoot("java/com/foo/FooTest.java"))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, null);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testTargetNameMatched() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//foo:FirstTest")
                .setKind("sh_test")
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:FooTest")
                .setKind("sh_test")
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, null);
    assertThat(match.label).isEqualTo(Label.create("//foo:FooTest"));
  }

  @Test
  public void testNoMatchFallBackToFirstTarget() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//bar:BarTest")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:OtherTest")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.LARGE);
    assertThat(match.label).isEqualTo(Label.create("//bar:BarTest"));
  }

  @Test
  public void testTargetNameCheckedBeforeTestSize() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//bar:BarTest")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:FooTest")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:FooTest"));
  }

  @Test
  public void testTargetSourcesCheckedBeforeTestSize() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//foo:test1")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .addSource(sourceRoot("java/com/bar/OtherTest.java"))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test2")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .addSource(sourceRoot("java/com/foo/FooTest.java"))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testMostRecentlySyncedTargetPreferred() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder()
                .setLabel("//foo:a")
                .setKind("sh_test")
                .setSyncTime(Instant.now().minusSeconds(5))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:b")
                .setKind("sh_test")
                .setSyncTime(Instant.now())
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:c")
                .setKind("sh_test")
                .setSyncTime(null)
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:b"));
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
