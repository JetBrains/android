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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.base.scope.BlazeContext;

/** Builds the APK and optionally installs the APK. */
public interface ApkBuildStep {
  /**
   * Builds and optionally installs the APK. Errors and messages are bubbled up to the caller via
   * the given {@link BlazeContext}.
   */
  void build(BlazeContext context, BlazeAndroidDeviceSelector.DeviceSession deviceSession);

  /**
   * Returns whether the IDE should deploy the artifacts of the build. This is true in most cases
   * except for mobile-install where the build itself does the deploy.
   */
  default boolean needsIdeDeploy() {
    return true;
  }

  BlazeAndroidDeployInfo getDeployInfo() throws ApkProvisionException;
}
