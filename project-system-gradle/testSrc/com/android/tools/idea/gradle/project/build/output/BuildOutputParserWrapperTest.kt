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

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.testing.disposable
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals

class BuildOutputParserWrapperTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  private lateinit var myProject: Project

  @get:Rule
  val projectRule = ProjectRule()

  private lateinit var myParserWrapper: BuildOutputParserWrapper
  private lateinit var messageEvent: MessageEvent
  private lateinit var outputParserManager: BuildOutputParserManager

  @Before
  fun setUp() {
    val studioBot = object : StudioBot.StubStudioBot() {
      override fun isAvailable(): Boolean = true
    }
    ApplicationManager.getApplication()
      .replaceService(StudioBot::class.java, studioBot, projectRule.disposable)
    MockitoAnnotations.initMocks(this)
    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer?.accept(messageEvent)
      true
    }
    myParserWrapper = BuildOutputParserWrapper(parser)
    outputParserManager = BuildOutputParserManager(myProject, listOf(myParserWrapper))

    whenever(myProject.basePath).thenReturn("test")

    val moduleManager = Mockito.mock(ModuleManager::class.java)
    whenever(myProject.getService(ModuleManager::class.java)).thenReturn(moduleManager)
    whenever(moduleManager.modules).thenReturn(emptyArray<Module>())
  }

  /** Regression test for b/323135834. */
  @Test
  fun `test build url injector`() {
    val folder = temporaryFolder.newFolder("test")
    val id = MockitoKt.mock<ExternalSystemTaskId>()
    whenever(id.type).thenReturn(ExternalSystemTaskType.RESOLVE_PROJECT)
    messageEvent = FileMessageEventImpl(id, MessageEvent.Kind.ERROR, "Compiler", "!!some error message!!", "Detailed error message",
                                        FilePosition(FileUtils.join(folder, "main", "src", "main.java"), 1, 2))
    myParserWrapper.parse(null, null) { event ->
      val expected = """
        Detailed error message

        >> Ask Gemini !!some error message!!
      """
      assertEquals(expected.trimIndent(), event.description)

    }

  }

}