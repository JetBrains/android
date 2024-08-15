/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run;

import com.google.common.base.StandardSystemProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.common.experiments.BoolExperiment;
import java.time.Duration;
import java.time.Instant;

/** Metrics collected during launch events. */
public class LaunchMetrics {
  private static final BoolExperiment launchMetricsEnabled =
      new BoolExperiment("aswb.launch.metrics", true);

  private static final String SANITIZED_USER_NAME =
      Strings.nullToEmpty(StandardSystemProperty.USER_NAME.value());

  // Launch metrics are currently logged using an untyped map of key values.
  // The following constants represent the keys currently in use.
  private static final String KEY_LAUNCH_ID = "launchId";
  private static final String KEY_LAUNCH_METHOD = "launchMethod";
  private static final String KEY_EXECUTOR_ID = "executorId";
  private static final String KEY_TARGET_LABEL = "targetLabel";
  private static final String KEY_NATIVE_DEBUGGING_ENABLED = "nativeDebuggingEnabled";
  private static final String KEY_USES_STUDIO_DEPLOYER = "studioDeployerEnabled";
  private static final String KEY_BUILD_DURATION_MILLIS = "buildDurationMillis";
  private static final String KEY_BLAZE_EXIT_CODE = "blazeExitCode";
  private static final String KEY_DEPLOY_DURATION_MILLIS = "deployDurationMillis";
  private static final String KEY_DEPLOY_STATUS = "deployStatus";

  /**
   * Returns a string suitable for use as a unique launch id.
   *
   * <p>An id is generated just based on the user name and the current time since epoch, where we
   * are basically assuming that the same user hasn't performed two launches from the IDE at the
   * same instant in time.
   */
  public static String newLaunchId() {
    return String.format("%1$s:%2$d", SANITIZED_USER_NAME, Instant.now().toEpochMilli());
  }

  public static void logBuildTime(
      String launchId,
      boolean usesStudioDeployer,
      Duration buildDuration,
      int blazeExitCode,
      ImmutableMap<String, String> additionalMetrics) {
    if (!launchMetricsEnabled.getValue()) {
      return;
    }
    ImmutableMap<String, String> metrics =
        ImmutableMap.<String, String>builder()
            .put(KEY_LAUNCH_ID, launchId)
            .put(KEY_USES_STUDIO_DEPLOYER, Boolean.toString(usesStudioDeployer))
            .put(KEY_BUILD_DURATION_MILLIS, Long.toString(buildDuration.toMillis()))
            .put(KEY_BLAZE_EXIT_CODE, Integer.toString(blazeExitCode))
            .putAll(additionalMetrics)
            .buildOrThrow();
    EventLoggingService.getInstance().logEvent(LaunchMetrics.class, "BuildTiming", metrics);
  }

  public static void logDeploymentTime(String launchId, Duration duration, boolean wasSuccessful) {
    if (!launchMetricsEnabled.getValue()) {
      return;
    }

    ImmutableMap<String, String> metrics =
        ImmutableMap.of(
            KEY_LAUNCH_ID,
            launchId,
            KEY_DEPLOY_DURATION_MILLIS,
            Long.toString(duration.toMillis()),
            KEY_DEPLOY_STATUS,
            Boolean.toString(wasSuccessful));
    EventLoggingService.getInstance().logEvent(LaunchMetrics.class, "DeployTiming", metrics);
  }

  public static void logBinaryLaunch(
      String launchId,
      String launchMethod,
      String executorId,
      String target,
      boolean nativeDebuggingEnabled) {
    if (!launchMetricsEnabled.getValue()) {
      return;
    }

    ImmutableMap<String, String> metrics =
        ImmutableMap.of(
            KEY_LAUNCH_ID,
            launchId,
            KEY_LAUNCH_METHOD,
            launchMethod,
            KEY_EXECUTOR_ID,
            executorId,
            KEY_TARGET_LABEL,
            target,
            KEY_NATIVE_DEBUGGING_ENABLED,
            Boolean.toString(nativeDebuggingEnabled));
    EventLoggingService.getInstance()
        .logEvent(LaunchMetrics.class, "BlazeAndroidBinaryRun", metrics);
  }

  public static void logTestLaunch(String launchId, String launchMethod, String executorId) {
    if (!launchMetricsEnabled.getValue()) {
      return;
    }

    ImmutableMap<String, String> metrics =
        ImmutableMap.of(
            KEY_LAUNCH_ID, launchId, KEY_LAUNCH_METHOD, launchMethod, KEY_EXECUTOR_ID, executorId);
    EventLoggingService.getInstance().logEvent(LaunchMetrics.class, "BlazeAndroidTestRun", metrics);
  }

  private LaunchMetrics() {}
}
