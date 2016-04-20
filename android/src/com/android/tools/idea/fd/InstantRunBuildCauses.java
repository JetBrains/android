/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.fd;

import org.intellij.lang.annotations.Language;

public class InstantRunBuildCauses {
  @Language("HTML") private static String FULL_BUILD_PREFIX = "Performing full build &amp; install: <br>";

  // Reasons for clean build
  public static final String NO_DEVICE = "no device provided at the time of the build";
  public static final String MISMATCHING_TIMESTAMPS = "build timestamp on the device does not match the previous build timestamp on disk";
  public static final String USER_REQUESTED_CLEAN_RERUN = "user requested clean re-run";

  // Reasons for full build
  public static final String MANIFEST_CHANGED =
    FULL_BUILD_PREFIX + "Instant Run detected that one of the AndroidManifest.xml files have changed.";
  public static final String MANIFEST_RESOURCE_CHANGED =
    FULL_BUILD_PREFIX + "Instant Run detected that a resource referenced from the AndroidManifest.xml file has changed";
  public static final String FIRST_INSTALLATION_TO_DEVICE = FULL_BUILD_PREFIX + "First installation on device requires a full build.";
  public static final String COLD_SWAP_REQUIRES_API21 = "Cold swap requires API 21 or above";
  public static final String NO_RUN_AS = "No working run-as";

  // Reasons for cold swap
  public static final String APP_NOT_RUNNING = "App not running";
  public static final String MULTI_PROCESS_APP = "Application uses multiple processes.";
}
