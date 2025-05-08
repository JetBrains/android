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
package com.android.tools.idea.gradle.project.sync.model

import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_17_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_1_8_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_21_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import org.gradle.internal.jvm.inspection.JvmVendor.KnownJvmVendor
import org.jetbrains.annotations.SystemIndependent

/**
 * Gradle [Daemon JVM criteria](https://docs.gradle.org/current/userguide/gradle_daemon.html#sec:daemon_jvm_criteria) representation model
 * to allow configuring different option for test projects
 * @param version A Java language version of required JVM to run the build
 * @param vendor A JVM vendor being the known ones [KnownJvmVendor], if not specified Gradle considers all vendors compatible
 * @param autoDetectionEnabled Enables toolchain auto-detection to locate locally matching toolchain
 * @param autoProvisioningEnabled Enables toolchain auto-provisioning to download toolchain given provided download URLs
 * @param customToolchainInstallationsPath A comma-separated list of paths to specific installations being the default all the embedded JDKs
 * @param customToolchainInstallationsEnv A comma-separated list of environment variables of toolchain installations paths
 */
data class GradleDaemonToolchain(
  val version: String,
  val vendor: String? = null,
  val autoDetectionEnabled: Boolean = false,
  val autoProvisioningEnabled: Boolean = false,
  val customToolchainInstallationsPath: List<@SystemIndependent String> =
    listOf(JDK_EMBEDDED_PATH, JDK_21_PATH, JDK_17_PATH, JDK_11_PATH, JDK_1_8_PATH),
  val customToolchainInstallationsEnv: List<String>? = null
)
