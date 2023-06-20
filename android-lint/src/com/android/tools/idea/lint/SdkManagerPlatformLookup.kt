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
package com.android.tools.idea.lint

import com.android.repository.api.ProgressIndicatorAdapter
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.lint.client.api.PlatformLookup

/**
 * Implementation of the [PlatformLookup] interface which is backed by the real SDK manager
 * ([AndroidSdkHandler]). This is primarily used in the IDE, where it's typically already available.
 * Lint only uses a tiny subset of what the SDK manager offers, and it's somewhat expensive to
 * create (performing recursive directory traversals looking for things like system images etc), so
 * there is a different implementation available for lint's purposes as SimplePlatformLookup.
 */
class SdkManagerPlatformLookup(
  private val sdkHandler: AndroidSdkHandler,
  private val logger: ProgressIndicatorAdapter
) : PlatformLookup {
  override fun getLatestSdkTarget(
    minApi: Int,
    includePreviews: Boolean,
    includeAddOns: Boolean
  ): IAndroidTarget? {
    val targets = getTargets(includeAddOns)
    for (i in targets.indices.reversed()) {
      val target = targets[i]
      if (
        (includeAddOns || target.isPlatform) &&
          target.version.featureLevel >= minApi &&
          (includePreviews || target.version.codename == null)
      ) {
        return target
      }
    }

    return null
  }

  override fun getTarget(buildTargetHash: String): IAndroidTarget? {
    if (targets == null) {
      val manager = sdkHandler.getAndroidTargetManager(logger)
      val target = manager.getTargetFromHashString(buildTargetHash, logger)
      if (target != null) {
        return target
      }

      return null
    } else {
      return targets!!.lastOrNull { it.hashString() == buildTargetHash }
    }
  }

  private var targets: List<IAndroidTarget>? = null

  override fun getTargets(includeAddOns: Boolean): List<IAndroidTarget> {
    return targets
      ?: run {
        sdkHandler
          .getAndroidTargetManager(logger)
          .getTargets(logger)
          .filter { includeAddOns || it.isPlatform }
          .toList()
          .also { targets = it }
      }
  }
}
