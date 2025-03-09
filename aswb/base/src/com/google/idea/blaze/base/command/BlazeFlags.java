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
package com.google.idea.blaze.base.command;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.TestFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import java.util.List;

/** The collection of all the Bazel flag strings we use. */
public final class BlazeFlags {
  // Build the maximum number of possible dependencies of the project and to show all the build
  // errors in single go.
  public static final String KEEP_GOING = "--keep_going";
  // Tells Blaze to open a debug port and wait for a connection while running tests
  // It expands to: --test_arg=--wrapper_script_flag=--debug --test_output=streamed
  //   --test_strategy=exclusive --test_timeout=9999 --nocache_test_results
  public static final String JAVA_TEST_DEBUG = "--java_debug";
  // Streams stdout/stderr output from each test in real-time.
  // Implies --test_strategy=exclusive and --test_sharding_strategy=disabled
  public static final String TEST_OUTPUT_STREAMED = "--test_output=streamed";
  // Runs tests locally, in sequence (rather than parallel).
  public static final String EXCLUSIVE_TEST_EXECUTION = "--test_strategy=exclusive";
  // No sharding of tests
  public static final String DISABLE_TEST_SHARDING = "--test_sharding_strategy=disabled";
  // Filters the unit tests that are run (used with regexp for Java/Robolectric tests).
  public static final String TEST_FILTER = "--test_filter";
  // Re-run the test even if the results are cached.
  public static final String NO_CACHE_TEST_RESULTS = "--nocache_test_results";

  public static final String DELETED_PACKAGES = "--deleted_packages";

  // Avoid running validation actions at the end of build. This flag is expected to be set only
  // during syncing projects.
  public static final String DISABLE_VALIDATIONS = "--noexperimental_run_validations";

  // Custom build metadata. This option takes a key-value pair as an argument.
  public static final String BUILD_METADATA = "--build_metadata=";

  /** Flags to add to blaze/bazel invocations of the given type. */
  public static List<String> blazeFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext) {
    List<String> flags = Lists.newArrayList();
    for (BuildFlagsProvider buildFlagsProvider : BuildFlagsProvider.EP_NAME.getExtensions()) {
      buildFlagsProvider.addBuildFlags(project, projectViewSet, command, invocationContext, flags);
    }
    flags.addAll(expandBuildFlags(projectViewSet.listItems(BuildFlagsSection.KEY)));
    if (invocationContext.type() == ContextType.Sync) {
      for (BuildFlagsProvider buildFlagsProvider : BuildFlagsProvider.EP_NAME.getExtensions()) {
        buildFlagsProvider.addSyncFlags(
            project, projectViewSet, command, context, invocationContext, flags);
      }
      flags.addAll(expandBuildFlags(projectViewSet.listItems(SyncFlagsSection.KEY)));
    }
    if (BlazeCommandName.TEST.equals(command)) {
      flags.addAll(expandBuildFlags(projectViewSet.listItems(TestFlagsSection.KEY)));
    }
    return flags;
  }

  public static final String ADB_PATH = "--adb_path";
  public static final String DEVICE = "--device";

  // Pass-through arg for sending test arguments.
  public static final String TEST_ARG = "--test_arg=";

  private static final String TOOL_TAG = "--tool_tag=ijwb:";

  // TODO: remove these when mobile-install V1 is obsolete
  // When used with mobile-install, deploys the an app incrementally.
  public static final String INCREMENTAL = "--incremental";
  // When used with mobile-install, deploys the an app incrementally
  // can be used for API 23 or higher, for which it is preferred to --incremental
  public static final String SPLIT_APKS = "--split_apks";
  // Pass-through arg for sending adb options during mobile-install.
  public static final String ADB_ARG = "--adb_arg=";
  public static final String ADB = "--adb";

  // We add this to every single BlazeCommand instance. It's for tracking usage.
  public static String getToolTagFlag() {
    String platformPrefix = PlatformUtils.getPlatformPrefix();

    // IDEA Community Edition is "Idea", whereas IDEA Ultimate Edition is "idea".
    // That's confusing. Let's make them more useful.
    if (PlatformUtils.isIdeaCommunity()) {
      platformPrefix = "IDEA:community";
    } else if (PlatformUtils.isIdeaUltimate()) {
      platformPrefix = "IDEA:ultimate";
    }
    return TOOL_TAG + platformPrefix;
  }

  /** Expands any macros in the passed build flags. */
  public static List<String> expandBuildFlags(List<String> flags) {
    // This built-in IntelliJ class will do macro expansion using
    // both your environment and your Settings > Behavior > Path Variables
    ParametersList parametersList = new ParametersList();
    parametersList.addAll(flags);
    return parametersList.getList();
  }

  private BlazeFlags() {}
}
