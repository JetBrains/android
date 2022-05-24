/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea

import org.jetbrains.android.AndroidTestCase

class KotlinPluginTest : AndroidTestCase() {
  
  fun testKotlinBundledVersionFormat() {
    // This is a regression test for when Jetbrains accidentally released a Kotlin plugin with version 1.2.60-release-76,
    // where the "-release-76" suffix should not have been there. This broke some code in Studio; for example, Studio would
    // populate build.gradle with the wrong Kotlin version string, thereby breaking the search for Kotlin plugin artifacts.

    // Kotlin-Compose has dash in the version number
    //assert(!bundledRuntimeVersion().contains('-'))
  }
}