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
import org.junit.Ignore;
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
            new TargetInfo(Label.create("//foo:test1"), "sh_test", TestSize.MEDIUM, null, null),
            new TargetInfo(Label.create("//foo:test2"), "sh_test", TestSize.SMALL, null, null));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label()).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testTargetNameMatched() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            new TargetInfo(Label.create("//foo:FirstTest"), "sh_test"),
            new TargetInfo(Label.create("//foo:FooTest"), "sh_test"));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, null);
    assertThat(match.label()).isEqualTo(Label.create("//foo:FooTest"));
  }

  @Test
  public void testNoMatchFallBackToFirstTarget() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> targets =
        ImmutableList.of(
            new TargetInfo(Label.create("//bar:BarTest"), "sh_test", TestSize.MEDIUM, null, null),
            new TargetInfo(Label.create("//foo:OtherTest"), "sh_test", TestSize.SMALL, null, null));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.LARGE);
    assertThat(match.label()).isEqualTo(Label.create("//bar:BarTest"));
  }

  @Test
  public void testTargetNameCheckedBeforeTestSize() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> targets =
        ImmutableList.of(
            new TargetInfo(Label.create("//bar:BarTest"), "sh_test", TestSize.SMALL, null, null),
            new TargetInfo(Label.create("//foo:FooTest"), "sh_test", TestSize.MEDIUM, null, null));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label()).isEqualTo(Label.create("//foo:FooTest"));
  }

  @Test
  @Ignore("b/466755859")
  public void testTargetSourcesCheckedBeforeTestSize() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            new TargetInfo(Label.create("//foo:test1"), "sh_test", TestSize.SMALL, null, null),
            new TargetInfo(Label.create("//foo:test2"), "sh_test", TestSize.MEDIUM, null, null));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label()).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testMostRecentlySyncedTargetPreferred() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    Collection<TargetInfo> targets =
        ImmutableList.of(
            new TargetInfo(
                Label.create("//foo:a"), "sh_test", null, null, Instant.now().minusSeconds(5)),
            new TargetInfo(Label.create("//foo:b"), "sh_test", null, null, Instant.now()),
            new TargetInfo(Label.create("//foo:c"), "sh_test", null, null, null));
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            getProject(), null, source, targets, TestSize.SMALL);
    assertThat(match.label()).isEqualTo(Label.create("//foo:b"));
  }
}