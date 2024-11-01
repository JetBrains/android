/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.cpu

import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.idea.gradle.project.sync.memory.OUTPUT_DIRECTORY
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import org.junit.rules.ExternalResource
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.time.Instant
import javax.management.MBeanServer
import javax.management.ObjectName

class CaptureJfrRule : ExternalResource() {

  interface ListenerWithGradleDaemonProgress: GradleSyncListenerWithRoot, ExternalSystemTaskNotificationListener

  val listener  = object : ListenerWithGradleDaemonProgress {
    // Gradle daemon finished
    override fun onSuccess(id: ExternalSystemTaskId) {
      startJavaFlightRecording()
    }

    // Entire sync finished
    override fun syncSucceeded(project: Project, rootProjectPath: String) {
      stopJavaFlightRecording()
    }
  }


  override fun before() {
    mutateGradleProperties {
      setJvmArgs("$jvmArgs -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:FlightRecorderOptions=stackdepth=512")
    }
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener)
  }

  override fun after() {
    File(OUTPUT_DIRECTORY).walk().filter { !it.isDirectory && it.extension == "jfr" }.forEach { jfrFile ->
      Files.move(jfrFile.toPath(), TestUtils.getTestOutputDir().resolve(jfrFile.name))
    }
    ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(listener)
  }

  private fun startJavaFlightRecording() {
    val server = ManagementFactory.getPlatformMBeanServer()
    println("Started capturing jfr")
    println(server.execute("jfrStart", arrayOf(arrayOf("name=jfr settings=profile method-profiling=max"))))
  }

  private fun stopJavaFlightRecording() {
    val fileJfr = TestUtils.getTestOutputDir().toFile().resolve("${Instant.now().toEpochMilli()}_ide.jfr")
    val server = ManagementFactory.getPlatformMBeanServer()

    println(server.execute("jfrStop", arrayOf(arrayOf("name=jfr filename=${fileJfr.path}"))))
  }

  private fun MBeanServer.execute(name: String, args: Array<Array<String>?> = arrayOf(null)) = invoke(
    ObjectName("com.sun.management:type=DiagnosticCommand"),
    name,
    args,
    arrayOf(Array<String>::class.java.name)
  ).toString()

  companion object {
    fun shouldEnable() = System.getProperty("capture_jfr").toBoolean()
  }
}