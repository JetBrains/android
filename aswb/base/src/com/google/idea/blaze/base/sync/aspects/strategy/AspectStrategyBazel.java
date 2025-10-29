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
package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

/** Aspect strategy for Bazel, where the aspect is situated in an external repository. */
public class AspectStrategyBazel extends AspectStrategy {
  private final String aspectFlag;
  private final String repositoryFlag;

  static final class Provider implements AspectStrategyProvider {
    @Override
    @Nullable
    public AspectStrategy getStrategy(BlazeVersionData versionData) {
      return versionData.buildSystem() == BuildSystemName.Bazel
          ? new AspectStrategyBazel(versionData)
          : null;
    }
  }

  @VisibleForTesting
  public AspectStrategyBazel(BlazeVersionData versionData) {
    super(/* aspectSupportsDirectDepsTrimming= */ true);
    if (versionData.bazelIsAtLeastVersion(6, 0, 0) && !versionData.bazelIsAtLeastVersion(8, 0, 0)) {
      // Bazel 8+ uses --inject_repository and requires single @.
      aspectFlag = "--aspects=@@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect";
    } else {
      aspectFlag = "--aspects=@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect";
    }
    if (versionData.bazelIsAtLeastVersion(8, 0, 0)) {
      repositoryFlag = "--inject_repository=intellij_aspect";
    } else {
      repositoryFlag = "--override_repository=intellij_aspect";
    }
  }

  @VisibleForTesting
  public String getAspectFlag() {
    return aspectFlag;
  }

  @Override
  public String getName() {
    return "AspectStrategySkylarkBazel";
  }

  @Override
  protected List<String> getAspectFlags() {
    return ImmutableList.of(aspectFlag, getAspectRepositoryOverrideFlag());
  }

  private static File findAspectDirectory() {
    return new File(
        PluginManager.getPluginByClass(AspectStrategy.class).getPluginPath().toFile(),
        "aspect");
  }

  private String getAspectRepositoryOverrideFlag() {
    return repositoryFlag + "=" + findAspectDirectory().getPath();
  }
}
