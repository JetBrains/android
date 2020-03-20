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
package com.android.tools.idea.nav.safeargs

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

enum class SafeArgsMode {
  /**
   * Safe Args is not enabled for this module.
   */
  NONE,

  /**
   * Safe Args is enabled for this module and will generate Java classes.
   */
  JAVA,

  /**
   * Safe Args is enabled for this module and will generate Kotlin classes.
   */
  KOTLIN,
}

private val SAFE_ARGS_MODE_KEY: Key<SafeArgsMode> = Key.create("SAFE_ARGS_MODE_KEY")
private val SAFE_ARGS_MODE_TRACKER_KEY: Key<SimpleModificationTracker> = KeyWithDefaultValue.create("SAFE_ARGS_MODE_TRACKER_KEY", SimpleModificationTracker())

private fun GradleModuleModel.toSafeArgsMode(): SafeArgsMode {
  when {
    // TODO(b/150497628): Update this logic to use the public plugin name, not the private one
    gradlePlugins.contains("androidx.navigation.safeargs.gradle.SafeArgsKotlinPlugin") -> return SafeArgsMode.KOTLIN
    gradlePlugins.contains("androidx.navigation.safeargs.gradle.SafeArgsJavaPlugin") -> return SafeArgsMode.JAVA
    else -> return SafeArgsMode.NONE
  }
}

/**
 * Updates the value associated with the [SAFE_ARGS_MODE_KEY] and, for convenience, returns it.
 */
private fun AndroidFacet.updateSafeArgsMode(): SafeArgsMode {
  val gradleFacet = GradleFacet.getInstance(module)
  val safeArgsMode = gradleFacet?.gradleModuleModel?.toSafeArgsMode() ?: SafeArgsMode.NONE

  val prevSafeArgsMode = getUserData(SAFE_ARGS_MODE_KEY) ?: SafeArgsMode.NONE
  if (prevSafeArgsMode != safeArgsMode) {
    putUserData(SAFE_ARGS_MODE_KEY, safeArgsMode)
    module.project.getUserData(SAFE_ARGS_MODE_TRACKER_KEY)!!.incModificationCount()
  }

  return safeArgsMode
}

var AndroidFacet.safeArgsMode: SafeArgsMode
  get() {
    if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return SafeArgsMode.NONE

    var safeArgsMode = getUserData(SAFE_ARGS_MODE_KEY)
    if (safeArgsMode == null) {
      val connection = module.messageBus.connect(this)
      connection.subscribe(GradleSyncState.GRADLE_SYNC_TOPIC, object : GradleSyncListener {
        override fun syncSucceeded(project: Project) {
          updateSafeArgsMode()
        }
        override fun syncFailed(project: Project, errorMessage: String) {
          updateSafeArgsMode()
        }
        override fun syncSkipped(project: Project) {
          updateSafeArgsMode()
        }
      })
      safeArgsMode = updateSafeArgsMode()
    }

    return safeArgsMode
  }

  /**
   * Allow tests to set the [SafeArgsMode] directly -- however, this value may get overwritten if
   * testing with a Gradle project. In that case, you should control the mode by applying the
   * appropriate safeargs plugin instead.
   */
  @TestOnly
  set(value) {
    putUserData(SAFE_ARGS_MODE_KEY, value)
  }

/**
 * A project-wide tracker which gets updated whenever [safeArgsMode] is updated on any of its
 * modules.
 */
val Project.safeArgsModeTracker: ModificationTracker
  get() {
    return getUserData(SAFE_ARGS_MODE_TRACKER_KEY)!!
  }

fun AndroidFacet.isSafeArgsEnabled() = safeArgsMode != SafeArgsMode.NONE
