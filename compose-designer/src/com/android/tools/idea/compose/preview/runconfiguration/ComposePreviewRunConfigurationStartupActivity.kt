/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.runconfiguration

import com.intellij.execution.RunConfigurationProducerService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class ComposePreviewRunConfigurationStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val producerClass = ComposePreviewRunConfigurationProducer::class.java
    val producerService = RunConfigurationProducerService.getInstance(project)
    // Make sure to remove the producer from the ignored list in case it was added at some point when the flag was disabled.
    producerService.state.ignoredProducers.remove(producerClass.name)
  }
}
