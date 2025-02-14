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

/** Holder class for basic information about querysync, e.g. is it enabled? */
public class QuerySync {
  private static final BoolExperiment useAdditionalLibraryProvider =
    new BoolExperiment("query.sync.use.additional.library.provider", false);

  public static final String BUILD_DEPENDENCIES_ACTION_NAME = "Enable analysis";

  public static final BoolExperiment ATTACH_DEP_SRCJARS =
      new BoolExperiment("querysync.attach.dep.srcjars", true);

  private QuerySync() {}

  /**
   * Checks if query sync for new project is enabled via experiment or settings page.
   */
  public static boolean useForNewProjects() {
    return QuerySyncSettings.getInstance().useQuerySync();
  }



  /** Provides library via BazelAdditionalLibraryRootsProvider instead of library table. */
  public static boolean enableBazelAdditionalLibraryRootsProvider() {
    return useAdditionalLibraryProvider.getValue();
  }
}
