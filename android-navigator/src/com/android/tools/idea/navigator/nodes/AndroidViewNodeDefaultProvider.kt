/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.AndroidViewNodes
import com.android.tools.idea.navigator.nodes.android.AndroidManifestsGroupNode
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode
import com.android.tools.idea.navigator.nodes.android.AndroidResFolderNode
import com.android.tools.idea.navigator.nodes.android.AndroidSourceTypeNode
import com.android.tools.idea.navigator.nodes.apk.ApkModuleNode
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.getModuleSystem
import com.google.common.collect.HashMultimap
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidSourceType

class AndroidViewNodeDefaultProvider : AndroidViewNodeProvider {
  override fun getModuleNodes(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? {
    val apkFacet = ApkFacet.getInstance(module)
    val androidFacet = AndroidFacet.getInstance(module)
    val project = module.project
    return when {
      androidFacet != null && apkFacet != null ->
        listOf(
          ApkModuleNode(project, module, androidFacet, apkFacet, settings),
          ExternalLibrariesNode(project, settings)
        )

      androidFacet != null && AndroidModel.isRequired(androidFacet) ->
        listOf(AndroidModuleNode(project, module, settings))

      else -> null
    }
  }

  override fun getModuleChildren(module: Module, settings: ViewSettings): List<AbstractTreeNode<*>>? {
    val result = mutableListOf<AbstractTreeNode<*>>()
    val facet = AndroidFacet.getInstance(module) ?: return null
    val project = facet.module.project
    val androidModuleModel = AndroidModuleModel.get(facet)
    val providers = SourceProviders.getInstance(facet)
    val sourcesByType = getSourcesBySourceType(providers, androidModuleModel)
    for (sourceType in sourcesByType.keySet()) {
      when {
        sourceType == AndroidSourceType.CPP -> {
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

    val moduleSystem = module.getModuleSystem()
    val sampleDataPsi = AndroidModuleNode.getPsiDirectory(module.project, moduleSystem.getSampleDataDirectory())
    if (sampleDataPsi != null) {
      result.add(PsiDirectoryNode(module.project, sampleDataPsi, settings))
    }
    return result
  }
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
