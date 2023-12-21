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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.flags.StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_BUILD_SYSTEM_MIN_SDK_VERSION_FOR_DEXING
import com.android.tools.idea.run.deployment.liveedit.LiveEditApp
import com.android.tools.idea.run.deployment.liveedit.desugaring.MinApiLevel
import com.intellij.execution.runners.ExecutionEnvironment
import java.nio.file.Path

class LiveEditHelper {
  fun invokeLiveEdit(
    liveEditService: LiveEditService,
    env: ExecutionEnvironment,
    applicationId: String,
    apkInfos: Collection<ApkInfo>,
    device: IDevice
  ) {
    var buildSystemMinSdkLevels = mutableSetOf<MinApiLevel>()
    if (COMPOSE_DEPLOY_LIVE_EDIT_BUILD_SYSTEM_MIN_SDK_VERSION_FOR_DEXING.get()) {
      apkInfos.forEach { apkInfo ->
        apkInfo.minSdkVersionForDexing?.let { buildSystemMinSdkLevels.add(it) }
      }
    }
    val liveEditApp = LiveEditApp(getApkPaths(apkInfos, device), device.getVersion().getApiLevel(), buildSystemMinSdkLevels)
    liveEditService.notifyAppDeploy(env.runProfile, env.executor, applicationId, device, liveEditApp)

  }

  fun getApkPaths(apkInfos: Collection<ApkInfo>, device: IDevice): Set<Path> {
    val apksPaths: MutableSet<Path> = HashSet()
    for (apkInfo in apkInfos) {
      for (apkFileUnit in apkInfo.files) {
        apksPaths.add(apkFileUnit.apkPath)
      }
    }
    return apksPaths
  }
}