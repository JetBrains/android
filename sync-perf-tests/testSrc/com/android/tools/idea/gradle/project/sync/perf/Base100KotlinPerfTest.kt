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

open class Base100KotlinPerfTestV1 : AbstractGradleSyncPerfTestCase() {
  override val relativePath: String = TestProjectPaths.BASE100_KOTLIN
  override val projectName: String = "Base100Kotlin_V1"
  override val initialDrops: Int = 1
  override val numSamples: Int = 5
}

class Base100KotlinPerfTestV2 : AbstractGradleSyncPerfTestCase() {
  override val relativePath: String = TestProjectPaths.BASE100_KOTLIN
  override val projectName: String = "Base100Kotlin_V2"
  override val initialDrops: Int = 1
  override val numSamples: Int = 5
  override val useModelV2: Boolean = true
}