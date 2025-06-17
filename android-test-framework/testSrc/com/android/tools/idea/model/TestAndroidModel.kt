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
package com.android.tools.idea.model

import com.android.sdklib.AndroidVersion
import com.android.tools.lint.detector.api.Desugaring
import com.google.common.collect.ImmutableList
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

open class TestAndroidModel @JvmOverloads constructor(
  override val applicationId: String = "com.example.test",
  minSdkVersion: AndroidVersion? = null,
  override val targetSdkVersion: AndroidVersion? = null,
  runtimeMinSdkVersion: AndroidVersion? = null,
  override val allApplicationIds: Set<String> = setOf(applicationId),
  val overridesManifestPackage: Boolean = false,
  override val isDebuggable: Boolean = false,
  override val namespacing: Namespacing = Namespacing.DISABLED,
  override val desugaring: Set<Desugaring> = Desugaring.DEFAULT,
  override val lintRuleJarsOverride: ImmutableList<File>? = null
) : AndroidModel {

  companion object {
    @JvmStatic fun namespaced(facet: AndroidFacet) = TestAndroidModel(
      namespacing = Namespacing.REQUIRED
    )
    @JvmStatic fun lintRuleJars(lintRuleJars: ImmutableList<File>) = TestAndroidModel(
      lintRuleJarsOverride = lintRuleJars
    )
  }

  override val minSdkVersion = minSdkVersion ?: AndroidVersion(1)
  override val runtimeMinSdkVersion = runtimeMinSdkVersion ?: AndroidVersion(1)
  override fun overridesManifestPackage() = overridesManifestPackage
}
