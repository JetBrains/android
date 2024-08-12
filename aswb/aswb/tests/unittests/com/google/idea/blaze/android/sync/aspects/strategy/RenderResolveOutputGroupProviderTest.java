/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.aspects.strategy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.OutputGroupsProvider;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests to check if {@link RenderResolveOutputGroupProvider} returns the correct output group name
 * when required.
 */
@RunWith(JUnit4.class)
public class RenderResolveOutputGroupProviderTest extends BlazeTestCase {

  private final FakeAspectStrategy aspectStrategy = new FakeAspectStrategy();
  private MockExperimentService experimentService;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    ExtensionPointImpl<OutputGroupsProvider> ep =
        registerExtensionPoint(OutputGroupsProvider.EP_NAME, OutputGroupsProvider.class);
    ep.registerExtension(new RenderResolveOutputGroupProvider());

    experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
  }

  /**
   * Tests intellij-render-resolve-android is included when android language is enabled and
   * "resolve" output group is attached.
   */
  @Test
  public void resolveOutputGroup_androidLanguageEnabled_renderResolveIsIncluded() {
    experimentService.setExperiment(RenderResolveOutputGroupProvider.buildOnSync, true);
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.ANDROID);
    ImmutableSet<OutputGroup> outputGroups = ImmutableSet.of(OutputGroup.RESOLVE);
    BlazeCommand.Builder builder = emptyBuilder();

    aspectStrategy.addAspectAndOutputGroups(builder, outputGroups, activeLanguages, false);
    assertThat(getOutputGroups(builder)).contains("intellij-render-resolve-android");
  }

  /**
   * Tests intellij-render-resolve-android is included when android language is enabled and
   * "compile" output group is attached.
   */
  @Test
  public void compileOutputGroup_androidLanguageEnabled_renderResolveIsIncluded() {
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.ANDROID);
    ImmutableSet<OutputGroup> outputGroups = ImmutableSet.of(OutputGroup.COMPILE);
    BlazeCommand.Builder builder = emptyBuilder();

    aspectStrategy.addAspectAndOutputGroups(builder, outputGroups, activeLanguages, false);
    assertThat(getOutputGroups(builder)).contains("intellij-render-resolve-android");
  }

  /** Tests intellij-render-resolve-android is skipped when android language is not enabled. */
  @Test
  public void androidLanguageDisabled_renderResolveIsNotIncluded() {
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.JAVA, LanguageClass.KOTLIN);
    ImmutableSet<OutputGroup> outputGroups =
        ImmutableSet.of(OutputGroup.INFO, OutputGroup.COMPILE, OutputGroup.RESOLVE);
    BlazeCommand.Builder builder = emptyBuilder();

    aspectStrategy.addAspectAndOutputGroups(builder, outputGroups, activeLanguages, false);
    assertThat(getOutputGroups(builder)).doesNotContain("intellij-render-resolve-android");
  }

  /**
   * Tests intellij-render-resolve-android is skipped when "resolve" and "compile" output groups are
   * not attached.
   */
  @Test
  public void infoOutputGroups_renderResolveIsNotIncluded() {
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.ANDROID);
    ImmutableSet<OutputGroup> outputGroups = ImmutableSet.of(OutputGroup.INFO);
    BlazeCommand.Builder builder = emptyBuilder();

    aspectStrategy.addAspectAndOutputGroups(builder, outputGroups, activeLanguages, false);
    assertThat(getOutputGroups(builder)).doesNotContain("intellij-render-resolve-android");
  }

  private static BlazeCommand.Builder emptyBuilder() {
    return BlazeCommand.builder("/usr/bin/blaze", BlazeCommandName.BUILD);
  }

  private static ImmutableList<String> getOutputGroups(BlazeCommand.Builder builder) {
    List<String> blazeFlags = getBlazeFlags(builder);
    assertThat(blazeFlags).hasSize(1);
    String groups = blazeFlags.get(0).substring("--output_groups=".length());
    return ImmutableList.copyOf(groups.split(","));
  }

  private static ImmutableList<String> getBlazeFlags(BlazeCommand.Builder builder) {
    ImmutableList<String> args = builder.build().toList();
    return args.subList(3, args.indexOf("--"));
  }

  private static class FakeAspectStrategy extends AspectStrategy {
    private FakeAspectStrategy() {
      super(/* aspectSupportsDirectDepsTrimming= */ true);
    }

    @Override
    public String getName() {
      return "FakeAspectStrategy";
    }

    @Override
    protected List<String> getAspectFlags() {
      return ImmutableList.of();
    }
  }
}
