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

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.util.toJvmVendor

private const val GRADLE_JVM_VERSION_CRITERIA_KEY = "gradle.jvm.version.criteria"
private const val GRADLE_JVM_VENDOR_CRITERIA_KEY = "gradle.jvm.vendor.criteria"

/**
 * Simple persistence based on [PropertiesComponent] for the default Daemon JVM criteria to run Gradle builds.
 */
object GradleDefaultJvmCriteriaStore {

  var daemonJvmCriteria: GradleDaemonJvmCriteria?
    get() {
      val version = PropertiesComponent.getInstance().getValue(GRADLE_JVM_VERSION_CRITERIA_KEY) ?: return null
      val vendor = PropertiesComponent.getInstance().getValue(GRADLE_JVM_VENDOR_CRITERIA_KEY)?.toJvmVendor()

      return GradleDaemonJvmCriteria(version, vendor)
    }
    set(criteria) {
      PropertiesComponent.getInstance().setValue(GRADLE_JVM_VERSION_CRITERIA_KEY, criteria?.version)
      PropertiesComponent.getInstance().setValue(GRADLE_JVM_VENDOR_CRITERIA_KEY, criteria?.vendor?.rawVendor)
    }
}