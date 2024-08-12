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
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.util.SystemInfo;

/**
 * A utility class that manages the experiment to use studio's built in deployer for deploying apks
 * instead of using the one present in mobile-install.
 */
public class StudioDeployerExperiment {
  /** Indicates if we should deploy via Studio or via MI. */
  private static final FeatureRolloutExperiment useStudioDeployer =
      new FeatureRolloutExperiment("aswb.use.studio.deployer.2");

  /** Returns whether mobile install deployments should happen via the studio deployer. */
  public static boolean isEnabled() {
    // The Studio deployer experiment is specific to local builds on Linux. For other platforms,
    // we'll rely entirely on the new Blaze specific deployment flow.
    if (!SystemInfo.isLinux) {
      return false;
    }

    return useStudioDeployer.isEnabled();
  }

  private StudioDeployerExperiment() {}
}
