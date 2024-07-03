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
package com.android.tools.idea.rendering

import com.android.tools.configurations.Configuration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.project.Project

fun RenderService.createHtmlLogger(project: Project?): RenderLogger {
  return createLogger(
    project,
    StudioFlags.NELE_LOG_ANDROID_FRAMEWORK.get(),
    ShowFixFactory,
    ::StudioHtmlLinkManager,
  )
}

/**
 * Returns a [RenderService.RenderTaskBuilder] that can be used to build a new [RenderTask] with
 * HTML rendering of issues and fix actions.
 */
fun RenderService.taskBuilderWithHtmlLogger(
  buildTarget: BuildTargetReference,
  configuration: Configuration,
): RenderService.RenderTaskBuilder =
  taskBuilder(buildTarget, configuration, createHtmlLogger(buildTarget.project))
