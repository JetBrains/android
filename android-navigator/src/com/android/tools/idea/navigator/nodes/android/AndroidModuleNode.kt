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
package com.android.tools.idea.navigator.nodes.android

import com.android.ide.common.util.PathString
import com.android.tools.idea.fileTypes.AndroidIconProvider
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.NdkModuleModel.Companion.get
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.AndroidViewNodes
import com.android.tools.idea.navigator.nodes.AndroidViewModuleNode
import com.android.tools.idea.navigator.nodes.ndk.containedByNativeNodes
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getHolderModule
import com.android.tools.idea.util.toVirtualFile
import com.google.common.collect.HashMultimap
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Queryable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidSourceType
import org.jetbrains.android.facet.SourceProviderManager.Companion.getInstance

/**
 * [com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode] does not classify source types, and just assumes that all source
 * roots contain Java packages. This class overrides that behavior to provide a per source type node ([AndroidSourceTypeNode]) inside
 * a module.
 */
class AndroidModuleNode(
  project: Project,
  module: Module,
  settings: ViewSettings
) : AndroidViewModuleNode(project, module, settings) {
  override fun getModuleChildren(): Collection<AbstractTreeNode<*>> {
    val module = value?.takeUnless { it.isDisposed } ?: return emptyList()
    val facet = AndroidFacet.getInstance(module)
    return if (facet == null || AndroidModel.get(facet) == null) {
      platformGetChildren()
    }
    else {
      getChildren(
        facet,
        settings,
        getInstance(facet)
      )
    }
  }

  override fun contains(file: VirtualFile): Boolean {
    if (super.contains(file)) {
      return true
    }

    // If there is a native-containing module then check it for externally referenced header files
    val module = value?.takeUnless { it.isDisposed } ?: return false

    val facet = AndroidFacet.getInstance(module) ?: return false
    if (AndroidModel.get(facet) == null) {
      return false
    }
    val ndkModuleModel = get(facet.module)
    return ndkModuleModel?.let { containedByNativeNodes(myProject, it, file) } ?: false
  }

  override fun getSortKey(): Comparable<*>? = value?.takeUnless { it.isDisposed }?.name
  override fun getTypeSortKey(): Comparable<*>? = sortKey

  override fun toTestString(printInfo: Queryable.PrintInfo?): String {
    val module = value
    return if (module == null || module.isDisposed) {
      if (module == null) "(null)" else "(Disposed)"
    }
    else String.format("%1\$s (Android)", super.toTestString(printInfo))
  }

  override fun update(presentation: PresentationData) {
    super.update(presentation)
    val module = value
    if (module == null || module.isDisposed) {
      return
    }
    // Use Android Studio Icons if module is available. If module was disposed, super.update will set the value of this node to null.
    // This can happen when a module was just deleted, see b/67838273.
    presentation.setIcon(AndroidIconProvider.getModuleIcon(module))
  }

  /**
   * This node represents:
   * - module represented by this node
   * - all virtual files that belong to this module or its linked modules that are not contained by its children
   *
   * TODO (http://b/249099672): This should be expanded to handle more cases.
   */
  override fun canRepresent(element: Any?): Boolean {
    if (super.canRepresent(element)) return true

    val file = when (element) {
      is VirtualFile -> element
      is PsiElement -> PsiUtilCore.getVirtualFile(element)
      else -> null
    } ?: return false

    val project = project.takeUnless { it == null || it.isDisposed } ?: return false
    val moduleForFile = ProjectFileIndex.getInstance(project).getModuleForFile(file, false)
    if (value != moduleForFile?.getHolderModule()) return false

    val childrenContainFile = moduleChildren.any {
      it !is ProjectViewNode || it.contains(file)
    }

    return !childrenContainFile
  }

  companion object {
    fun getChildren(
      facet: AndroidFacet,
      settings: ViewSettings,
      providers: SourceProviders
    ): Collection<AbstractTreeNode<*>> {
      val result: MutableList<AbstractTreeNode<*>> = ArrayList()
      val project = facet.module.project
      val androidModuleModel = AndroidModuleModel.get(facet)
      val sourcesByType = getSourcesBySourceType(providers, androidModuleModel)
      val ndkModuleModel = get(facet.module)
      for (sourceType in sourcesByType.keySet()) {
        when {
          sourceType == AndroidSourceType.CPP && ndkModuleModel != null -> {
            // Native sources will be added separately from NativeAndroidGradleModel.
          }
          sourceType == AndroidSourceType.MANIFEST -> {
            result.add(AndroidManifestsGroupNode(project, facet, settings, sourcesByType[sourceType]))
          }
          sourceType == AndroidSourceType.RES || sourceType == AndroidSourceType.GENERATED_RES -> {
            result.add(AndroidResFolderNode(project, facet, sourceType, settings, sourcesByType[sourceType]))
          }
          sourceType == AndroidSourceType.SHADERS && androidModuleModel == null -> {
          }
          sourceType == AndroidSourceType.ASSETS -> {
            result.add(
              AndroidSourceTypeNode(
                project,
                facet,
                object : ViewSettings by settings {
                  override fun isFlattenPackages(): Boolean = false
                  override fun isHideEmptyMiddlePackages(): Boolean = false
                },
                sourceType,
                sourcesByType[sourceType]
              )
            )
          }
          else -> {
            result.add(AndroidSourceTypeNode(project, facet, settings, sourceType, sourcesByType[sourceType]))
          }
        }
      }
      if (ndkModuleModel != null) {
        result.add(AndroidJniFolderNode(project, ndkModuleModel, settings))
      }
      val moduleSystem = facet.holderModule.getModuleSystem()
      val sampleDataPsi = getPsiDirectory(project, moduleSystem.getSampleDataDirectory())
      if (sampleDataPsi != null) {
        result.add(PsiDirectoryNode(project, sampleDataPsi, settings))
      }
      return result
    }

    private fun getPsiDirectory(project: Project, path: PathString?): PsiDirectory? {
      val virtualFile = path.toVirtualFile() ?: return null
      return PsiManager.getInstance(project).findDirectory(virtualFile)
    }

    private fun getSourcesBySourceType(
      providers: SourceProviders,
      androidModel: AndroidModuleModel?
    ): HashMultimap<AndroidSourceType, VirtualFile> {
      val sourcesByType = HashMultimap.create<AndroidSourceType, VirtualFile>()

      // Multiple source types can sometimes be present in the same source folder, e.g.:
      //    sourcesSets.main.java.srcDirs = sourceSets.main.aidl.srcDirs = ['src']
      // in such a case, we only want to show one of them. Source sets can be either proper or improper subsets. It is not entirely
      // obvious there is a perfect solution here, but since this is not a common occurrence, we resort to the easiest solution here:
      // If a set of sources has partially been included as part of another source type's source set, then we simply don't include it
      // as part of this source type.
      val allSources: MutableSet<VirtualFile> = HashSet()
      for (sourceType in AndroidSourceType.BUILT_IN_TYPES) {
        if (sourceType == AndroidSourceType.SHADERS && androidModel == null) {
          continue
        }

        val sources = when (sourceType) {
          AndroidSourceType.GENERATED_JAVA, AndroidSourceType.GENERATED_RES -> getGeneratedSources(sourceType, providers)
          else -> getSources(sourceType, providers)
        }
        
        if (sources.isEmpty()) {
          continue
        }

        // if we have a partial overlap, we put just the non overlapping sources into this source type
        sourcesByType.putAll(sourceType, sources - allSources)
        allSources.addAll(sources)
      }

      for (provider in AndroidViewNodes.getSourceProviders(providers)) {
        for (customKey in provider.custom.keys) {
          val customType = AndroidSourceType.Custom(customKey)
          sourcesByType.putAll(customType, getSources(customType, providers))
        }
      }
      return sourcesByType
    }

    private fun getSources(sourceType: AndroidSourceType, providers: SourceProviders): Set<VirtualFile> {
      return AndroidViewNodes.getSourceProviders(providers).sourcesOfType(sourceType)
    }

    private fun getGeneratedSources(sourceType: AndroidSourceType, providers: SourceProviders): Set<VirtualFile> {
      return AndroidViewNodes.getGeneratedSourceProviders(providers).sourcesOfType(sourceType)
    }

    private fun Iterable<IdeaSourceProvider>.sourcesOfType(sourceType: AndroidSourceType): Set<VirtualFile> {
      return flatMap { sourceType.getSources(it) }.toSet()
    }
  }
}