/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import java.util.function.Supplier;

/** Holder class for basic information about querysync, e.g. is it enabled? */
public class QuerySync {

  public static final String BUILD_DEPENDENCIES_ACTION_NAME = "Enable analysis";

  public static final BoolExperiment USE_NEW_RES_DIR_LOGIC =
      new BoolExperiment("query.sync.new.resdir.logic", true);

  public static final BoolExperiment EXTRACT_RES_PACKAGES_AT_BUILD_TIME =
      new BoolExperiment("query.sync.respackages.at.build.time", true);

  public static final BoolExperiment ATTACH_DEP_SRCJARS =
      new BoolExperiment("querysync.attach.dep.srcjars", true);

  private QuerySync() {}

  /**
   * Checks if query sync for new project is enabled via experiment or settings page.
   */
  public static boolean useForNewProjects() {
    return QuerySyncSettings.getInstance().useQuerySync();
  }
}
