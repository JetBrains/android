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

import com.android.builder.model.AaptOptions
import com.android.builder.model.SourceProvider
import com.android.sdklib.AndroidVersion
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

open class TestAndroidModel @JvmOverloads constructor(
  private val applicationId: String = "com.example.test",
  private val minSdkVersion: AndroidVersion? = null,
  private val targetSdkVersion: AndroidVersion? = null,
  private val runtimeMinSdkVersion: AndroidVersion? = null,
  private val allApplicationIds: Set<String> = setOf(applicationId),
  private val defaultSourceProvider: SourceProvider? = null,
  private val activeSourceProviders: List<SourceProvider> = emptyList(),
  private val testSourceProviders: List<SourceProvider> = emptyList(),
  private val allSourceProviders: List<SourceProvider> = emptyList(),
  private val classJarProvider: ClassJarProvider? = null,
  private val overridesManifestPackage: Boolean = false,
  private val debuggable: Boolean = false,
  private val dataBindingEnabled: Boolean = false,
  private val versionCode: Int? = null,
  private val rootDir: VirtualFile? = null,
  private val namespacing: AaptOptions.Namespacing = AaptOptions.Namespacing.DISABLED
) : AndroidModel {

  override fun getDefaultSourceProvider(): SourceProvider = defaultSourceProvider ?: error("defaultSourceProvider not set")
  override fun getActiveSourceProviders(): List<SourceProvider> = activeSourceProviders
  override fun getTestSourceProviders(): List<SourceProvider> = testSourceProviders
  override fun getAllSourceProviders(): List<SourceProvider> = allSourceProviders
  override fun getApplicationId(): String = applicationId
  override fun getAllApplicationIds(): Set<String> = allApplicationIds
  override fun overridesManifestPackage(): Boolean = overridesManifestPackage
  override fun isDebuggable(): Boolean = debuggable
  override fun getMinSdkVersion(): AndroidVersion? = minSdkVersion
  override fun getRuntimeMinSdkVersion(): AndroidVersion? = runtimeMinSdkVersion
  override fun getTargetSdkVersion(): AndroidVersion? = targetSdkVersion
  override fun getVersionCode(): Int? = versionCode
  override fun getDataBindingEnabled(): Boolean = dataBindingEnabled
  override fun getClassJarProvider(): ClassJarProvider = classJarProvider ?: error("classJarProvider not set")
  override fun getNamespacing(): AaptOptions.Namespacing = namespacing

  override fun getRootDir(): VirtualFile = rootDir ?: error("rootDir not set")
  override fun getRootDirPath(): File = VfsUtil.virtualToIoFile(getRootDir())

  override fun isGenerated(file: VirtualFile): Boolean = TODO("not implemented")
  override fun isClassFileOutOfDate(module: Module, fqcn: String, classFile: VirtualFile): Boolean = TODO("not implemented")
}
