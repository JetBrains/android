/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.preview.modes.GALLERY_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.GRID_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.LIST_LAYOUT_OPTION
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup

class CommonPreviewToolbar(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup {
    return DefaultActionGroup(
      StopInteractivePreviewAction(isDisabled = { isPreviewRefreshing(it.dataContext) }),
      StopAnimationInspectorAction(isDisabled = { isPreviewRefreshing(it.dataContext) }),
      // TODO(b/292057010) Enable group filtering for Gallery mode.
      GroupSwitchAction(isEnabled = { !isPreviewRefreshing(it.dataContext) })
        .visibleOnlyInDefaultPreview(),
      CommonViewControlAction(
          layoutOptions = listOf(LIST_LAYOUT_OPTION, GRID_LAYOUT_OPTION, GALLERY_LAYOUT_OPTION)
        )
        .visibleOnlyInStaticPreview(),
    )
  }

  override fun getNorthEastGroup(): ActionGroup =
    DefaultActionGroup(
      listOf(
        CommonIssueNotificationAction(),
        ForceCompileAndRefreshActionForNotification.getInstance(),
      )
    )
}
