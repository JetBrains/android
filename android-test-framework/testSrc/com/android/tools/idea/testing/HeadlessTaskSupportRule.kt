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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.testing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskSupport
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Allows the `com.intellij.platform.ide.progress.withModalProgress` and
 * `com.intellij.platform.ide.progress.runWithModalProgressBlocking` functions
 * to be safely used in headless tests. Without this rule these functions may
 * cause deadlocks when used in headless tests.
 */
class HeadlessTaskSupportRule : ExternalResource() {

  private val disposable = Disposer.newDisposable("HeadlessTaskSupportRule")

  override fun before() {
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(TaskSupport::class.java, HeadlessTaskSupport(), disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}

private class HeadlessTaskSupport : TaskSupport {

  override suspend fun <T> withBackgroundProgressInternal(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    suspender: TaskSuspender?,
    visibleInStatusBar: Boolean,
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope { action() }

  override suspend fun <T> withModalProgressInternal(
    owner: ModalTaskOwner,
    title: @NlsContexts.ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope { action() }

  override fun <T> runWithModalProgressBlockingInternal(
    owner: ModalTaskOwner,
    title: @NlsContexts.ProgressTitle String,
    cancellation: TaskCancellation,
    action: suspend CoroutineScope.() -> T,
  ): T = runBlocking { action() }
}