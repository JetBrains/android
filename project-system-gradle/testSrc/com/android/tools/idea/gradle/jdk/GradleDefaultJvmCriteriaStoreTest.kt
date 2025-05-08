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
package com.android.tools.idea.gradle.jdk

import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.toJvmVendor

class GradleDefaultJvmCriteriaStoreTest : LightPlatformTestCase() {

  override fun tearDown() {
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = null
    super.tearDown()
  }

  fun `test Given undefined default JVM criteria When obtain criteria Then returns null`() {
    assertNull(GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
  }

  fun `test Given stored default JVM criteria with known vendor When obtain criteria Then returns expected criteria`() {
    val storeJvmCriteria = GradleDaemonJvmCriteria("19", "IBM".toJvmVendor())
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = storeJvmCriteria
    assertEquals(storeJvmCriteria, GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
  }

  fun `test Given stored default JVM criteria with unknown vendor When obtain criteria Then returns expected criteria`() {
    val storeJvmCriteria = GradleDaemonJvmCriteria("21", "unknown vendor".toJvmVendor())
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = storeJvmCriteria
    assertEquals(storeJvmCriteria, GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
  }

  fun `test Given stored default JVM criteria with any vendor When obtain criteria Then returns expected criteria`() {
    val storeJvmCriteria = GradleDaemonJvmCriteria("24", null)
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = storeJvmCriteria
    assertEquals(storeJvmCriteria, GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
  }
}