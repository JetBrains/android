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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * An implementation of [IdeaSourceProvider] configured with a static set of file urls for each collection and the manifest but still
 * resolving them dynamically so that changes to the set of files that exist at any given moment are picked up.
 */
// TODO(solodkyy): Merge with (derive from) IdeaSourceProviderCoreImpl when virtual files are statically resolved from the urls.
class NamedIdeaSourceProviderImpl(
  override val name: String,
  override val scopeType: ScopeType,
  val core: Core
) : NamedIdeaSourceProvider {

  interface Core {
    val manifestFileUrl: String
    val javaDirectoryUrls: Sequence<String>
    val kotlinDirectoryUrls: Sequence<String>
    val resourcesDirectoryUrls: Sequence<String>
    val aidlDirectoryUrls: Sequence<String>
    val renderscriptDirectoryUrls: Sequence<String>
    val jniLibsDirectoryUrls: Sequence<String>
    val resDirectoryUrls: Sequence<String>
    val assetsDirectoryUrls: Sequence<String>
    val shadersDirectoryUrls: Sequence<String>
    val mlModelsDirectoryUrls: Sequence<String>
    val customSourceDirectories: Map<String, Sequence<String>>
    val baselineProfileDirectoryUrls: Sequence<String>
  }

  private val manifestFileUrl: String get() = core.manifestFileUrl
  override val javaDirectoryUrls: Iterable<String> get() = core.javaDirectoryUrls.asIterable()
  override val kotlinDirectoryUrls: Iterable<String> get() = core.kotlinDirectoryUrls.asIterable()
  override val resourcesDirectoryUrls: Iterable<String> get() = core.resourcesDirectoryUrls.asIterable()
  override val aidlDirectoryUrls: Iterable<String> get() = core.aidlDirectoryUrls.asIterable()
  override val renderscriptDirectoryUrls: Iterable<String> get() = core.renderscriptDirectoryUrls.asIterable()
  override val jniLibsDirectoryUrls: Iterable<String> get() = core.jniLibsDirectoryUrls.asIterable()
  override val resDirectoryUrls: Iterable<String> get() = core.resDirectoryUrls.asIterable()
  override val assetsDirectoryUrls: Iterable<String> get() = core.assetsDirectoryUrls.asIterable()
  override val shadersDirectoryUrls: Iterable<String> get() = core.shadersDirectoryUrls.asIterable()
  override val mlModelsDirectoryUrls: Iterable<String> get() = core.mlModelsDirectoryUrls.asIterable()

  override val custom: Map<String, IdeaSourceProvider.Custom> = core.customSourceDirectories.mapValues { CustomImpl(it.value) }
  override val baselineProfileDirectoryUrls: Iterable<String> get() = core.baselineProfileDirectoryUrls.asIterable()

  @Volatile
  private var myManifestFile: VirtualFile? = null

  private val manifestFile: VirtualFile?
    get() {
      if (myManifestFile == null || !myManifestFile!!.isValid) {
        myManifestFile = VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)
      }
      return myManifestFile
    }

  private val manifestDirectory: VirtualFile?
    get() = VirtualFileManager.getInstance().findFileByUrl(manifestDirectoryUrl)

  private val manifestDirectoryUrl: String
    get() = VfsUtil.getParentDir(manifestFileUrl) ?: error("Invalid manifestFileUrl: $manifestFileUrl")

  override val manifestFileUrls: Collection<String>
    get() = listOf(manifestFileUrl)

  override val manifestFiles: Collection<VirtualFile>
    get() = listOfNotNull(manifestFile)

  override val manifestDirectoryUrls: Collection<String>
    get() = listOf(manifestDirectoryUrl)

  override val manifestDirectories: Collection<VirtualFile>
    get() = listOfNotNull(manifestDirectory)

  override val javaDirectories: Iterable<VirtualFile> get() = core.javaDirectoryUrls.toVirtualFiles()
  override val kotlinDirectories: Iterable<VirtualFile> get() = core.kotlinDirectoryUrls.toVirtualFiles()
  override val resourcesDirectories: Iterable<VirtualFile> get() = core.resourcesDirectoryUrls.toVirtualFiles()
  override val aidlDirectories: Iterable<VirtualFile> get() = core.aidlDirectoryUrls.toVirtualFiles()
  override val renderscriptDirectories: Iterable<VirtualFile> get() = core.renderscriptDirectoryUrls.toVirtualFiles()
  override val jniLibsDirectories: Iterable<VirtualFile> get() = core.jniLibsDirectoryUrls.toVirtualFiles()
  override val resDirectories: Iterable<VirtualFile> get() = core.resDirectoryUrls.toVirtualFiles()
  override val assetsDirectories: Iterable<VirtualFile> get() = core.assetsDirectoryUrls.toVirtualFiles()
  override val shadersDirectories: Iterable<VirtualFile> get() = core.shadersDirectoryUrls.toVirtualFiles()
  override val mlModelsDirectories: Iterable<VirtualFile> get() = core.mlModelsDirectoryUrls.toVirtualFiles()
  override val baselineProfileDirectories: Iterable<VirtualFile> get() = core.baselineProfileDirectoryUrls.toVirtualFiles()
  override fun toString(): String = "$name($scopeType)"
}

