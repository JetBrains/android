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
  private val manifestFileUrl: String,
  override val javaDirectoryUrls: Collection<String> = emptyList(),
  override val resourcesDirectoryUrls: Collection<String> = emptyList(),
  override val aidlDirectoryUrls: Collection<String> = emptyList(),
  override val renderscriptDirectoryUrls: Collection<String> = emptyList(),
  override val jniDirectoryUrls: Collection<String> = emptyList(),
  override val jniLibsDirectoryUrls: Collection<String> = emptyList(),
  override val resDirectoryUrls: Collection<String> = emptyList(),
  override val assetsDirectoryUrls: Collection<String> = emptyList(),
  override val shadersDirectoryUrls: Collection<String> = emptyList()
) : NamedIdeaSourceProvider {
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


  override val javaDirectories: Collection<VirtualFile> get() = convertUrlSet(javaDirectoryUrls)

  override val resourcesDirectories: Collection<VirtualFile> get() = convertUrlSet(resourcesDirectoryUrls)

  override val aidlDirectories: Collection<VirtualFile> get() = convertUrlSet(aidlDirectoryUrls)

  override val renderscriptDirectories: Collection<VirtualFile> get() = convertUrlSet(renderscriptDirectoryUrls)

  override val jniDirectories: Collection<VirtualFile> get() = convertUrlSet(jniDirectoryUrls)

  override val jniLibsDirectories: Collection<VirtualFile> get() = convertUrlSet(jniLibsDirectoryUrls)

  // TODO: Perform some caching; this method gets called a lot!
  override val resDirectories: Collection<VirtualFile> get() = convertUrlSet(resDirectoryUrls)

  override val assetsDirectories: Collection<VirtualFile> get() = convertUrlSet(assetsDirectoryUrls)

  override val shadersDirectories: Collection<VirtualFile> get() = convertUrlSet(shadersDirectoryUrls)

  /** Convert a set of IDEA file urls into a set of equivalent virtual files  */
  private fun convertUrlSet(fileUrls: Collection<String>): Collection<VirtualFile> {
    val fileManager = VirtualFileManager.getInstance()
    return fileUrls.mapNotNull { fileManager.findFileByUrl(it) }
  }
}

/**
 * An implementation of [IdeaSourceProviderCore] configured with a static set of file urls for each collection and the manifest but still
 * resolving them dynamically so that changes to the set of files that exist at any given moment are picked up.
 */
class IdeaSourceProviderImpl(
  override val scopeType: ScopeType,
  override val manifestFileUrls: Collection<String> = emptyList(),
  override val manifestDirectoryUrls: Collection<String> = emptyList(),
  override val javaDirectoryUrls: Collection<String> = emptyList(),
  override val resourcesDirectoryUrls: Collection<String> = emptyList(),
  override val aidlDirectoryUrls: Collection<String> = emptyList(),
  override val renderscriptDirectoryUrls: Collection<String> = emptyList(),
  override val jniDirectoryUrls: Collection<String> = emptyList(),
  override val jniLibsDirectoryUrls: Collection<String> = emptyList(),
  override val resDirectoryUrls: Collection<String> = emptyList(),
  override val assetsDirectoryUrls: Collection<String> = emptyList(),
  override val shadersDirectoryUrls: Collection<String> = emptyList()
) : IdeaSourceProvider {
  override val manifestFiles: Collection<VirtualFile> get() = convertUrlSet(manifestFileUrls)

  override val manifestDirectories: Collection<VirtualFile> get() = convertUrlSet(manifestDirectoryUrls)

  override val javaDirectories: Collection<VirtualFile> get() = convertUrlSet(javaDirectoryUrls)

  override val resourcesDirectories: Collection<VirtualFile> get() = convertUrlSet(resourcesDirectoryUrls)

  override val aidlDirectories: Collection<VirtualFile> get() = convertUrlSet(aidlDirectoryUrls)

  override val renderscriptDirectories: Collection<VirtualFile> get() = convertUrlSet(renderscriptDirectoryUrls)

  override val jniDirectories: Collection<VirtualFile> get() = convertUrlSet(jniDirectoryUrls)

  override val jniLibsDirectories: Collection<VirtualFile> get() = convertUrlSet(jniLibsDirectoryUrls)

  // TODO: Perform some caching; this method gets called a lot!
  override val resDirectories: Collection<VirtualFile> get() = convertUrlSet(resDirectoryUrls)

  override val assetsDirectories: Collection<VirtualFile> get() = convertUrlSet(assetsDirectoryUrls)

  override val shadersDirectories: Collection<VirtualFile> get() = convertUrlSet(shadersDirectoryUrls)

  /** Convert a set of IDEA file urls into a set of equivalent virtual files  */
  private fun convertUrlSet(fileUrls: Collection<String>): Collection<VirtualFile> {
    val fileManager = VirtualFileManager.getInstance()
    return fileUrls.mapNotNull { fileManager.findFileByUrl(it) }
  }
}

/**
 * A builder to build [IdeaSourceProvider] in a Java-friendly way.
 */
interface NamedIdeaSourceProviderBuilder {
  fun withName(name: String): NamedIdeaSourceProviderBuilder
  fun withScopeType(scopeType: ScopeType): NamedIdeaSourceProviderBuilder
  fun withManifestFileUrl(url: String): NamedIdeaSourceProviderBuilder
  fun withJavaDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withResourcesDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withAidlDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withRenderscriptDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withJniDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withJniLibsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withResDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withAssetsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
  fun withShadersDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder
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
    val resourcesDirectoryUrls: Collection<String> = emptyList(),
    val aidlDirectoryUrls: Collection<String> = emptyList(),
    val renderscriptDirectoryUrls: Collection<String> = emptyList(),
    val jniDirectoryUrls: Collection<String> = emptyList(),
    val jniLibsDirectoryUrls: Collection<String> = emptyList(),
    val resDirectoryUrls: Collection<String> = emptyList(),
    val assetsDirectoryUrls: Collection<String> = emptyList(),
    val shadersDirectoryUrls: Collection<String> = emptyList()
  ) : NamedIdeaSourceProviderBuilder {
    override fun withName(name: String): NamedIdeaSourceProviderBuilder = copy(name = name)

    override fun withScopeType(scopeType: ScopeType): NamedIdeaSourceProviderBuilder = copy(scopeType = scopeType)

    override fun withManifestFileUrl(url: String): NamedIdeaSourceProviderBuilder = copy(manifestFileUrl = url)

    override fun withJavaDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(javaDirectoryUrls = urls)

    override fun withResourcesDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(resourcesDirectoryUrls = urls)

    override fun withAidlDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(aidlDirectoryUrls = urls)

    override fun withRenderscriptDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(renderscriptDirectoryUrls = urls)

    override fun withJniDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(jniDirectoryUrls = urls)

    override fun withJniLibsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(jniLibsDirectoryUrls = urls)

    override fun withResDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(resDirectoryUrls = urls)

    override fun withAssetsDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(assetsDirectoryUrls = urls)

    override fun withShadersDirectoryUrls(urls: Collection<String>): NamedIdeaSourceProviderBuilder = copy(shadersDirectoryUrls = urls)

    override fun build(): NamedIdeaSourceProvider = NamedIdeaSourceProviderImpl(
      name,
      scopeType,
      manifestFileUrl,
      javaDirectoryUrls,
      resourcesDirectoryUrls,
      aidlDirectoryUrls,
      renderscriptDirectoryUrls,
      jniDirectoryUrls,
      jniLibsDirectoryUrls,
      resDirectoryUrls,
      assetsDirectoryUrls,
      shadersDirectoryUrls
    )
  }
}
