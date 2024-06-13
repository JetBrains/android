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
package com.android.tools.idea.gradle.project.build.output

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.quickFixes.AbstractSetJavaLanguageLevelQuickFix
import com.android.tools.idea.gradle.project.sync.quickFixes.OpenLinkQuickFix
import com.google.common.truth.Truth.assertThat
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.pom.java.LanguageLevel
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.util.function.Consumer

class KotlincWithQuickFixesParserTest {
  @Mock
  lateinit var reader: BuildOutputInstantReader

  @Mock
  lateinit var messageConsumer: Consumer<in BuildEvent?>

  private lateinit var parser: KotlincWithQuickFixesParser

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
    parser = KotlincWithQuickFixesParser()
    whenever(reader.parentEventId).thenReturn("testId")
  }

  @Test
  fun `Cannot inline bytecode with JVM target 8 has correct quickfixes`() {
    verifyJvmTarget8QuickFixes(
      "e: /path/file.kt: (42, 47): Cannot inline bytecode built with JVM target 1.8 into bytecode that is being built with JVM target 1.6. Please specify proper '-jvm-target' option")
  }

  @Test
  fun `Calls to static methods prohibited in JVM target 6 has correct quickfixes`() {
    verifyJvmTarget8QuickFixes(
      "e: /path/file.kt: (15, 19): Calls to static methods in Java interfaces are prohibited in JVM target 1.6. Recompile with '-jvm-target 1.8'")
  }

  private fun verifyJvmTarget8QuickFixes(errorLine: String) {
    val captor = ArgumentCaptor.forClass(BuildEvent::class.java)
    assertThat(parser.parse(errorLine, reader, messageConsumer)).isTrue()
    Mockito.verify(messageConsumer).accept(captor.capture())
    val events = captor.allValues
    assertThat(events).hasSize(1)
    assertThat(events[0]).isInstanceOf(BuildIssueEvent::class.java)
    val quickFixes = (events[0] as BuildIssueEvent).issue.quickFixes
    assertThat(quickFixes).hasSize(2)
    val setJvmQuickFixes = quickFixes.filterIsInstance(AbstractSetJavaLanguageLevelQuickFix::class.java)
    assertThat(setJvmQuickFixes).isNotEmpty()
    assertThat(setJvmQuickFixes.map { it.level }).containsAllIn(arrayOf(LanguageLevel.JDK_1_8))
    assertThat(quickFixes.filterIsInstance(OpenLinkQuickFix::class.java)).isNotEmpty()
  }
}