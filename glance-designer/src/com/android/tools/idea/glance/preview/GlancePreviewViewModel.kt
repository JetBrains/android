/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.mvvm.PreviewView
import com.android.tools.idea.preview.mvvm.PreviewViewModel
import com.android.tools.idea.preview.viewmodels.CommonPreviewViewModel
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

private const val PREVIEW_NOTIFICATION_GROUP_ID = "Glance Preview Notification"

/** [PreviewViewModel] for the Glance previews. */
internal class GlancePreviewViewModel(
  previewView: PreviewView,
  renderingBuildStatusManager: RenderingBuildStatusManager,
  previewRefreshManager: PreviewRefreshManager,
  project: Project,
  psiFilePointer: SmartPsiElementPointer<PsiFile>,
  hasRenderErrors: () -> Boolean,
) :
  CommonPreviewViewModel(
    previewView,
    renderingBuildStatusManager,
    previewRefreshManager,
    project,
    psiFilePointer,
    hasRenderErrors,
    { durationString ->
      Notification(
        PREVIEW_NOTIFICATION_GROUP_ID,
        GlancePreviewBundle.message("event.log.refresh.title"),
        GlancePreviewBundle.message("event.log.refresh.total.elapsed.time", durationString),
        NotificationType.INFORMATION,
      )
    },
  )
