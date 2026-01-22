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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.activity.StartActivityFlagsProvider;
import com.google.idea.blaze.android.run.runner.BlazeLaunchTask;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.run.binary.tasks.AndroidDeepLinkLaunchTask;
import com.google.idea.blaze.android.run.binary.tasks.BlazeDefaultActivityLaunchTask;
import com.google.idea.blaze.android.run.binary.tasks.SpecificActivityLaunchTask;
import com.google.idea.blaze.android.run.BazelApplicationIdProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;

/** Provides the launch task for android_binary */
public class BlazeAndroidBinaryApplicationLaunchTaskProvider {
  private static final Logger LOG =
      Logger.getInstance(BlazeAndroidBinaryApplicationLaunchTaskProvider.class);

  public static BlazeLaunchTask getApplicationLaunchTask(
      BazelApplicationIdProvider applicationIdProvider,
      ManifestParser.ParsedManifest mergedManifestParsedManifest,
      BlazeAndroidBinaryRunConfigurationState configState,
      StartActivityFlagsProvider startActivityFlagsProvider)
      throws ExecutionException {
    String applicationId = applicationIdProvider.getPackageName();

    switch (configState.getMode()) {
      case BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY:
        BlazeDefaultActivityLocator activityLocator =
            new BlazeDefaultActivityLocator(mergedManifestParsedManifest);
        return new BlazeDefaultActivityLaunchTask(
            applicationId, activityLocator, startActivityFlagsProvider);
      case BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY:
        return launchContext ->
            new SpecificActivityLaunchTask(
                    applicationId, configState.getActivityClass(), startActivityFlagsProvider)
                .run(
                    launchContext.getDevice(),
                    launchContext.getProgressIndicator(),
                    launchContext.getConsoleView());
      case BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEEP_LINK:
        return launchContext ->
            new AndroidDeepLinkLaunchTask(configState.getDeepLink(), startActivityFlagsProvider)
                .run(
                    launchContext.getDevice(),
                    launchContext.getProgressIndicator(),
                    launchContext.getConsoleView());
      default:
        return null;
    }
  }
}