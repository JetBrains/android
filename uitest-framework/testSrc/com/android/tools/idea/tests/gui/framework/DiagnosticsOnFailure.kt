/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework

import com.intellij.ide.logsUploader.LogsPacker.packLogs
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.nio.file.Files
import kotlin.io.path.isDirectory
import kotlin.io.path.moveTo
import kotlin.streams.asSequence

/** Rule that takes a screenshot when the test fails.  */
internal class DiagnosticsOnFailure : TestWatcher() {
  override fun failed(throwable: Throwable, description: Description) {
    val project = ProjectManager.getInstance().openProjects[0]
    val fileName = description.testClass.simpleName + "." + description.methodName + "-diagnostics.zip"
    @Suppress("UnstableApiUsage") val diagnostics = runBlocking { packLogs(project) }

    try {
      val diagnosticsDir = GuiTests.getFailedTestDiagnosticsDirPath().toPath()
      Files.createDirectories(diagnosticsDir)
      val destination = diagnosticsDir.resolve(fileName)
      diagnostics.moveTo(destination, true)
      println("Diagnostics: $diagnostics to $destination")
    } catch (t: Throwable) {
      println("Diagnostics failed. " + t.message)
      t.printStackTrace()
    }
  }
}
