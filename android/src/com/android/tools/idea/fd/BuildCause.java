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

public enum BuildCause {
  // reasons for clean build
  NO_DEVICE,
  APP_NOT_INSTALLED,
  MISMATCHING_TIMESTAMPS,
  USER_REQUESTED_CLEAN_BUILD,

  // reasons for full build
  API_TOO_LOW_FOR_INSTANT_RUN,
  FIRST_INSTALLATION_TO_DEVICE, // first installation in this Android Studio session
  MANIFEST_RESOURCE_CHANGED,
  FREEZE_SWAP_REQUIRES_API21,
  FREEZE_SWAP_REQUIRES_WORKING_RUN_AS,

  // reasons for forced cold swap build
  APP_NOT_RUNNING,
  APP_USES_MULTIPLE_PROCESSES,

  INCREMENTAL_BUILD
}
