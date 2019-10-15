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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.uibuilder.model.viewInfo
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.pom.Navigatable
import com.intellij.psi.xml.XmlTag


/**
 * Handles navigation for compose preview when NlDesignSurface preview is clicked.
 */
class PreviewNavigationHandler : NlDesignSurface.NavigationHandler {

  private val map = HashMap<NlModel, Navigatable>()
  private val fileNames = HashSet<String>()

  fun addMap(model: NlModel, navigatable: Navigatable, name: String) {
    map[model] = navigatable
    fileNames.add(name)
  }

  override fun getFileNames(): Set<String> {
    return fileNames
  }


  override fun handleNavigate(sceneView: SceneView, models: ImmutableList<NlModel>, requestFocus: Boolean) {
    for (model in models) {
      if (sceneView.model == model) {
        val navigatable = map[model] ?: return
        navigatable.navigate(requestFocus)
        return
      }
    }
  }

  override fun dispose() {
    map.clear()
    fileNames.clear()
  }
}

class PreviewModelUpdater(val surface: NlDesignSurface) : DefaultModelUpdater() {
  override fun update(model: NlModel, newRoot: XmlTag?, roots: MutableList<NlModel.TagSnapshotTreeNode>) {

    // TODO: Perhaps there's a better place to register the listener. It must be after
    // the scene manager is set though.
    surface.sceneManager!!.addRenderListener(object : RenderListener {
      override fun onRenderCompleted() {
        // Make sure you deregister after.
        surface.sceneManager!!.removeRenderListener(this)

        if (model.components.isEmpty()) {
          return
        }

        val root = model.components[0]
        val viewInfo = root.viewInfo
        val viewObject = viewInfo!!.viewObject
        val customViewInfo = parseViewObject(
          surface.navigationHandler!!, viewObject)

        // TODO: After the render is complete, we inject our own scene hierarchy.

      }
    })

    // Delegate to the default updater.
    super.update(model, newRoot, roots)
  }
}