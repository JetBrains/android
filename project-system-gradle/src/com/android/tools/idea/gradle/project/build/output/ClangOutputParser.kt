/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.utils.cxx.process.NativeBuildOutputClassifier
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.INFO
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.ERROR
import com.android.utils.cxx.process.NativeToolLineClassification.Kind.WARNING
import com.android.utils.cxx.process.regexField
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.io.File
import java.util.function.Consumer

/**
 * Pattern matching Gradle output indicating the start of a native build task. For example
 *
 * > Task :app:externalNativeBuildDebug
 */
private val nativeBuildTaskPattern = Regex(
  "> Task (?<gradleProject>(?::[^:]+)*):" +
          "(?:buildCMake|buildNdkBuild|buildNinja|configureCMake|configureNdkBuild|configureNinja|externalNativeBuild)" +
          "(?<variant>[^ \\[]+)(\\[.*])?(?: [-A-Z]+)?")

/**
 * Pattern indicating the end of the overall build. This is needed to finalize the last message being parsed.
 */
private val endOfBuildSignals = Regex("BUILD FAILED in (\\d+)s.*|FAILURE: Build failed.*")

const val CLANG_COMPILER_MESSAGES_GROUP_PREFIX = "Clang Compiler"

fun compilerMessageGroup(gradleProject: String, variant: String, abi: String?) =
  "$CLANG_COMPILER_MESSAGES_GROUP_PREFIX [${listOfNotNull(gradleProject, variant, abi).joinToString(" ")}]"

/** Parser that parses Clang output and emit [BuildEvent] indicating with compiler diagnostic messages. */
class ClangOutputParser : BuildOutputParser {
  /**
   * contains parsers for all native tasks that are currently running.
   *   key: Build Tree node parent ID
   *   value: parser for that task
   */
  private val parsers = mutableMapOf<Any, RunningParse>()

  /**
   * Parses an build output while it's being streamed from external build systems.
   *
   * @param currentLine the most recent line acquired from the passed in [BuildOutputInstantReader]
   * @param reader a reader that is useful to actively consuming more build output or peek previous output. This can be used by parsers that
   * needs more than the current line to work. Also note that all state changes made to the reader will affect other parsers. That is, if
   * this parser reads several lines from the reader without reset the reader's state, other parsers won't be able to read such consumed
   * states. This is useful if the parser knows other parsers won't be interested in the consumed build outputs.
   * @param messageConsumer consumer of build events emitted by this parser. For example, upon encountering a syntax error in a source code
   * file, this parser can emit a [FileMessageEventImpl] so that the IDE will show a corresponding entry in the 'Build Output' UI.
   * @return true if the current line is consumed by this parser and should not be passed to other parsers. Otherwise, false.
   */
  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    // Check for the start of a new running task.
    if (line.startsWith("> Task")) {
      val nativeBuildTaskMatch = nativeBuildTaskPattern.matchEntire(line) ?: return false
      val gradleProject = nativeBuildTaskMatch.regexField("gradleProject")
      val variant = nativeBuildTaskMatch.regexField("variant")
      parsers[reader.parentEventId] = RunningParse(reader.parentEventId, gradleProject, variant)
      return false
    }

    // If there are no active parsers then we're done.
    if (parsers.isEmpty()) return false

    // If there is an end of build signal then flush, close, and clear active parsers.
    if (endOfBuildSignals.matches(line)) {
      for(parser in parsers.values) parser.close()
      parsers.clear()
      return false
    }

    // If there is an active parser for this parentId then send this line to it.
    parsers[reader.parentEventId]?.apply { parse(line, messageConsumer) }

    return true
  }

  /**
   * Receives build output for a single parentID. Typically this is STDOUT from a single Gradle task.
   */
  private data class RunningParse(
    private val parentId: Any,
    private val gradleProject: String,
    private val variant: String) : AutoCloseable {
    private val classier = NativeBuildOutputClassifier { message -> receiveClassification(message) }
    private lateinit var messageConsumer: Consumer<in BuildEvent>

    /**
     * Send one line of build output to the classifier.
     */
    fun parse(line: String, messageConsumer: Consumer<in BuildEvent>) {
      this.messageConsumer = messageConsumer
      classier.consume(line)
    }

    /**
     * Receives grouped lines that form a single message. Converts them in to Build Output tree nodes.
     */
    private fun receiveClassification(message: NativeBuildOutputClassifier.Message) {
      // Only diagnostics get forwarded as Build Tree nodes
      if (message !is NativeBuildOutputClassifier.Message.Diagnostic) return
      val commandLinePrefix = if (message.command != null) "${message.command}\n\n" else ""
      val kind = when (message.classification.kind) {
          ERROR -> MessageEvent.Kind.ERROR
          WARNING -> MessageEvent.Kind.WARNING
          INFO -> MessageEvent.Kind.INFO
        }
      val group = compilerMessageGroup(gradleProject, variant, message.abi)
      val detailedMessage = commandLinePrefix + message.lines.joinToString("\n")

      messageConsumer.accept(if (message.file != null && message.column != null && message.line != null) {
          val abiMessage = if (message.abi == null) "" else " [${message.abi}]"
          val position = FilePosition(File(message.file!!), message.line!! - 1, message.column!! - 1)
          FileMessageEventImpl(parentId, kind, group, message.body + abiMessage, detailedMessage, position)
        } else {
          MessageEventImpl(parentId, kind, group, message.body, detailedMessage)
        })
    }

    override fun close() = classier.close()
  }
}

