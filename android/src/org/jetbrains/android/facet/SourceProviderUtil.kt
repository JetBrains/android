/*
 * Copyright (C) 2019 The Android Open Source Project
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
@file:JvmName("SourceProviderUtil")
package org.jetbrains.android.facet

import com.android.ide.common.gradle.model.IdeSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderImpl
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceProviders
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

@JvmOverloads
fun createIdeaSourceProviderFromModelSourceProvider(it: IdeSourceProvider, scopeType: ScopeType = ScopeType.MAIN): NamedIdeaSourceProvider {
  return NamedIdeaSourceProviderImpl(
    it.name,
    scopeType,
    VfsUtil.fileToUrl(it.manifestFile),
    javaDirectoryUrls = convertToUrlSet(it.javaDirectories),
    resourcesDirectoryUrls = convertToUrlSet(it.resourcesDirectories),
    aidlDirectoryUrls = convertToUrlSet(it.aidlDirectories),
    renderscriptDirectoryUrls = convertToUrlSet(it.renderscriptDirectories),
    // Even though the model has separate methods to get the C and Cpp directories,
    // they both return the same set of folders. So we combine them here.
    jniDirectoryUrls = convertToUrlSet(it.cDirectories + it.cppDirectories).toSet(),
    jniLibsDirectoryUrls = convertToUrlSet(it.jniLibsDirectories),
    resDirectoryUrls = convertToUrlSet(it.resDirectories),
    assetsDirectoryUrls = convertToUrlSet(it.assetsDirectories),
    shadersDirectoryUrls = convertToUrlSet(it.shadersDirectories),
    mlModelsDirectoryUrls = convertToUrlSet(it.mlModelsDirectories)
  )
}

fun createSourceProvidersForLegacyModule(facet: AndroidFacet): SourceProviders =
  com.android.tools.idea.projectsystem.createSourceProvidersForLegacyModule(facet)

/** Convert a set of IO files into a set of IDEA file urls referring to equivalent virtual files  */
private fun convertToUrlSet(fileSet: Collection<File>): Collection<String> = fileSet.map { VfsUtil.fileToUrl(it) }

