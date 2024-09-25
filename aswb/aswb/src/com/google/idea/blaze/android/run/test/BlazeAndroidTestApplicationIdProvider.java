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
package com.google.idea.blaze.android.run.test;

import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.ApplicationIdProvider;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.runner.ApkBuildStep;

/** Application id provider for android_test and android_instrumentation_test. */
public class BlazeAndroidTestApplicationIdProvider implements ApplicationIdProvider {
  private final ApkBuildStep buildStep;

  BlazeAndroidTestApplicationIdProvider(ApkBuildStep buildStep) {
    this.buildStep = buildStep;
  }

  /** Returns the package name of the target APK under test. */
  @Override
  public String getPackageName() throws ApkProvisionException {
    BlazeAndroidDeployInfo deployInfo = buildStep.getDeployInfo();
    ManifestParser.ParsedManifest parsedManifest = deployInfo.getTestTargetMergedManifest();
    if (parsedManifest == null) {
      // The test instrumentor may not have a separate package,
      // and can instead be in the same package as the test target package.
      return getTestPackageName();
    }
    if (parsedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in test target manifest.");
    }
    return parsedManifest.packageName;
  }

  /** Returns the package name of the test instrumentor. */
  @Override
  public String getTestPackageName() throws ApkProvisionException {
    BlazeAndroidDeployInfo deployInfo = buildStep.getDeployInfo();
    ManifestParser.ParsedManifest parsedManifest = deployInfo.getMergedManifest();
    if (parsedManifest.packageName == null) {
      throw new ApkProvisionException("No application id in test instrumentor manifest");
    }
    return parsedManifest.packageName;
  }
}
