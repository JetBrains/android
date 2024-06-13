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
package com.android.tools.idea.projectsystem.apk

import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.util.PathString
import com.android.projectmodel.ExternalAndroidLibrary
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.CapabilityNotSupported
import com.android.tools.idea.projectsystem.CapabilityStatus
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.ManifestOverrides
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.projectsystem.ScopeType
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

class ApkModuleSystem(override val module: Module): AndroidModuleSystem {
  private val delegate = DefaultModuleSystem(module)

  override val moduleClassFileFinder: ClassFileFinder
    get() = delegate.moduleClassFileFinder

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> =
    delegate.getModuleTemplates(targetDirectory)

  override fun analyzeDependencyCompatibility(dependenciesToAdd: List<GradleCoordinate>): Triple<List<GradleCoordinate>, List<GradleCoordinate>, String> =
    delegate.analyzeDependencyCompatibility(dependenciesToAdd)

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? =
    delegate.getRegisteredDependency(coordinate)

  override fun registerDependency(coordinate: GradleCoordinate) =
    throw UnsupportedOperationException("Cannot register dependencies in ApkModuleSystem")

  override fun registerDependency(coordinate: GradleCoordinate, type: DependencyType) =
    throw UnsupportedOperationException("Cannot register dependencies in ApkModuleSystem")

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus =
    delegate.canGeneratePngFromVectorGraphics()

  override fun getResolveScope(scopeType: ScopeType): GlobalSearchScope =
    delegate.getResolveScope(scopeType)

  override val submodules: Collection<Module> = listOf()

  // TODO: suspiciously, this is "required" early by determineDataBindingMode in LayoutBindingModuleCache.  I think that might be spurious.
  override fun getResolvedDependency(coordinate: GradleCoordinate, scope: DependencyScopeType): GradleCoordinate? = null

  override fun getAndroidLibraryDependencies(scope: DependencyScopeType): Collection<ExternalAndroidLibrary> = listOf()

  override fun getSampleDataDirectory(): PathString? = null

  override fun getOrCreateSampleDataDirectory(): PathString? = null

  override fun getResourceModuleDependencies(): List<Module> = listOf()

  override fun getDirectResourceModuleDependents(): List<Module> = listOf()

  override fun getPackageName(): String? =
    delegate.getPackageName()

  override fun canRegisterDependency(type: DependencyType): CapabilityStatus = CapabilityNotSupported()

  override fun getManifestOverrides(): ManifestOverrides = ManifestOverrides()

  override val isDebuggable: Boolean
    get() = delegate.isDebuggable
}