/**
 * An implementation of [IdeaSourceProviderCore] configured with a static set of file urls for each collection and the manifest but still
 * resolving them dynamically so that changes to the set of files that exist at any given moment are picked up.
 */
class IdeaSourceProviderImpl(
  override val scopeType: ScopeType,
  val core: Core
) : IdeaSourceProvider {

  interface Core {
    val manifestFileUrls: Sequence<String>
    val manifestDirectoryUrls: Sequence<String>
    val javaDirectoryUrls: Sequence<String>
    val kotlinDirectoryUrls: Sequence<String>
    val resourcesDirectoryUrls: Sequence<String>
    val aidlDirectoryUrls: Sequence<String>
    val renderscriptDirectoryUrls: Sequence<String>
    val jniLibsDirectoryUrls: Sequence<String>
    val resDirectoryUrls: Sequence<String>
    val assetsDirectoryUrls: Sequence<String>
    val shadersDirectoryUrls: Sequence<String>
    val mlModelsDirectoryUrls: Sequence<String>
    val customSourceDirectories: Map<String, Sequence<String>>
    val baselineProfileDirectoryUrls: Sequence<String>
  }

  override val manifestFileUrls: Iterable<String> get() = core.manifestFileUrls.asIterable()
  override val manifestDirectoryUrls: Iterable<String> get() = core.manifestDirectoryUrls.asIterable()
  override val javaDirectoryUrls: Iterable<String> get() = core.javaDirectoryUrls.asIterable()
  override val kotlinDirectoryUrls: Iterable<String> get() = core.kotlinDirectoryUrls.asIterable()
  override val resourcesDirectoryUrls: Iterable<String> get() = core.resourcesDirectoryUrls.asIterable()
  override val aidlDirectoryUrls: Iterable<String> get() = core.aidlDirectoryUrls.asIterable()
  override val renderscriptDirectoryUrls: Iterable<String> get() = core.renderscriptDirectoryUrls.asIterable()
  override val jniLibsDirectoryUrls: Iterable<String> get() = core.jniLibsDirectoryUrls.asIterable()
  override val resDirectoryUrls: Iterable<String> get() = core.resDirectoryUrls.asIterable()
  override val assetsDirectoryUrls: Iterable<String> get() = core.assetsDirectoryUrls.asIterable()
  override val shadersDirectoryUrls: Iterable<String> get() = core.shadersDirectoryUrls.asIterable()
  override val mlModelsDirectoryUrls: Iterable<String> get() = core.mlModelsDirectoryUrls.asIterable()
  override val baselineProfileDirectoryUrls: Iterable<String> get() = core.baselineProfileDirectoryUrls.asIterable()

  override val manifestFiles: Iterable<VirtualFile> get() = core.manifestFileUrls.toVirtualFiles()
  override val manifestDirectories: Iterable<VirtualFile> get() = core.manifestDirectoryUrls.toVirtualFiles()
  override val javaDirectories: Iterable<VirtualFile> get() = core.javaDirectoryUrls.toVirtualFiles()
  override val kotlinDirectories: Iterable<VirtualFile> get() = core.kotlinDirectoryUrls.toVirtualFiles()
  override val resourcesDirectories: Iterable<VirtualFile> get() = core.resourcesDirectoryUrls.toVirtualFiles()
  override val aidlDirectories: Iterable<VirtualFile> get() = core.aidlDirectoryUrls.toVirtualFiles()
  override val renderscriptDirectories: Iterable<VirtualFile> get() = core.renderscriptDirectoryUrls.toVirtualFiles()
  override val jniLibsDirectories: Iterable<VirtualFile> get() = core.jniLibsDirectoryUrls.toVirtualFiles()
  override val resDirectories: Iterable<VirtualFile> get() = core.resDirectoryUrls.toVirtualFiles()
  override val assetsDirectories: Iterable<VirtualFile> get() = core.assetsDirectoryUrls.toVirtualFiles()
  override val shadersDirectories: Iterable<VirtualFile> get() = core.shadersDirectoryUrls.toVirtualFiles()
  override val mlModelsDirectories: Iterable<VirtualFile> get() = core.mlModelsDirectoryUrls.toVirtualFiles()
  override val custom: Map<String, IdeaSourceProvider.Custom> = core.customSourceDirectories.mapValues { CustomImpl(it.value) }
  override val baselineProfileDirectories: Iterable<VirtualFile> get() = core.baselineProfileDirectoryUrls.toVirtualFiles()
}

