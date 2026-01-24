/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.idea.blaze.android.run

import com.android.ddmlib.IDevice
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApplicationIdProvider
import com.android.tools.idea.run.ConsoleProvider
import com.android.tools.idea.run.editor.ProfilerState
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo
import com.intellij.execution.Executor
import com.intellij.openapi.project.Project
import java.io.File

/** Holds the context data required to run an Android application.  */
class BazelAndroidRunContext(
  val consoleProvider: ConsoleProvider,
  val deployInfo: BlazeAndroidDeployInfo,
  val applicationIdProvider: BazelApplicationIdProvider,
  val apkProvider: BazelApkProvider,
  val applicationProjectContext: BazelApplicationProjectContext,
  val executor: Executor,
  val profileState: ProfilerState?
)

/** Holds the pre-calculated application IDs for a launched app.  */
class BazelApplicationIdProvider(
  private val packageName: String,
  private val testPackageName: String?
) : ApplicationIdProvider {
  /**
   * Returns the application ID of the main app (for android_binary targets) or the app under test
   * (for android_test targets).
   */
  override fun getPackageName(): String = packageName

  /** Returns the application ID of the test instrumentation APK, or null if none.  */
  override fun getTestPackageName(): String? = testPackageName
}

/** Apk provider from deploy info proto  */
class BazelApkProvider(
  val apkInfos: List<ApkInfo>,
  val symbolFiles: List<File>
) : ApkProvider {
  override fun getApks(device: IDevice): Collection<ApkInfo> {
    return apkInfos
  }
}

/**
 * An implementation of [ApplicationProjectContext] used in the Bazel project system.
 *
 * **Note:** The Bazel project system assumes all instances of the [ApplicationProjectContext] associated with its projects to be backed by
 * this specific class.
 */
class BazelApplicationProjectContext(val project: Project, override val applicationId: String) : ApplicationProjectContext