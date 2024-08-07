/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.util.SystemInfo;

/**
 * A utility class that manages the experiments to use blaze command runners for blaze test
 * invocations on Mac and Linux
 */
public class BlazeCommandRunnerExperiments {
  public static final BoolExperiment USE_SINGLEJAR_FOR_DEBUGGING =
      new BoolExperiment("debug.localtests.singlejar.enable", true);
  public static final BoolExperiment ENABLE_DBIP_ONCORP =
      new BoolExperiment("dbip.cloudtop.pilot.enable", false);
  public static final BoolExperiment ANDROID_JNI_LIBRARY_FORCE_ANDROID =
      new BoolExperiment("debug.localtests.android.jni.library", SystemInfo.isMac);

  private BlazeCommandRunnerExperiments() {}
}
