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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.GradleExperimentalSettingsConfigurable.TraceProfileItem.DEFAULT
import com.android.utils.FileUtils.writeToFile
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings.nullToEmpty
import com.intellij.diagnostic.VMOptions
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.IOException
import java.lang.System.currentTimeMillis
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Locale

object TraceSyncUtil {
  private val LOG = Logger.getInstance(this::class.java)

  private const val defaultTraceMethods = "# GRADLE\n" +
                                          "Trace: com.android.build.gradle.internal.ide.DefaultAndroidProject\n" +
                                          "Trace: com.android.build.gradle.internal.ide.ModelBuilder\n" +
                                          "Trace: com.android.build.gradle.internal.ide.VariantImpl\n" +
                                          "Trace: com.android.build.gradle.internal.ide.dependencies.ArtifactDependencyGraph\n" +
                                          "Trace: com.android.build.gradle.internal.ide.dependencies.ArtifactUtils\n" +
                                          "Trace: com.android.build.gradle.internal.tasks.factory.TaskAction\n" +
                                          "Trace: com.android.build.gradle.internal.tasks.factory.TaskAction2\n" +
                                          "Trace: org.gradle.api.internal.artifacts.configurations.DefaultConfiguration\$ConfigurationArtifactCollection\n" +
                                          "Trace: org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter::execute\n" +
                                          "Trace: org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter::execute\n" +
                                          "Trace: org.jetbrains.kotlin.gradle.model.builder.KotlinModelBuilder\n" +
                                          "Trace: org.jetbrains.kotlin.idea.gradleTooling.model.kapt.KaptModelBuilderService\n" +
                                          "Trace: com.intellij.gradle.toolingExtension.impl.modelBuilder.ExtraModelBuilder\n" +
                                          "Trace: org.jetbrains.kotlin.idea.gradleTooling.KotlinGradleModelBuilder\n" +
                                          "Trace: org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModelBuilder\n" +
                                          "\n" +
                                          "# IDEA\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.GradleSyncState::syncFailed\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.GradleSyncState::syncStarted\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.GradleSyncState::syncSucceeded\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver::populateProjectExtraModels\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver::populateModuleContentRoots\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver::populateModuleDependencies\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.idea.data.service.GradleModuleModelDataService::importData\n" +
                                          "Trace: com.android.tools.idea.gradle.project.sync.idea.data.service.NdkModuleModelDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.LibraryDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.LibraryDependencyDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.ModuleDependencyDataService::importData\n" +
                                          "Trace: com.intellij.openapi.externalSystem.service.project.manage.ProjectDataServiceImpl::importData\n"


  /**
   * Add the trace jvm options to given args list, only if Studio was launched with trace agent enabled.
   * The added jvm arg is in the format of "-javaagent:AGENT_JAR=TRACE_PROFILE".
   */
  @JvmStatic
  fun addTraceJvmArgs(args: MutableList<Pair<String, String>>) {
    val expectedKey = "-javaagent:${findAgentJar()}"
    ManagementFactory.getRuntimeMXBean().inputArguments.firstOrNull {
      it.startsWith(expectedKey)
    }?.let {
      val optionLine = it.split('=')
      if (optionLine.size == 2) {
        LOG.info("Launch Gradle with trace jvm option: ${it}")
        args.add(Pair(optionLine[0], optionLine[1]))
      }
    }
  }

  /**
   * Write/Remove javaagent in vm options file so that Studio will launch with trace agent enabled/disabled.
   *
   * The added/removed jvm arg is in the format of "-javaagent:AGENT_JAR=TRACE_PROFILE".
   */
  @JvmStatic
  fun updateTraceArgsInFile() {
    val traceArgsPrefix = "-javaagent:${findAgentJar()}="
    val traceArgsValue = (if (GradleExperimentalSettings.getInstance().TRACE_GRADLE_SYNC) getTraceMethods() else null)?.let {
      createTraceProfileFile(it)
    }
    try {
      VMOptions.setOption(traceArgsPrefix, traceArgsValue)
    }
    catch (e: IOException) {
      LOG.error("Unable to write to VM options file", e)
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
      LOG.error("Unable to trace Gradle: could not load trace profile from ${localProfile.path}.")
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
    var path = File(PathManager.getHomePath(), "plugins/android/resources/trace_agent.jar")
    if (!path.exists()) {
      // development build.
      path = File(PathManager.getHomePath(), "../../bazel-bin/tools/base/tracer/trace_agent.jar")
    }
    return path.absolutePath
  }

  /**
   * Create the trace profile.
   * Add output location to the top of trace profile, then append the given traceMethods.
   * The output directory will be idea log path, output file name contains suffix of timestamp to generate one output file per Studio session.
   */
  @VisibleForTesting
  fun createTraceProfileFile(traceMethods: String): String {
    // Specify output file, "Output: /path/to/log/dir/sync_profile_report_[timestamp].json".
    val outputFileName = "sync_profile_report_" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(currentTimeMillis()) + ".json"
    val outputFilePath = FileUtilRt.toSystemDependentName(File(PathManager.getLogPath(), outputFileName).absolutePath)

    val profileContent = "Output: ${outputFilePath}\n${traceMethods}"

    val traceProfile: File = FileUtil.createTempFile("sync.trace", ".profile")
    writeToFile(traceProfile, profileContent)
    LOG.info("Trace output file: $outputFileName")
    return traceProfile.path
  }
}