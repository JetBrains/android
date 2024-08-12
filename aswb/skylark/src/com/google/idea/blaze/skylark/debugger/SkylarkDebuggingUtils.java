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
package com.google.idea.blaze.skylark.debugger;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;

/** Helper methods for skylark debugging. */
public final class SkylarkDebuggingUtils {
  private SkylarkDebuggingUtils() {}

  // the most recent backwards-incompatible change to the protocol
  private static final long EARLIEST_SUPPORTED_BLAZE_CL = 202705882L;

  private static final BoolExperiment debuggingEnabled =
      new BoolExperiment("skylark.debugging.enabled", true);

  public static boolean debuggingEnabled(Project project) {
    if (Blaze.getProjectType(project).equals(ProjectType.UNKNOWN)) {
      return false;
    }

    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      // Skylark debugging only needs a blaze version past EARLIEST_SUPPORTED_BLAZE_CL, which
      // greatly predates query sync
      return true;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return projectData != null && debuggingEnabled(projectData.getBlazeVersionData());
  }

  public static boolean debuggingEnabled(BlazeVersionData blazeVersion) {
    if (!debuggingEnabled.getValue()) {
      return false;
    }
    // available in the earliest-supported version of bazel
    if (blazeVersion.buildSystem() != BuildSystemName.Blaze) {
      return true;
    }
    return !blazeVersion.blazeVersionIsKnown()
        || blazeVersion.blazeContainsCl(EARLIEST_SUPPORTED_BLAZE_CL);
  }
}
