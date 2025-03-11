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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dcl.lang.sync.ProjectSchemas
import com.android.tools.idea.gradle.dcl.lang.sync.SettingsSchemas
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.testFramework.ApplicationRule
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class GradleSchemaProjectResolverTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun testEmptyResultWithoutFlag() {
    val provider = GradleSchemaProjectResolver()
    assertThat(provider.getModelProviders()).isEmpty()

    val nextResolver = mock(GradleProjectResolverExtension::class.java)
    val project = mock(IdeaProject::class.java)
    val dataNode: DataNode<ProjectData?> = mock(DataNode::class.java) as DataNode<ProjectData?>

    provider.setNext(nextResolver)
    provider.populateProjectExtraModels(project, dataNode)

    verify(nextResolver).populateProjectExtraModels(project, dataNode)
  }

  @Test
  fun testResultWithDeclarativeFlag() {
    DeclarativeIdeSupport.override(true)
    try {
      val provider = GradleSchemaProjectResolver()
      assertThat(provider.getModelProviders()).isNotEmpty()

      val nextResolver = mock(GradleProjectResolverExtension::class.java)
      val project = mock(IdeaProject::class.java)
      val dataNode: DataNode<ProjectData?> = mock(DataNode::class.java) as DataNode<ProjectData?>
      val resolverContext = mock(ProjectResolverContext::class.java)
      val projectSchema = mock(ProjectSchemas::class.java)
      val settingsSchemas = mock(SettingsSchemas::class.java)

      Mockito.`when`(resolverContext.getRootModel(ProjectSchemas::class.java)).thenReturn(projectSchema)
      Mockito.`when`(resolverContext.getRootModel(SettingsSchemas::class.java)).thenReturn(settingsSchemas)
      provider.setProjectResolverContext(resolverContext)
      provider.setNext(nextResolver)

      provider.populateProjectExtraModels(project, dataNode)
      verify(dataNode, times(1)).createChild(GradleSchemaProjectResolver.DECLARATIVE_PROJECT_SCHEMAS, projectSchema)
      verify(dataNode, times(1)).createChild(GradleSchemaProjectResolver.DECLARATIVE_SETTINGS_SCHEMAS, settingsSchemas)
      verify(nextResolver).populateProjectExtraModels(project, dataNode)
    }
    finally {
      DeclarativeIdeSupport.clearOverride()
    }
  }
}