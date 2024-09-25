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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectsystem.RenderJarClassFileFinder;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.OutputGroupsProvider;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.util.SystemInfo;

/** Adds output group required by {@link RenderJarClassFileFinder} when it is enabled. */
public class RenderResolveOutputGroupProvider implements OutputGroupsProvider {
  public static final ImmutableSet<String> RESOLVE_OUTPUT_GROUP =
      ImmutableSet.of("intellij-render-resolve-android");

  /**
   * Experiment to toggle render jar generation during syncs. By default, render jars should be
   * built during syncs on Linux but not on Macs, since downloading large render jars can be slow if
   * you are not on the corp network.
   */
  public static final BoolExperiment buildOnSync =
      new BoolExperiment("aswb.build.render.jar.on.sync", SystemInfo.isLinux);

  @Override
  public ImmutableSet<String> getAdditionalOutputGroups(
      OutputGroup outputGroup, ImmutableSet<LanguageClass> activeLanguages) {
    if (!RenderJarClassFileFinder.isEnabled()
        || !activeLanguages.contains(LanguageClass.ANDROID)
        || !getSupportedOutputGroups().contains(outputGroup)) {
      return ImmutableSet.of();
    }

    return RESOLVE_OUTPUT_GROUP;
  }

  /** Returns a set of {@link OutputGroup} for which render jars should be built. */
  private static ImmutableSet<OutputGroup> getSupportedOutputGroups() {
    ImmutableSet.Builder<OutputGroup> builder = ImmutableSet.builder();
    // Always build render jars during blaze build action
    builder.add(OutputGroup.COMPILE);
    // optionally build render jars on sync
    if (buildOnSync.getValue()) {
      builder.add(OutputGroup.RESOLVE);
    }
    return builder.build();
  }
}
