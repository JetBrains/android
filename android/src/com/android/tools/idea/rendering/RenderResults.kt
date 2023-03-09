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
@file:JvmName("RenderResults")
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.Result
import com.android.tools.idea.rendering.imagepool.ImagePool
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile

private val LOG = Logger.getInstance(RenderResult::class.java)

private fun createErrorResult(file: PsiFile, errorResult: Result, logger: RenderLogger?): RenderResult {
  val module = ReadAction.compute<Module, Throwable> { ModuleUtilCore.findModuleForPsiElement(file) }
  assert(module != null)
  val result = RenderResult(
    file,
    module,
    logger ?: RenderLogger(module, null, false),
    null,
    false,
    errorResult,
    ImmutableList.of(),
    ImmutableList.of(),
    ImagePool.NULL_POOLED_IMAGE,
    ImmutableMap.of(),
    ImmutableMap.of(),
    null,
    RenderResultStats.EMPTY
  )

  if (LOG.isDebugEnabled) {
    LOG.debug(result.toString())
  }

  return result
}

/**
 * Creates a new blank [RenderResult]
 *
 * @param file the PSI file the render result corresponds to
 * @return a blank render result
 */
fun createBlank(file: PsiFile): RenderResult {
  return createErrorResult(file, Result.Status.ERROR_UNKNOWN.createResult(""), null)
}

/**
 * Creates a blank [RenderResult] to report render task creation errors
 *
 * @param file the PSI file the render result corresponds to
 * @param logger the logger containing the errors to surface to the user
 */
fun createRenderTaskErrorResult(file: PsiFile, logger: RenderLogger): RenderResult =
  createErrorResult(file, Result.Status.ERROR_RENDER_TASK.createResult(), logger)

fun createRenderTaskErrorResult(file: PsiFile, throwable: Throwable?): RenderResult =
  createErrorResult(file, Result.Status.ERROR_RENDER_TASK.createResult("Render error", throwable), null)
