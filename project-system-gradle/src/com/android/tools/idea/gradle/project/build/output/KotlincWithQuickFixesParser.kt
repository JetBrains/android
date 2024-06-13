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
package com.android.tools.idea.gradle.project.build.output

import com.android.tools.idea.gradle.project.sync.idea.issues.BuildIssueComposer
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.SetJavaLanguageLevelAllQuickFix
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.output.KotlincOutputParser
import com.intellij.pom.java.LanguageLevel
import java.util.function.Consumer

const val JVM_TARGET_FIX_BYTECODE = "Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target"
const val JVM_TARGET_FIX_SPECIFY_OPTION = "Please specify proper '-jvm-target' option"
const val JVM_TARGET_FIX_STATIC = "Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'"
const val JAVA_8_SUPPORT_LINK = "https://developer.android.com/studio/write/java8-support"

/**
 * Wrapper class for [KotlincOutputParser] that adds quickfixes based on the error messages.
 *
 * Current quick fixes:
 *   - Errors that contain (JVM_TARGET_FIX_BYTECODE and JVM_TARGET_JVM_TARGET_SPECIFY_OPTION) or JVM_TARGET_FIX_STATIC display a [SetJavaLanguageLevelAllQuickFix]
 */
class KotlincWithQuickFixesParser : BuildOutputParser {
  private val myKotlinParser = KotlincOutputParser()
  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    val wrappedConsumer = Consumer<BuildEvent> {
      if (it != null) {
        messageConsumer.accept(addQuickfixes(it))
      }
    }
    return myKotlinParser.parse(line, reader, wrappedConsumer)
  }

  private fun addQuickfixes(originalEvent: BuildEvent): BuildEvent {
    val originalMessage = originalEvent.message
    if ((originalMessage.contains(JVM_TARGET_FIX_BYTECODE) && originalMessage.contains(JVM_TARGET_FIX_SPECIFY_OPTION)) ||
        (originalMessage.contains(JVM_TARGET_FIX_STATIC))) {
      if (originalEvent is MessageEvent) {
        val buildIssueComposer = BuildIssueComposer(originalEvent.description!!.trim(), originalMessage)
        buildIssueComposer.addDescription("Adding support for Java 8 language features could solve this issue.")
        buildIssueComposer.addQuickFix(SetJavaLanguageLevelAllQuickFix(LanguageLevel.JDK_1_8, setJvmTarget = true))
        buildIssueComposer.addQuickFix("More information...", OpenLinkQuickFix(JAVA_8_SUPPORT_LINK))
        return BuildIssueEventImpl(originalEvent.parentId!!, buildIssueComposer.composeBuildIssue(), originalEvent.kind)
      }
    }
    return originalEvent
  }
}