/**
 * A builder to build [IdeaSourceProvider] in a Java-friendly way.
 */
interface NamedIdeaSourceProviderBuilder {
  fun withName(name: String): NamedIdeaSourceProviderBuilder
  fun withScopeType(scopeType: ScopeType): NamedIdeaSourceProviderBuilder
  fun withManifestFileUrl(url: String): NamedIdeaSourceProviderBuilder
  fun withJavaDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withKotlinDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withResourcesDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withAidlDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withRenderscriptDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withJniDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withJniLibsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withResDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withAssetsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withShadersDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withMlModelsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withCustomSourceDirectories(customSourceDirectories: Map<String, Collection<String>>) : NamedIdeaSourceProviderBuilder
  fun withBaselineProfileDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder

  fun build(): NamedIdeaSourceProvider

  companion object {
    @JvmStatic
    fun create(name: String, manifestUrl: String): NamedIdeaSourceProviderBuilder = Builder(name = name, manifestFileUrl = manifestUrl)
  }

  private data class Builder(
    val name: String,
    val scopeType: ScopeType = ScopeType.MAIN,
    val manifestFileUrl: String,
    val javaDirectoryUrls: Collection<String> = emptyList(),
    val kotlinDirectoryUrls: Collection<String> = emptyList(),
    val resourcesDirectoryUrls: Collection<String> = emptyList(),
    val aidlDirectoryUrls: Collection<String> = emptyList(),
    val renderscriptDirectoryUrls: Collection<String> = emptyList(),
    val jniDirectoryUrls: Collection<String> = emptyList(),
    val jniLibsDirectoryUrls: Collection<String> = emptyList(),
    val resDirectoryUrls: Collection<String> = emptyList(),
    val assetsDirectoryUrls: Collection<String> = emptyList(),
    val shadersDirectoryUrls: Collection<String> = emptyList(),
    val mlModelsDirectoryUrls: Collection<String> = emptyList(),
    val customSourceDirectories: Map<String, Collection<String>> = emptyMap(),
    val baselineProfileDirectoryUrls: Collection<String> = emptyList(),
  ) : NamedIdeaSourceProviderBuilder {
    override fun withName(name: String): NamedIdeaSourceProviderBuilder = copy(name = name)
    override fun withScopeType(scopeType: ScopeType): NamedIdeaSourceProviderBuilder = copy(scopeType = scopeType)
    override fun withManifestFileUrl(url: String): NamedIdeaSourceProviderBuilder = copy(manifestFileUrl = url)
    override fun withJavaDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(javaDirectoryUrls = urls)
    override fun withKotlinDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(kotlinDirectoryUrls = urls)
    override fun withResourcesDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(resourcesDirectoryUrls = urls)
    override fun withAidlDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(aidlDirectoryUrls = urls)
    override fun withRenderscriptDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(renderscriptDirectoryUrls = urls)
    override fun withJniDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(jniDirectoryUrls = urls)
    override fun withJniLibsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(jniLibsDirectoryUrls = urls)
    override fun withResDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(resDirectoryUrls = urls)
    override fun withAssetsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(assetsDirectoryUrls = urls)
    override fun withShadersDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(shadersDirectoryUrls = urls)
    override fun withMlModelsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(mlModelsDirectoryUrls = urls)
    override fun withCustomSourceDirectories(customSourceDirectories: Map<String, Collection<String>>): NamedIdeaSourceProviderBuilder =
      copy(customSourceDirectories = customSourceDirectories)
    override fun withBaselineProfileDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(baselineProfileDirectoryUrls = urls)
    override fun build(): NamedIdeaSourceProvider = NamedIdeaSourceProviderImpl(
      name,
      scopeType,
      object : NamedIdeaSourceProviderImpl.Core {
        override val manifestFileUrl: String get() = this@Builder.manifestFileUrl
        override val javaDirectoryUrls: Sequence<String> get() = this@Builder.javaDirectoryUrls.asSequence()
        override val kotlinDirectoryUrls: Sequence<String> get() = this@Builder.kotlinDirectoryUrls.asSequence()
        override val resourcesDirectoryUrls: Sequence<String> get() = this@Builder.resourcesDirectoryUrls.asSequence()
        override val aidlDirectoryUrls: Sequence<String> get() = this@Builder.aidlDirectoryUrls.asSequence()
        override val renderscriptDirectoryUrls: Sequence<String> get() = this@Builder.renderscriptDirectoryUrls.asSequence()
        override val jniLibsDirectoryUrls: Sequence<String> get() = this@Builder.jniLibsDirectoryUrls.asSequence()
        override val resDirectoryUrls: Sequence<String> get() = this@Builder.resDirectoryUrls.asSequence()
        override val assetsDirectoryUrls: Sequence<String> get() = this@Builder.assetsDirectoryUrls.asSequence()
        override val shadersDirectoryUrls: Sequence<String> get() = this@Builder.shadersDirectoryUrls.asSequence()
        override val mlModelsDirectoryUrls: Sequence<String> get() = this@Builder.mlModelsDirectoryUrls.asSequence()
        override val customSourceDirectories: Map<String, Sequence<String>> = this@Builder.customSourceDirectories.mapValues { it.value.asSequence() }
        override val baselineProfileDirectoryUrls: Sequence<String> get() = this@Builder.baselineProfileDirectoryUrls.asSequence()
      }
    )
  }
}

private class CustomImpl(private val _directoryUrls: Sequence<String>) : IdeaSourceProvider.Custom {
  override val directoryUrls: Iterable<String> get() = _directoryUrls.asIterable()
  override val directories: Iterable<VirtualFile> get() = _directoryUrls.toVirtualFiles()
}

/** Convert a set of IDEA file urls into a set of equivalent virtual files  */
private fun Sequence<String>.toVirtualFiles(): Iterable<VirtualFile> {
  val fileManager = VirtualFileManager.getInstance()
  return mapNotNull {
    ProgressManager.checkCanceled()
    fileManager.findFileByUrl(it)
  }.asIterable()
}
