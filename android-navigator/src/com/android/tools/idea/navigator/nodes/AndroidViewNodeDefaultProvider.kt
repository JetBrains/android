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
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode
import com.android.tools.idea.navigator.nodes.apk.ApkModuleNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.Module
import org.jetbrains.android.facet.AndroidFacet

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
}