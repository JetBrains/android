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
package com.android.tools.idea.testing

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.containers.ContainerUtil
import org.junit.rules.ExternalResource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * A rule for testing of code that creates crash reports.
 */
class CrashReporterRule : ExternalResource() {

  private val reporter = FakeCrushReporter()
  private var disposable = Disposer.newDisposable()

  val reports: List<CrashReport>
    get() = reporter.reports

  override fun before() {
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(StudioCrashReporter::class.java, reporter, disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}

private class FakeCrushReporter : StudioCrashReporter() {

  val reports = ContainerUtil.createConcurrentList<CrashReport>()
  private val reportCounter = AtomicInteger()

  override fun submit(report: CrashReport, userReported: Boolean): CompletableFuture<String> {
    reports.add(report)
    return CompletableFuture.completedFuture(reportCounter.incrementAndGet().toString())
  }
}
