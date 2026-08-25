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
import com.google.idea.blaze.base.run.DeployedApplicationTargetStore
import com.intellij.openapi.project.Project
import java.io.File

/** Contains Android-Blaze related state necessary for configuring an IDEA project based on a user-selected build variant. */
abstract class BlazeAndroidModelBase
protected constructor(
  protected val project: Project,
  private val minSdkVersionInt: Int,
) : AndroidModel {
  // ASwB does not have modules hence no module-to-application-id mapping.
  override val applicationId: String
    get() = "aswb.workspace"

  protected abstract fun uninitializedApplicationId(): String

  override val allApplicationIds: Set<String>
    get() = project.getService(DeployedApplicationTargetStore::class.java)?.getAllApplicationIds() ?: emptySet()

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

  override val lintRuleJarsOverride: Iterable<File> = listOf()
}
