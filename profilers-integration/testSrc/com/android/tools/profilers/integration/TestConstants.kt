/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.profilers.integration

import com.android.tools.asdriver.tests.Emulator

object TestConstants {
  val SYSTEM_IMAGE = Emulator.SystemImage.API_33
  const val MIN_APP_PROJECT_PATH = "tools/adt/idea/profilers-integration/testData/minapp"
  const val MIN_APP_REPO_MANIFEST = "tools/adt/idea/profilers-integration/minapp_deps.manifest"
  const val APK_PROJECT_PATH = "tools/adt/idea/profilers-integration/testData/helloworldapk"
}