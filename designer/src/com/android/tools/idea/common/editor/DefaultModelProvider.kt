/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.common.editor

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.type.getConfiguration

val DEFAULT_MODEL_PROVIDER: ModelProvider =
  ModelProvider { disposable, project, facet, componentRegistrar, file ->
    val configuration =
      file.getConfiguration(ConfigurationManager.getOrCreateInstance(facet.module))
    val model =
      NlModel.Builder(disposable, facet, file, configuration)
        .withComponentRegistrar(componentRegistrar)
        .build()
    // For the Layout Editor, set an empty name to enable SceneView toolbars.
    model.displaySettings.setDisplayName("")
    model
  }
