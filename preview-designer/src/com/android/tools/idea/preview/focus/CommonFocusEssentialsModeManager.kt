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
package com.android.tools.idea.preview.focus

import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * This class handles changing [PreviewModeManager.mode] to [PreviewMode.Focus] whenever essentials
 * mode is activated, either through the Android Studio Essentials Mode or the Preview type's
 * specific Essentials mode.
 *
 * @param project the project this manager will be attached to. It will be used to listen to
 *   messages on its [Project.getMessageBus].
 * @param lifecycleManager used to ensure the preview is active/in the foreground before running any
 *   updates.
 * @param previewFlowManager when switching to Focus Mode, the first preview returned by
 *   [PreviewFlowManager.allPreviewElementsFlow] will be used as the selected preview element. See
 *   [PreviewFlowManager].
 * @param previewModeManager used to switch modes. See [PreviewModeManager].
 * @param onUpdatedFromPreviewEssentialsMode callback that is invoked whenever an update is trigger
 *   by a change to [NlOptionsConfigurable].
 * @param requestRefresh callback used to request a refresh. It is invoked each time there is an
 *   update.
 */
class CommonFocusEssentialsModeManager<T : PreviewElement<*>>(
  project: Project,
  private val lifecycleManager: PreviewLifecycleManager,
  private val previewFlowManager: PreviewFlowManager<T>,
  private val previewModeManager: PreviewModeManager,
  private val onUpdatedFromPreviewEssentialsMode: () -> Unit,
  private val requestRefresh: () -> Unit,
) : Disposable {
  init {
    project.messageBus
      .connect(this)
      .subscribe(
        NlOptionsConfigurable.Listener.TOPIC,
        NlOptionsConfigurable.Listener { updateFocusMode(onUpdatedFromPreviewEssentialsMode) },
      )
  }

  /**
   * Activates the [CommonFocusEssentialsModeManager]. This method should be called from
   * [PreviewLifecycleManager]'s activate methods.
   */
  fun activate() {
    check(lifecycleManager.isActive()) {
      "This method should be called from PreviewLifecycleManager's activate methods."
    }
    updateFocusMode()
  }

  /**
   * Updates the [PreviewModeManager]'s [PreviewMode] according to the state of Android Studio
   * (and/or Preview) Essentials Mode.
   *
   * @param sourceCallback callback for the source that triggered the update. See
   *   [onUpdatedFromPreviewEssentialsMode].
   */
  private fun updateFocusMode(sourceCallback: (() -> Unit)? = null) {
    // If Preview is inactive - don't update Focus.
    if (!lifecycleManager.isActive()) return
    val focusModeIsSet = previewModeManager.mode.value is PreviewMode.Focus
    // Only update focus mode if needed
    if (PreviewEssentialsModeManager.isEssentialsModeEnabled == focusModeIsSet) return

    if (focusModeIsSet) {
      // There is no need to switch back to Default mode as toolbar is available.
      // When exiting Essentials mode - preview will stay in Focus mode.
    } else {
      (previewFlowManager.allPreviewElementsFlow.value as? FlowableCollection.Present)
        ?.collection
        ?.firstOrNull()
        .let { previewModeManager.setMode(PreviewMode.Focus(it)) }
    }
    sourceCallback?.invoke()
    requestRefresh()
  }

  override fun dispose() {}
}
