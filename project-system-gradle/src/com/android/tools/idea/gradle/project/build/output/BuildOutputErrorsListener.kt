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
package com.android.tools.idea.gradle.project.build.output

import com.android.SdkConstants
import com.android.ide.common.blame.parser.aapt.AbstractAaptOutputParser
import com.android.ide.common.resources.MergingException
import com.android.tools.idea.projectsystem.FilenameConstants
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FailureResult
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.util.Disposer
import java.io.File

class BuildOutputErrorsListener(
  private val externalSystemTaskId: ExternalSystemTaskId,
  private val listenerDisposable: Disposable,
  private val onFailureFinishBuildEvent: (List<BuildErrorMessage>) -> Unit
) : BuildProgressListener {
  private val buildErrorMessages = ArrayList<BuildErrorMessage>()

  override fun onEvent(buildId: Any, event: BuildEvent) {
    if (buildId != externalSystemTaskId) return
    toBuildErrorMessage(event)?.let { buildErrorMessages.add(it)  }
    if (event is FinishBuildEvent) {
      try {
        // When running tests, this result is 'successful' even if build failed with e.g. compilation failure.
        // So if we collected anything report anyway.
        if (event.result is FailureResult || buildErrorMessages.isNotEmpty()) {
          onFailureFinishBuildEvent(buildErrorMessages)
        }
      }
      finally {
        Disposer.dispose(listenerDisposable)
      }
    }
  }
}

fun toBuildErrorMessage(buildEvent: BuildEvent): BuildErrorMessage? {
  if (buildEvent !is MessageEvent || buildEvent.kind != MessageEvent.Kind.ERROR) {
    return null
  }

  return if (buildEvent is BuildIssueEvent) {
    addStatsFromBuildIssue(buildEvent)
  }
  else {
    addStatsFromDefaultMessage(buildEvent)
  }
}


private fun addStatsFromBuildIssue(buildEvent: BuildIssueEvent): BuildErrorMessage? {
  val buildErrorMessageBuilder = BuildErrorMessage.newBuilder()
  when(buildEvent.issue.title) {
    TomlErrorParser.BUILD_ISSUE_TITLE -> BuildErrorMessage.ErrorType.INVALID_TOML_DEFINITION
    ConfigurationCacheErrorParser.BUILD_ISSUE_TITLE -> BuildErrorMessage.ErrorType.CONFIGURATION_CACHE
    else -> null
  }?.let {
    buildErrorMessageBuilder.errorShownType = it
  }

  //TODO(b/326938231): add file stats based on navigable, while doing refactoring. Currently it is hard as requires project.
  //               Plus eagerly requesting navigable might be wrong.
  return buildErrorMessageBuilder.build()
}

private fun addStatsFromDefaultMessage(buildEvent: MessageEvent): BuildErrorMessage? {
  val buildErrorMessageBuilder = BuildErrorMessage.newBuilder()
  findErrorTypeByGroup(buildEvent.group)?.let {
    buildErrorMessageBuilder.errorShownType = it
  }

  if (buildEvent is FileMessageEvent) {
    buildErrorMessageBuilder.fileLocationIncluded = true
    buildErrorMessageBuilder.fileIncludedType = getFileType(buildEvent.filePosition.file)
    if (buildEvent.filePosition.startLine >= 0) {
      buildErrorMessageBuilder.lineLocationIncluded = true
    }
  }
  return buildErrorMessageBuilder.build()
}


private val toolNameToEnumMap = mapOf("Compiler" to BuildErrorMessage.ErrorType.JAVA_COMPILER,
                                      "Kotlin Compiler" to BuildErrorMessage.ErrorType.KOTLIN_COMPILER,
                                      CLANG_COMPILER_MESSAGES_GROUP_PREFIX to BuildErrorMessage.ErrorType.CLANG,
                                      CmakeOutputParser.CMAKE to BuildErrorMessage.ErrorType.CMAKE,
                                      DATABINDING_GROUP to BuildErrorMessage.ErrorType.DATA_BINDING,
                                      XmlErrorOutputParser.XML_PARSING_GROUP to BuildErrorMessage.ErrorType.XML_PARSER,
                                      @Suppress("VisibleForTests")
                                      (AbstractAaptOutputParser.AAPT_TOOL_NAME) to BuildErrorMessage.ErrorType.AAPT,
                                      "D8" to BuildErrorMessage.ErrorType.D8,
                                      "R8" to BuildErrorMessage.ErrorType.R8,
                                      MergingException.RESOURCE_ASSET_MERGER_TOOL_NAME to BuildErrorMessage.ErrorType.RESOURCE_AND_ASSET_MERGER,
                                      AndroidGradlePluginOutputParser.ANDROID_GRADLE_PLUGIN_MESSAGES_GROUP to BuildErrorMessage.ErrorType.GENERAL_ANDROID_GRADLE_PLUGIN,
)

private fun findErrorTypeByGroup(messageGroup: String): BuildErrorMessage.ErrorType? = toolNameToEnumMap.filterKeys {
  messageGroup.startsWith(it)
}.values.firstOrNull()

/**
 * Returns whether the file is build generated or user added.
 */
private fun getFileType(file: File): BuildErrorMessage.FileType {
  val filePath = if (file.isAbsolute) file.absolutePath else file.path
  if (filePath.contains(File.separatorChar + FileUtils.join(FilenameConstants.BUILD, SdkConstants.FD_GENERATED) + File.separatorChar) ||
      filePath.contains(File.separatorChar + FileUtils.join(FilenameConstants.BUILD, SdkConstants.FD_INTERMEDIATES) + File.separatorChar)) {
    return BuildErrorMessage.FileType.BUILD_GENERATED_FILE
  }
  return BuildErrorMessage.FileType.PROJECT_FILE
}