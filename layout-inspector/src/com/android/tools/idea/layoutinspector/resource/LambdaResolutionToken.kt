/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

interface LambdaResolutionToken<P : AndroidProjectSystem> : Token {
  companion object {
    val EP_NAME =
      ExtensionPointName<LambdaResolutionToken<AndroidProjectSystem>>(
        "com.android.tools.idea.layoutinspector.resource.getLambdaResolutionToken"
      )
  }

  fun findCauseOfMissingSourceLocation(projectSystem: P, module: Module): VersionProblem?
}

enum class ProblemType {
  /** The compose.ui:ui version doesn't have support for reading D8 annotations for LambaLocation. */
  COMPOSE_UI,

  /** The AGP version does not contain a D8 version that emits annotations for LambdaLocation. */
  D8,

  /** AGP does not support global synthetics for this JDK version. */
  JDK,
}

/** A generic version problem */
data class VersionProblem(val type: ProblemType, val requiredVersion: String, val actualVersion: String) {
  fun getMessage(): String =
    when (type) {
      ProblemType.COMPOSE_UI -> LayoutInspectorBundle.message("version.problem.compose.ui", requiredVersion, actualVersion)
      ProblemType.D8 -> LayoutInspectorBundle.message("version.problem.d8", requiredVersion, actualVersion)
      ProblemType.JDK -> LayoutInspectorBundle.message("version.problem.jdk", requiredVersion, actualVersion)
    }
}
