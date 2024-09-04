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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TestTargetSourcesHeuristic}. */
@RunWith(JUnit4.class)
public class TestTargetSourcesHeuristicTest extends BlazeTestCase {

  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    BlazeProjectData blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    projectServices.register(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));

    ExtensionPointImpl<TestTargetHeuristic> ep =
        registerExtensionPoint(TestTargetHeuristic.EP_NAME, TestTargetHeuristic.class);
    ep.registerExtension(new TestTargetSourcesHeuristic());

    ExtensionPointImpl<Kind.Provider> kindProvider =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    kindProvider.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  @Test
  public void testPredicateNoSources() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    TargetInfo target =
        TargetIdeInfo.builder().setLabel("//foo:test").setKind("sh_test").build().toTargetInfo();
    assertThat(new TestTargetSourcesHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testPredicateNoMatchingSource() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .addSource(sourceRoot("java/com/bar/OtherTest.java"))
            .build()
            .toTargetInfo();
    assertThat(new TestTargetSourcesHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testPredicateMatchingSource() {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .addSource(sourceRoot("java/com/bar/OtherTest.java"))
            .addSource(sourceRoot("java/com/foo/FooTest.java"))
            .build()
            .toTargetInfo();
    assertThat(new TestTargetSourcesHeuristic().matchesSource(project, target, null, source, null))
        .isTrue();
  }

  @Test
  public void testFilterNoMatchesFallBackToFirstRule() throws Exception {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> rules =
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
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(project, null, source, rules, null);
    assertThat(match.label).isEqualTo(Label.create("//foo:test1"));
  }

  @Test
  public void testFilterOneMatch() throws Exception {
    File source = workspaceRoot.fileForPath(new WorkspacePath("java/com/foo/FooTest.java"));
    ImmutableList<TargetInfo> rules =
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
        TestTargetHeuristic.chooseTestTargetForSourceFile(project, null, source, rules, null);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
