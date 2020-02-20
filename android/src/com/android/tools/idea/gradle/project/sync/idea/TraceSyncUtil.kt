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
@file:JvmName("TraceSyncUtil")

package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.flags.ExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.utils.FileUtils.writeToFile
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings.nullToEmpty
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.lang.System.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Locale

private const val defaultTraceMethods = "Trace: com.android.build.gradle.internal.ide.ModelBuilder\n" +
                                        "Trace: com.android.build.gradle.internal.ide.dependencies.ArtifactDependencyGraph\n" +
                                        "Trace: com.android.build.gradle.internal.tasks.factory.TaskAction\n" +
                                        "Trace: com.android.build.gradle.internal.tasks.factory.TaskAction2\n" +
                                        "Trace: org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter::execute\n" +
                                        "Trace: org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter::execute\n"

/**
 * Add the trace jvm options to given args list.
 * This is no-op if trace is not enabled.
 * The added jvm arg is in the format of "-javaagent:AGENT_JAR=TRACE_PROFILE".
 */
fun addTraceJvmArgs(args: MutableList<Pair<String, String>>) {
  val settings = GradleExperimentalSettings.getInstance()
  if (!settings.TRACE_GRADLE_SYNC) {
    return
  }

  getTraceMethods()?.let { traceMethods ->
    val traceJvmArg = Pair("-javaagent:${findAgentJar()}", createTraceProfileFile(traceMethods))
    RESOLVER_LOG.info("Trace jvm option: ${traceJvmArg.first}=${traceJvmArg.second}")
    args.add(traceJvmArg)
  }
}

/**
 * Returns defaultTraceMethods if DEFAULT profile is selected in settings, return file content if
 * profile location is specified or null if the specified location doesn't exist.
 */
private fun getTraceMethods(): String? {
  val settings = GradleExperimentalSettings.getInstance()

  if (settings.TRACE_PROFILE_SELECTION == DEFAULT) {
    return defaultTraceMethods
  }

  val localProfile = File(nullToEmpty(settings.TRACE_PROFILE_LOCATION))
  return if (!localProfile.isFile) {
    RESOLVER_LOG.error("Unable to trace Gradle: could not load trace profile from ${localProfile.path}.")
    null
  }
  else {
    localProfile.readText()
  }
}

/**
 * Find the location of trace_agent jar.
 * The expected location for release build is plugins/android/lib/trace_agent.jar,
 * for dev build is ../../bazel-bin/tools/base/tracer/trace_agent.jar.
 */
@VisibleForTesting
fun findAgentJar(): String {
  var path = File(PathManager.getHomePath(), "plugins/android/lib/trace_agent.jar")
  if (!path.exists()) {
    // development build.
    path = File(PathManager.getHomePath(), "../../bazel-bin/tools/base/tracer/trace_agent.jar")
  }
  return path.absolutePath
}

/**
 * Create the trace profile.
 * Add output location to the top of trace profile, then append the given traceMethods.
 * The output directory will be idea log path, output file name contains suffix of timestamp to generate one output file per sync.
 */
@VisibleForTesting
fun createTraceProfileFile(traceMethods: String): String {
  // Specify output file, "Output: /path/to/log/dir/sync_profile_report_[timestamp].json".
  val outputFileName = "sync_profile_report_" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(currentTimeMillis()) + ".json"
  val outputFilePath = FileUtil.toSystemDependentName(File(PathManager.getLogPath(), outputFileName).absolutePath)

  val profileContent = "Output: ${outputFilePath}\n${traceMethods}"

  val traceProfile: File = FileUtil.createTempFile("sync.trace", ".profile")
  traceProfile.deleteOnExit()
  writeToFile(traceProfile, profileContent)
  RESOLVER_LOG.info("Trace output file: $outputFileName")
  return traceProfile.path
}