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
package com.android.tools.idea.preview.essentials

import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Returns a flow that is updated each time [NlOptionsConfigurable]s are updated. The flow value
 * will be the current state of preview essentials mode.
 */
fun essentialsModeFlow(project: Project, parentDisposable: Disposable): StateFlow<Boolean> {
  val flow = MutableStateFlow(PreviewEssentialsModeManager.isEssentialsModeEnabled)
  project.messageBus
    .connect(parentDisposable)
    .subscribe(
      NlOptionsConfigurable.Listener.TOPIC,
      NlOptionsConfigurable.Listener {
        flow.value = PreviewEssentialsModeManager.isEssentialsModeEnabled
      },
    )
  return flow
}
