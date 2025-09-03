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

import com.google.idea.blaze.base.project.BaseQuerySyncConversionUtility;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.application.ApplicationManager;

/** Holder class for basic information about querysync, e.g. is it enabled? */
public class QuerySync {
  public static final String BUILD_DEPENDENCIES_ACTION_NAME = "Enable analysis";
  private static final BoolExperiment TEMPORARY_REENABLE_LEGACY_SYNC =
    new BoolExperiment("querysync.temporary.reenable.legacy.sync", false);

  public static final BoolExperiment ATTACH_DEP_SRCJARS =
      new BoolExperiment("querysync.attach.dep.srcjars", true);

  private QuerySync() {}

  /**
   * Checks if query sync for new project is enabled via experiment or settings page or Query-Sync auto-convert experiment is set.
   */
  public static boolean useForNewProjects() {
    if (!legacySyncIsReenabled()) return true;
    return QuerySyncSettings.getInstance().useQuerySync()
           || BaseQuerySyncConversionUtility.AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled();
  }

  public static boolean syncModeSelectionEnabled() {
    return legacySyncIsReenabled() && !BaseQuerySyncConversionUtility.AUTO_CONVERT_LEGACY_SYNC_TO_QUERY_SYNC_EXPERIMENT.isEnabled();
  }

  public static boolean legacySyncEnabled() {
    return legacySyncIsReenabled();
  }

  private static boolean legacySyncIsReenabled() {
    return ApplicationManager.getApplication().isUnitTestMode() || TEMPORARY_REENABLE_LEGACY_SYNC.getValue();
  }
}
