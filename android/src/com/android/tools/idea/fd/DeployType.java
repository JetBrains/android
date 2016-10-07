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

public enum DeployType {
  LEGACY,     // full apk installation when IR is disabled
  FULLAPK,    // full apk installation when IR is enabled
  HOTSWAP,    // hot swap changes (no activity restart)
  WARMSWAP,   // hot swap changes (w/ activity restart)
  SPLITAPK,   // split apk installation as part of cold swap (however, split APKs are currently disabled..)
  DEX,        // cold swap scheme that uses dex files
  NO_CHANGES, // no deploy necessary, no changes from what is already deployed
}
