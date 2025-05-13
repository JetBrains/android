/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.FOOJAY_RESOLVER_CONVENTION_NAME
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class NewProjectTemplateRendererTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val multiTemplateRenderer: MultiTemplateRenderer
    get() = MultiTemplateRenderer { renderer ->
      object : Task.Modal(projectRule.project, "Test", false) {
        override fun run(indicator: ProgressIndicator) {
          renderer(project)
        }
      }.queue()
    }

  @Before
  fun setUp() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").setValue(true)
  }

  @After
  fun tearDown() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").resetToDefault()
  }

  @Test
  fun `Given gradle version without toolchain as default When create project Then Foojay plugin is not applied`() {
    val render = createNewProjectTemplateRender("8.13")
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(false)
  }

  @Test
  fun `Given gradle version with toolchain as default When create project using KTS Then Foojay plugin is applied`() {
    val render = createNewProjectTemplateRender("9.0", true)
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(true)
  }

  @Test
  fun `Given gradle version with toolchain as default When create project not using KTS Then Foojay plugin is applied`() {
    val render = createNewProjectTemplateRender("9.1", true)
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(true)
  }

  @Test
  fun `Given gradle version with toolchain as default but disable registry When create project Then Foojay plugin is not applied`() {
    Registry.get("gradle.daemon.jvm.criteria.new.project").setValue(false)

    val render = createNewProjectTemplateRender("9.0")
    multiTemplateRenderer.requestRender(render)

    assertFoojayPlugin(false)
  }

  private fun createNewProjectTemplateRender(
    gradleVersion: String, useGradleKts: Boolean = false
  ) : NewProjectModel.ProjectTemplateRenderer {
    val newProjectModel = spy(NewProjectModel())
    val render = newProjectModel.ProjectTemplateRenderer()
    val projectTemplateDataBuilder = spy(newProjectModel.projectTemplateDataBuilder)
    val projectTemplateData = createSimpleProjectTemplateData(gradleVersion)

    newProjectModel.project = projectRule.project
    doReturn(projectTemplateData).whenever(projectTemplateDataBuilder).build()
    doReturn(projectTemplateDataBuilder).whenever(newProjectModel).projectTemplateDataBuilder
    doReturn(BoolValueProperty(useGradleKts)).whenever(newProjectModel).useGradleKts

    return render
  }

  private fun createSimpleProjectTemplateData(
    gradleVersion: String
  ) = ProjectTemplateData(
    false,
    AgpVersion.parse("8.10.0"),
    GradleVersion.version(gradleVersion),
    listOf(),
    null,
    Language.Java,
    "2.0.0",
    projectRule.project.guessProjectDir()!!.toIoFile(),
    "com.test.packagename",
    mapOf(),
    null,
    null,
    true,
  )

  private fun assertFoojayPlugin(isApplied: Boolean) {
    assertEquals(isApplied, ProjectBuildModel.get(projectRule.project).projectSettingsModel!!.plugins().declaredProperties.any {
      it.valueAsString()!!.contains(FOOJAY_RESOLVER_CONVENTION_NAME)
    })
  }
}