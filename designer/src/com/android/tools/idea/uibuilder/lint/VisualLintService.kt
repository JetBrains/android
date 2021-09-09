/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.AdditionalDeviceService
import com.android.tools.idea.configurations.Configuration
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

/**
 * Service that runs visual lints
 */
@Service
class VisualLintService {

  companion object {
    @JvmStatic
    fun getInstance(): VisualLintService? {
      return ApplicationManager.getApplication().getService(VisualLintService::class.java)
    }
  }

  /**
   * Run visual lint analysis and return the list of issues.
   */
  fun runVisualLintAnalysis(models: List<NlModel>) {
    // TODO: Implementation
  }
}

