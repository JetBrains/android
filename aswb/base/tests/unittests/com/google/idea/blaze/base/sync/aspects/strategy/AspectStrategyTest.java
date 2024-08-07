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
package com.google.idea.blaze.base.sync.aspects.strategy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AspectStrategy}. */
@RunWith(JUnit4.class)
public class AspectStrategyTest extends BlazeTestCase {

  private static final MockAspectStrategy strategy = new MockAspectStrategy();
  private MockExperimentService experiments;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    experiments = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experiments);
    registerExtensionPoint(OutputGroupsProvider.EP_NAME, OutputGroupsProvider.class);
  }

  @Test
  public void testGenericOutputGroupAlwaysPresent() {
    Set<LanguageClass> activeLanguages = ImmutableSet.of();

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder, ImmutableList.of(OutputGroup.INFO), activeLanguages, /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-info-generic");
  }

  @Test
  public void testNoGenericOutputGroupInResolveOrCompile() {
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.JAVA);

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.RESOLVE),
        activeLanguages,
        /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-resolve-java");

    builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.COMPILE),
        activeLanguages,
        /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-compile-java");
  }

  @Test
  public void testAllPerLanguageOutputGroupsRecognized() {
    Set<LanguageClass> activeLanguages =
        Arrays.stream(LanguageOutputGroup.values())
            .map(lang -> lang.languageClass)
            .collect(Collectors.toSet());

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder, ImmutableList.of(OutputGroup.INFO), activeLanguages, /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-info-generic",
            "intellij-info-java",
            "intellij-info-kt",
            "intellij-info-cpp",
            "intellij-info-android",
            "intellij-info-py",
            "intellij-info-go",
            "intellij-info-js",
            "intellij-info-ts",
            "intellij-info-dart");

    builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.RESOLVE),
        activeLanguages,
        /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-resolve-java",
            "intellij-resolve-kt",
            "intellij-resolve-cpp",
            "intellij-resolve-android",
            "intellij-resolve-py",
            "intellij-resolve-go",
            "intellij-resolve-js",
            "intellij-resolve-ts",
            "intellij-resolve-dart");

    builder = emptyBuilder();
    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.COMPILE),
        activeLanguages,
        /* directDepsOnly= */ false);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-compile-java",
            "intellij-compile-kt",
            "intellij-compile-cpp",
            "intellij-compile-android",
            "intellij-compile-py",
            "intellij-compile-go",
            "intellij-compile-js",
            "intellij-compile-ts",
            "intellij-compile-dart");
  }

  @Test
  public void testDirectDepsOutputGroupsEnabledForJava() {
    BlazeCommand.Builder builder = emptyBuilder();

    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.INFO, OutputGroup.RESOLVE),
        ImmutableSet.of(LanguageClass.JAVA),
        /* directDepsOnly= */ true);

    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-info-generic",
            "intellij-info-java-direct-deps",
            "intellij-resolve-java-direct-deps");
  }

  @Test
  public void testDirectDepsOutputGroupsDisabledForCpp() {
    BlazeCommand.Builder builder = emptyBuilder();

    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.INFO, OutputGroup.RESOLVE),
        ImmutableSet.of(LanguageClass.C),
        /* directDepsOnly= */ true);

    assertThat(getOutputGroups(builder))
        .containsExactly("intellij-info-generic", "intellij-info-cpp", "intellij-resolve-cpp");
  }

  @Test
  public void testDirectDepsExperimentRespected() {
    experiments.setExperimentRaw("sync.allow.requesting.direct.deps", false);
    BlazeCommand.Builder builder = emptyBuilder();

    strategy.addAspectAndOutputGroups(
        builder,
        ImmutableList.of(OutputGroup.INFO, OutputGroup.RESOLVE),
        ImmutableSet.of(LanguageClass.JAVA),
        /* directDepsOnly= */ true);

    assertThat(getOutputGroups(builder))
        .containsExactly("intellij-info-generic", "intellij-info-java", "intellij-resolve-java");
  }

  private static BlazeCommand.Builder emptyBuilder() {
    return BlazeCommand.builder("/usr/bin/blaze", BlazeCommandName.BUILD);
  }

  private static ImmutableList<String> getBlazeFlags(BlazeCommand.Builder builder) {
    ImmutableList<String> args = builder.build().toList();
    return args.subList(3, args.indexOf("--"));
  }

  private static ImmutableList<String> getOutputGroups(BlazeCommand.Builder builder) {
    List<String> blazeFlags = getBlazeFlags(builder);
    assertThat(blazeFlags).hasSize(1);
    String groups = blazeFlags.get(0).substring("--output_groups=".length());
    return ImmutableList.copyOf(groups.split(","));
  }

  private static class MockAspectStrategy extends AspectStrategy {
    private MockAspectStrategy() {
      super(/* aspectSupportsDirectDepsTrimming= */ true);
    }

    @Override
    public String getName() {
      return "MockAspectStrategy";
    }

    @Override
    protected List<String> getAspectFlags() {
      return ImmutableList.of();
    }
  }
}
