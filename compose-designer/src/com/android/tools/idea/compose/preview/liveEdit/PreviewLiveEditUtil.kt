/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.liveEdit

import com.android.tools.idea.compose.preview.PREVIEW_NOTIFICATION_GROUP_ID
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.toDisplayString
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.google.common.base.Stopwatch
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.psi.PsiFile
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.time.withTimeout
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.kotlin.idea.util.module
import java.io.File
import java.time.Duration

/**
 * Maximum amount of time to wait for a fast compilation to happen.
 */
private val LIVE_EDIT_PREVIEW_COMPILE_TIMEOUT = java.lang.Long.getLong("preview.live.edit.daemon.compile.seconds.timeout", 20)

/**
 * Starts a new fast compilation for the current file in the Preview. [onSuccessCallback] will be invoked after the build
 * finishes successfully.
 */
fun fastCompileAsync(parentDisposable: Disposable, file: PsiFile, onSuccessCallback: () -> Unit = {}) {
  val contextModule = file.module ?: return
  val project = file.project
  val stopWatch = Stopwatch.createStarted()
  object : Task.Backgroundable(project, message("notification.compiling"), false) {
    override fun run(indicator: ProgressIndicator) {
      AndroidCoroutineScope(parentDisposable).async {
        val (result, outputAbsolutePath) = withTimeout(Duration.ofSeconds(LIVE_EDIT_PREVIEW_COMPILE_TIMEOUT)) {
          PreviewLiveEditManager.getInstance(project).compileRequest(listOf(file), contextModule, indicator)
        }
        val durationString = stopWatch.elapsed().toDisplayString()
        val isSuccess = result == CompilationResult.Success
        val buildMessage = if (isSuccess)
          message("event.log.live.edit.build.successful", durationString)
        else
          message("event.log.live.edit.build.failed", durationString)
        Notification(PREVIEW_NOTIFICATION_GROUP_ID,
                     buildMessage,
                     NotificationType.INFORMATION)
          .notify(project)
        if (isSuccess) {
          ModuleClassLoaderOverlays.getInstance(contextModule).overlayPath = File(outputAbsolutePath).toPath()
          onSuccessCallback()
        }
      }.asCompletableFuture().join()
    }
  }.queue()
}