/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.assistant

import com.android.tools.idea.common.model.NlComponent
import javax.swing.JComponent

/**
 * Interface that allows [com.android.tools.idea.uibuilder.api.ViewHandler]s providing the assistant component.
 */
interface ComponentAssistantFactory {
  /**
   * Context for an assistant panel instance.
   */
  data class Context(
    /** The component that triggered the assistant */
    val component: NlComponent,
    /** Method to be called by the assistant panel if it wants the panel to be closed */
    val doClose: (cancel: Boolean) -> Unit
  ) {
    /** Callback that will be called when the panel closes */
    var onClose: (cancelled: Boolean) -> Unit = {}
  }

  fun createComponent(context: Context): JComponent
}