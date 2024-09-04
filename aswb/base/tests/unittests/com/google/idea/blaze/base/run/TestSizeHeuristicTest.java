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
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TestSizeHeuristic}. */
@RunWith(JUnit4.class)
public class TestSizeHeuristicTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    ExtensionPointImpl<TestTargetHeuristic> ep =
        registerExtensionPoint(TestTargetHeuristic.EP_NAME, TestTargetHeuristic.class);
    ep.registerExtension(new TestSizeHeuristic());

    ExtensionPointImpl<Kind.Provider> kindProvider =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    kindProvider.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  @Test
  public void testPredicateMatchingSize() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
            .build()
            .toTargetInfo();
    assertThat(
            new TestSizeHeuristic().matchesSource(project, target, null, source, TestSize.MEDIUM))
        .isTrue();
  }

  @Test
  public void testPredicateDifferentSize() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
            .build()
            .toTargetInfo();
    assertThat(new TestSizeHeuristic().matchesSource(project, target, null, source, TestSize.SMALL))
        .isFalse();
  }

  @Test
  public void testPredicateDefaultToSmallSize() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetInfo target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
            .build()
            .toTargetInfo();
    assertThat(new TestSizeHeuristic().matchesSource(project, target, null, source, null)).isTrue();

    target =
        TargetIdeInfo.builder()
            .setLabel("//foo:test")
            .setKind("sh_test")
            .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
            .build()
            .toTargetInfo();
    assertThat(new TestSizeHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testFilterNoMatchesFallBackToFirstRule() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    ImmutableList<TargetInfo> rules =
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
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.LARGE))
                .build()
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test3")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.ENORMOUS))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            project, null, source, rules, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:test1"));
  }

  @Test
  public void testFilterOneMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    ImmutableList<TargetInfo> rules =
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
            project, null, source, rules, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }

  @Test
  public void testFilterChoosesFirstMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    ImmutableList<TargetInfo> rules =
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
                .toTargetInfo(),
            TargetIdeInfo.builder()
                .setLabel("//foo:test3")
                .setKind("sh_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build()
                .toTargetInfo());
    TargetInfo match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(
            project, null, source, rules, TestSize.SMALL);
    assertThat(match.label).isEqualTo(Label.create("//foo:test2"));
  }
}
