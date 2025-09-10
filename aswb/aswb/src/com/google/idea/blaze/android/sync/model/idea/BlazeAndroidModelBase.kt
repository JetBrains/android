/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.model.idea

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.model.AndroidModel
import com.android.tools.lint.detector.api.Desugaring
import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.base.model.BlazeProjectData
import com.google.idea.blaze.base.settings.Blaze
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager
import com.google.idea.blaze.base.sync.libraries.LintCollector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Contains Android-Blaze related state necessary for configuring an IDEA project based on a
 * user-selected build variant.
 */
abstract class BlazeAndroidModelBase protected constructor(
  protected val project: Project,
  rootDirPath: File,
  private val applicationIdFuture: ListenableFuture<String>,
  private val minSdkVersionInt: Int
) : AndroidModel {
  override val applicationId: String
    get() {
    try {
      return applicationIdFuture.get(1, TimeUnit.SECONDS)
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    } catch (e: TimeoutException) {
      Logger.getInstance(BlazeAndroidModelBase::class.java).warn("Application Id not initialized yet", e)
    } catch (e: ExecutionException) {
      Logger.getInstance(BlazeAndroidModelBase::class.java).warn("Application Id not initialized yet", e)
    }
    return uninitializedApplicationId()
  }

  protected abstract fun uninitializedApplicationId(): String
  override val allApplicationIds: Set<String>
    get() = setOf<String>(applicationId)

  override fun overridesManifestPackage() = false

  override val isDebuggable: Boolean
    get() = true

  override val minSdkVersion: AndroidVersion
    get() = AndroidVersion(minSdkVersionInt, null)

  override val runtimeMinSdkVersion: AndroidVersion
    get() = minSdkVersion

  override val targetSdkVersion: AndroidVersion?
    get() = null

  override val desugaring: Set<Desugaring>
    get() = Desugaring.FULL

  override val lintRuleJarsOverride: Iterable<File>?
    get() {
      if (Blaze.getProjectType(project) !== ProjectType.ASPECT_SYNC) {
        return listOf<File>()
      }
      val blazeProjectData: BlazeProjectData? =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData()
      return LintCollector.getLintJars(project, blazeProjectData)
    }
}
