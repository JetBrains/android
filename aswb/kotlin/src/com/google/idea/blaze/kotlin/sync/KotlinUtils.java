/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync;

import com.google.idea.blaze.base.ideinfo.KotlinToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.common.experiments.BoolExperiment;
import java.util.Objects;
import javax.annotation.Nullable;

class KotlinUtils {
  private KotlinUtils() {}

  // whether kotlin language support is enabled for internal users
  private static final BoolExperiment blazeKotlinSupport =
      new BoolExperiment("blaze.kotlin.support", true);

  static boolean isKotlinSupportEnabled(WorkspaceType workspaceType) {
    if (!workspaceType.getLanguages().contains(LanguageClass.JAVA)) {
      return false;
    }
    // enable for all external users, behind an experiment for internal users
    return Blaze.defaultBuildSystem().equals(BuildSystemName.Bazel)
        || blazeKotlinSupport.getValue();
  }

  /**
   * Returns {@link KotlinToolchainIdeInfo} from the given {@link TargetMap}, or null if not found
   */
  @Nullable
  static KotlinToolchainIdeInfo findToolchain(TargetMap targets) {
    return targets.targets().stream()
        .map(TargetIdeInfo::getKotlinToolchainIdeInfo)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }
}
