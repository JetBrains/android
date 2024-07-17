/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build

import com.android.tools.idea.testing.AgpVersionSoftwareEnvironment
import com.android.tools.idea.testing.ModelVersion
import com.intellij.openapi.projectRoots.JavaSdkVersion

enum class AgpVersionInBuildAttributionTest(
  override val agpVersion: String? = null,
  override val gradleVersion: String? = null,
  override val jdkVersion: JavaSdkVersion? = null,
  override val compileSdk: String? = null,
  override val kotlinVersion: String? = null
) : AgpVersionSoftwareEnvironment {
  CURRENT,
  AGP_71_GRADLE_75(agpVersion = "7.1.0", gradleVersion = "7.5", jdkVersion = JavaSdkVersion.JDK_17, compileSdk = "34")
  ;

  override val modelVersion: ModelVersion = ModelVersion.V2
}