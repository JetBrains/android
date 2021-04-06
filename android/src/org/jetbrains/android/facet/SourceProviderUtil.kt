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

import com.android.tools.idea.gradle.model.IdeSourceProvider
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
    core = object : NamedIdeaSourceProviderImpl.Core {
      override val manifestFileUrl: String get() = VfsUtil.fileToUrl(it.manifestFile)
      override val javaDirectoryUrls: Sequence<String> get() = it.javaDirectories.asSequence().toUrls()
      override val kotlinDirectoryUrls: Sequence<String> get() = it.kotlinDirectories.asSequence().toUrls()
      override val resourcesDirectoryUrls: Sequence<String> get() = it.resourcesDirectories.asSequence().toUrls()
      override val aidlDirectoryUrls: Sequence<String> get() = it.aidlDirectories.asSequence().toUrls()
      override val renderscriptDirectoryUrls: Sequence<String> get() = it.renderscriptDirectories.asSequence().toUrls()
      override val jniLibsDirectoryUrls: Sequence<String> get() = it.jniLibsDirectories.asSequence().toUrls()
      override val resDirectoryUrls: Sequence<String> get() = it.resDirectories.asSequence().toUrls()
      override val assetsDirectoryUrls: Sequence<String> get() = it.assetsDirectories.asSequence().toUrls()
      override val shadersDirectoryUrls: Sequence<String> get() = it.shadersDirectories.asSequence().toUrls()
      override val mlModelsDirectoryUrls: Sequence<String> get() = it.mlModelsDirectories.asSequence().toUrls()
    }
  )
}

fun createSourceProvidersForLegacyModule(facet: AndroidFacet): SourceProviders =
  com.android.tools.idea.projectsystem.createSourceProvidersForLegacyModule(facet)

/** Convert a set of IO files into a set of IDEA file urls referring to equivalent virtual files  */
private fun Sequence<File>.toUrls(): Sequence<String> = map { VfsUtil.fileToUrl(it) }

