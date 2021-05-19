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
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

open class TestAndroidModel @JvmOverloads constructor(
  private val applicationId: String = "com.example.test",
  private val minSdkVersion: AndroidVersion? = null,
  private val targetSdkVersion: AndroidVersion? = null,
  private val runtimeMinSdkVersion: AndroidVersion? = null,
  private val allApplicationIds: Set<String> = setOf(applicationId),
  private val classJarProvider: ClassJarProvider? = null,
  private val overridesManifestPackage: Boolean = false,
  private val debuggable: Boolean = false,
  private val versionCode: Int? = null,
  private val namespacing: Namespacing = Namespacing.DISABLED,
  private val desugaringLevel: Set<Desugaring> = Desugaring.DEFAULT
) : AndroidModel {

  companion object {
    @JvmStatic fun namespaced(facet: AndroidFacet) = TestAndroidModel(
      namespacing = Namespacing.REQUIRED
    )
  }

  override fun getApplicationId(): String = applicationId
  override fun getAllApplicationIds(): Set<String> = allApplicationIds
  override fun overridesManifestPackage(): Boolean = overridesManifestPackage
  override fun isDebuggable(): Boolean = debuggable
  override fun getMinSdkVersion(): AndroidVersion? = minSdkVersion
  override fun getRuntimeMinSdkVersion(): AndroidVersion? = runtimeMinSdkVersion
  override fun getTargetSdkVersion(): AndroidVersion? = targetSdkVersion
  override fun getVersionCode(): Int? = versionCode
  override fun getClassJarProvider(): ClassJarProvider = classJarProvider ?: error("classJarProvider not set")
  override fun getNamespacing(): Namespacing = namespacing
  override fun getDesugaring(): Set<Desugaring> = desugaringLevel
  override fun isGenerated(file: VirtualFile): Boolean = TODO("not implemented")
  override fun isClassFileOutOfDate(module: Module, fqcn: String, classFile: VirtualFile): Boolean = TODO("not implemented")
}
