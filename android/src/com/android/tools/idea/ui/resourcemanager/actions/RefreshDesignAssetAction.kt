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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.tools.idea.ui.resourcemanager.ResourceManagerTracking
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.RESOURCE_DESIGN_ASSETS_KEY
import com.android.tools.idea.ui.resourcemanager.rendering.SlowResource.Companion.isSlowResource
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey

/**
 * Action that calls the given [refreshAssetsCallback] when there are supported [DesignAsset]s under the [RESOURCE_DESIGN_ASSETS_KEY]
 * [DataKey].
 */
class RefreshDesignAssetAction(private val refreshAssetsCallback: (Array<DesignAsset>) -> Unit)
  : AnAction("Refresh Preview", "Refresh the preview for the selected resources", null) {

  override fun actionPerformed(e: AnActionEvent) {
    val assets = e.getData(RESOURCE_DESIGN_ASSETS_KEY)
    if (assets != null && canRefresh(assets)) {
      ResourceManagerTracking.logRefreshAsset(e.project, assets.first().type)
      refreshAssetsCallback(assets)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = canRefresh(e.getData(RESOURCE_DESIGN_ASSETS_KEY))
  }

  private fun canRefresh(assets: Array<DesignAsset>?): Boolean {
    return if (assets.isNullOrEmpty()) {
      false
    }
    else {
      assets.all { it.type.isSlowResource() }
    }
  }
}