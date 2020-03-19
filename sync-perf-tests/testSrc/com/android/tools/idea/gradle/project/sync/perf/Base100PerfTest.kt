/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.perf

import com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths.BASE100

class Base100PerfTest(useSingleVariantSyncInfrastructure: Boolean, gradleVersion: String?, agpVersion: String?) :
  AbstractGradleSyncPerfTestCase(useSingleVariantSyncInfrastructure, gradleVersion, agpVersion) {
  override val relativePath: String = BASE100
  override val projectName: String = "Base100"
  override val initialDrops: Int = 1
  override val numSamples: Int = 5
}