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
package com.android.tools.compose.debug

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.sun.jdi.Method
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.KotlinFunctionBreakpoint
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.SourcePositionRefiner

/**
 * A [com.intellij.debugger.ui.breakpoints.MethodBreakpoint] that supports `@Composable` function
 * breakpoints
 */
internal class ComposeFunctionBreakpoint(project: Project, breakpoint: XBreakpoint<*>) :
  KotlinFunctionBreakpoint(project, breakpoint), SourcePositionRefiner {
  override fun isMethodMatch(method: Method, debugProcess: DebugProcessImpl) =
    method.name() == methodName &&
      method.signature().withoutComposeArgs() == mySignature?.getName(debugProcess)
}

private fun String.withoutComposeArgs(): String {
  val start = indexOf("Landroidx/compose/runtime/Composer;")
  val end = lastIndexOf(')')
  if (start < 0 || end < 0) {
    return this
  }
  return substring(0, start) + substring(end)
}
