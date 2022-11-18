/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableList.copyOf
import com.google.common.collect.Iterables
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import java.util.Objects
import javax.swing.Icon


/**
 * A source type supported by the Android Gradle Plugin, for display in the Android view
 *
 * Third party gradle plugins can register new custom source directory types, which should
 * also be displayed in the content view
 * (for an example, see `com.android.build.gradle.integration.model.CustomSourceDirectoryTest`)
 */
sealed class AndroidSourceType(
  val name: String,
  val icon: Icon?,
  val isGenerated: Boolean = false,
  private val isCustom: Boolean = false,
) : Comparable<AndroidSourceType> {

  companion object {
    /** The inbuilt types supported by the Android Gradle Plugin.
     *
     * The order here determines the sort order in the UI
     */
    @JvmField
    val BUILT_IN_TYPES: List<AndroidSourceType> =
      listOf(
        MANIFEST,
        JAVA,
        KOTLIN,
        GENERATED_JAVA,
        CPP,
        AIDL,
        RENDERSCRIPT,
        SHADERS,
        ASSETS,
        JNILIBS,
        RES,
        GENERATED_RES,
        RESOURCES,
        ML,
        BASELINE_PROFILES,
      )

    private const val JAVA_NAME = "java"
    private const val RES_NAME = "res"

  }

  abstract fun getSources(provider: IdeaSourceProvider): List<VirtualFile>

  object MANIFEST : AndroidSourceType(
    "manifest",
    AllIcons.Modules.SourceRoot) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.manifestFiles)
  }

  /** Java and Kotlin sources.  */
  object JAVA : AndroidSourceType(
    JAVA_NAME,
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.javaDirectories)
  }

  object KOTLIN : AndroidSourceType(
    "kotlin",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.kotlinDirectories)
  }

  /** Generated java source folders, e.g. R, BuildConfig, and etc.  */
  object GENERATED_JAVA : AndroidSourceType(
    JAVA_NAME,
    AllIcons.Modules.GeneratedSourceRoot,
    isGenerated = true,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> =
      copyOf(Iterables.concat(provider.javaDirectories, provider.kotlinDirectories))
  }

  /** C++ sources  */
  object CPP : AndroidSourceType(
    "cpp",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = ImmutableList.of()
  }

  object AIDL : AndroidSourceType(
    "aidl",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.aidlDirectories)
  }

  object RENDERSCRIPT : AndroidSourceType(
    "renderscript",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.renderscriptDirectories)
  }

  object SHADERS : AndroidSourceType(
    "shaders",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.shadersDirectories)
  }

  object ASSETS : AndroidSourceType(
    "assets",
    AllIcons.Modules.ResourcesRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.assetsDirectories)
  }

  object JNILIBS : AndroidSourceType(
    "jniLibs",
    AllIcons.Modules.ResourcesRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.jniLibsDirectories)
  }

  /** Android resources.  */
  object RES : AndroidSourceType(
    RES_NAME,
    AllIcons.Modules.ResourcesRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.resDirectories)
  }

  /** Generated Android resources, coming from the build system model.  */
  object GENERATED_RES : AndroidSourceType(
    RES_NAME,
    AllIcons.Modules.ResourcesRoot,
    isGenerated = true,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.resDirectories)
  }

  /** Java-style resources.  */
  object RESOURCES : AndroidSourceType(
    "resources",
    AllIcons.Modules.ResourcesRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.resourcesDirectories)
  }

  /** Machine learning models.  */
  object ML : AndroidSourceType(
    "ml",
    AllIcons.Modules.ResourcesRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.mlModelsDirectories)
  }

  object BASELINE_PROFILES : AndroidSourceType(
    "baselineProfiles",
    AllIcons.Modules.SourceRoot,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> = copyOf(provider.baselineProfileDirectories)
  }

  class Custom(name: String) : AndroidSourceType(
    name,
    AllIcons.Modules.ResourcesRoot,
    isGenerated = false,
    isCustom = true,
  ) {
    override fun getSources(provider: IdeaSourceProvider): List<VirtualFile> =
      copyOf(provider.custom[name]?.directories ?: emptyList<VirtualFile>())

  }

  override fun equals(other: Any?): Boolean =
    if (!isCustom) this === other else (other is AndroidSourceType) && other.isCustom && other.name == name

  override fun hashCode(): Int = Objects.hash(name, isGenerated, isCustom)

  override fun toString(): String = when {
    isCustom -> "Custom($name)"
    isGenerated -> "$name (generated)"
    else -> name
  }

  override fun compareTo(other: AndroidSourceType): Int = when {
    this.isCustom != other.isCustom -> if (this.isCustom) 1 else -1
    this.isCustom -> this.name.compareTo(other.name)
    else -> BUILT_IN_TYPES.indexOf(this).compareTo(BUILT_IN_TYPES.indexOf(other))
  }

}