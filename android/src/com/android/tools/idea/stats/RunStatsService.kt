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
package com.android.tools.idea.stats

import com.android.annotations.VisibleForTesting
import com.android.ddmlib.IDevice
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.run.tasks.DynamicAppDeployTaskContext
import com.android.tools.idea.run.tasks.SplitApkDeployTaskContext
import com.google.wireless.android.sdk.stats.StudioRunEvent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly

/**
 * Abstract base class so it can be mocked for testing.
 */
abstract class RunStatsService {
  @VisibleForTesting
  var myRun: Run? = null

  companion object {
    private var ourTestInstance: RunStatsService? = null

    @TestOnly
    @JvmStatic
    fun setTestOverride(testInstance: RunStatsService?) {
      ourTestInstance = testInstance
    }

    @JvmStatic
    fun get(project: Project): RunStatsService {
      if (ourTestInstance != null) return ourTestInstance!!
      return ServiceManager.getService(project, RunStatsServiceImpl::class.java)
    }
  }

  abstract fun notifyRunStarted(packageName: String,
                                runType: String,
                                isDebuggable: Boolean,
                                forceColdswap: Boolean,
                                instantRunEnabled: Boolean)

  abstract fun notifyStudioSectionFinished(isSuccessful: Boolean,
                                           isInstantRun: Boolean,
                                           userSelectedDeployTarget: Boolean)

  abstract fun notifyGradleStarted(buildMode: BuildMode?)

  abstract fun notifyGradleFinished(isSuccessful: Boolean)

  abstract fun notifyEmulatorStarting()

  abstract fun notifyEmulatorStarted(isSuccessful: Boolean)

  fun notifyDeployApkStarted(device: IDevice,
                                   artifactCount: Int) {
    notifyDeployStarted(StudioRunEvent.DeployTask.DEPLOY_APK, device, artifactCount)
  }

  fun notifyDeployInstantAppStarted(device: IDevice,
                             artifactCount: Int) {
    notifyDeployStarted(StudioRunEvent.DeployTask.DEPLOY_INSTANT_APP, device, artifactCount)
  }

  fun notifyDeployHotSwapStarted(device: IDevice,
                             artifactCount: Int) {
    notifyDeployStarted(StudioRunEvent.DeployTask.HOTSWAP, device, artifactCount)
  }

  fun notifyDeploySplitApkStarted(device: IDevice,
                                  context: SplitApkDeployTaskContext, dontKill: Boolean) {
    val disabledFeaturesCount = if (context is DynamicAppDeployTaskContext) {
      context.disabledFeatures.size
    }
    else {
      0
    }
    notifyDeployStarted(StudioRunEvent.DeployTask.SPLIT_APK_DEPLOY, device, context.artifacts.size, context.isPatchBuild, dontKill,
                        disabledFeaturesCount)
  }

  abstract fun notifyDeployStarted(deployTask: StudioRunEvent.DeployTask,
                                   device: IDevice,
                                   artifactCount: Int,
                                   isPatchBuild: Boolean = false,
                                   dontKill: Boolean = false, disabledDynamicFeaturesCount: Int = 0)

  abstract fun notifyDeployFinished(isSuccessful: Boolean)

  abstract fun notifyRunFinished(isSuccessful: Boolean)
}
