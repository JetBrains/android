/*
 * Copyright (C) 2019 The Android Open Source Project
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
android {
  buildTypes {
    maybeCreate("xyz") {
      applicationIdSuffix("mySuffix")
      buildConfigField("abcd", "efgh", "ijkl")
      consumerProguardFiles("proguard-android.txt", "proguard-rules.pro")
      debuggable(true)
      embedMicroApp(true)
      jniDebuggable(true)
      manifestPlaceholders(mapOf("activityLabel1" to "defaultName1", "activityLabel2" to "defaultName2"))
      minifyEnabled(true)
      multiDexEnabled(true)
      proguardFiles("proguard-android.txt", "proguard-rules.pro")
      pseudoLocalesEnabled(true)
      renderscriptDebuggable(true)
      renderscriptOptimLevel(1)
      resValue("mnop", "qrst", "uvwx")
      shrinkResources(true)
      testCoverageEnabled(true)
      useJack(true)
      versionNameSuffix("abc")
      zipAlignEnabled(true)
    }
  }
}