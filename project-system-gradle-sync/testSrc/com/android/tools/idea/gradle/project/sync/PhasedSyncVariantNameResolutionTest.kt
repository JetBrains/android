/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.dependencies.DeclaredDependencies
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.BasicGradleProject
import org.jetbrains.plugins.gradle.model.GradleTaskModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider.GradleModelConsumer
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

class PhasedSyncVariantNameResolutionTest {

  @Mock private lateinit var syncOptions: SingleVariantSyncActionOptions
  @Mock private lateinit var switchVariantRequest: SwitchVariantRequest
  @Mock private lateinit var modelConsumer: GradleModelConsumer

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    whenever(syncOptions.switchVariantRequest).thenReturn(switchVariantRequest)
  }

  @Test
  fun `projects data sorted according to incoming dependencies`() {
    val projects =
      listOf(
        ProjectSetup(":app", IdeAndroidProjectType.PROJECT_TYPE_APP, listOf(":lib2", ":lib4"), "debug", listOf("debug", "release")),
        ProjectSetup(":lib1", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, emptyList(), "debug", listOf("debug", "release")),
        ProjectSetup(":lib2", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, listOf(":lib1"), "debug", listOf("debug", "release")),
        ProjectSetup(":lib3", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, listOf(":lib1"), "debug", listOf("debug", "release")),
        ProjectSetup(":lib4", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, emptyList(), "debug", listOf("debug", "release")),
      )

    // Setup: lib3 is the switch target.
    whenever(switchVariantRequest.moduleId).thenReturn(":lib3")

    val nodes = projects.map { project -> ProjectNode(project.moduleId, project.moduleId, project.projectType, project.dependencies) }

    val sortedBatches = sortProjectsByPriority(nodes, { it }, syncOptions)

    // Expected Layers:
    // Batch 0: :lib3 (Switch target)
    // Batch 1: :app (App module)
    // Batch 2: :lib2, :lib4 (Direct dependencies of :app)
    // Batch 3: :lib1 (Transitive dependency of :lib2 and :lib3)

    assertThat(sortedBatches[0]!!.map { it.path }).containsExactly(":lib3")
    assertThat(sortedBatches[1]!!.map { it.path }).containsExactly(":app")
    assertThat(sortedBatches[2]!!.map { it.path }).containsExactly(":lib2", ":lib4")
    assertThat(sortedBatches[3]!!.map { it.path }).containsExactly(":lib1")
  }

  @Test
  fun `projects variant selected and propagated based on priority`() {
    // Setup:
    // :app (Priority 1) -> :lib2, :lib4
    // :lib2 (Priority 2) -> :lib1
    // :lib3 (Priority 2) -> :lib1
    // :lib4 (Priority 2)
    val projects =
      listOf(
        ProjectSetup(":app", IdeAndroidProjectType.PROJECT_TYPE_APP, listOf(":lib2", ":lib4"), "debug", listOf("debug", "release")),
        ProjectSetup(":lib1", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, emptyList(), "debug", listOf("debug", "release", "special")),
        ProjectSetup(":lib2", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, listOf(":lib1"), "debug", listOf("debug", "release")),
        ProjectSetup(":lib3", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, listOf(":lib1"), "release", listOf("debug", "release")),
        ProjectSetup(":lib4", IdeAndroidProjectType.PROJECT_TYPE_LIBRARY, emptyList(), "debug", listOf("debug", "release", "special")),
      )

    // Verify changing Lib3 variant propagates accordingly
    whenever(switchVariantRequest.moduleId).thenReturn(projects[3].moduleId) // Lib3 is the switched module
    whenever(switchVariantRequest.variantName).thenReturn("release") // Switching Lib3 to 'release'
    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    // Verify the selected variants.
    assertThat(selectedVariants[":app"]).isEqualTo("debug")
    assertThat(selectedVariants[":lib1"]).isEqualTo("release")
    assertThat(selectedVariants[":lib2"]).isEqualTo("debug")
    assertThat(selectedVariants[":lib3"]).isEqualTo("release")
    assertThat(selectedVariants[":lib4"]).isEqualTo("debug")

    // Verify changing App variant propagates through
    whenever(switchVariantRequest.moduleId).thenReturn(projects[0].moduleId) // App is the switched module
    whenever(switchVariantRequest.variantName).thenReturn("release") // Switching App to 'release'
    val selectedAppVariants = sortProjectsAndGetSelectedVariants(projects)
    // Verify the selected variants.
    assertThat(selectedAppVariants[":app"]).isEqualTo("release")
    assertThat(selectedAppVariants[":lib2"]).isEqualTo("release")
    assertThat(selectedAppVariants[":lib4"]).isEqualTo("release")
    // assertThat(selectedAppVariants[":lib1"]).isEqualTo("release")
  }

  private fun sortProjectsAndGetSelectedVariants(projects: List<ProjectSetup>): Map<String, String> {
    val projectDataList =
      projects.map { setup ->
        val params = createMocksForProject(setup)
        val androidProjectData =
          AndroidProjectData(
            versions = Mockito.mock(Versions::class.java),
            modelVersions = Mockito.mock(ModelVersions::class.java),
            basicAndroidProject = params.basicAndroidProject,
            androidProject = Mockito.mock(AndroidProject::class.java),
            androidDsl = params.androidDsl,
            declaredDependencies = params.declaredDependencies,
            gradlePluginModel = Mockito.mock(GradlePluginModel::class.java),
            gradleTaskModel = Mockito.mock(GradleTaskModel::class.java),
            ideAndroidProject =
              Mockito.mock(IdeAndroidProjectImpl::class.java).apply { whenever(projectType).thenReturn(setup.projectType) },
            selectedVariantName = switchVariantRequest.takeIf { it.moduleId == setup.moduleId }?.variantName ?: setup.defaultVariant,
            shouldSkipRuntimeClassPathForLibraries = false,
          )
        params.basicGradleProject to androidProjectData
      }

    val cachedModels = ModelProviderCachedData(disableLegacyModelProvidersForSupportedProjects = false)
    setupProjectsVariantsAndConsume(projectDataList, syncOptions, modelConsumer, cachedModels)

    return projectDataList.associate { it.first.path to it.second.selectedVariantName }
  }

  private fun createMocksForProject(project: ProjectSetup): Parameters {
    val gradleProject = Mockito.mock(BasicGradleProject::class.java)
    val projectIdentifier = Mockito.mock(ProjectIdentifier::class.java)
    val buildIdentifier = Mockito.mock(BuildIdentifier::class.java)
    val basicAndroidProject = Mockito.mock(BasicAndroidProject::class.java)
    val androidDsl = Mockito.mock(AndroidDsl::class.java)
    val declaredDependencies = Mockito.mock(DeclaredDependencies::class.java)

    val variants =
      project.variants.map { variantName ->
        val variant = Mockito.mock(BasicVariant::class.java)
        whenever(variant.name).thenReturn(variantName)
        variant
      }

    whenever(gradleProject.path).thenReturn(project.moduleId)
    whenever(gradleProject.projectIdentifier).thenReturn(projectIdentifier)
    whenever(projectIdentifier.buildIdentifier).thenReturn(buildIdentifier)
    whenever(buildIdentifier.rootDir).thenReturn(File("/tmp"))
    whenever(basicAndroidProject.variants).thenReturn(variants)
    whenever(androidDsl.buildTypes).thenReturn(emptyList()) // Will need this later for fallbacks.
    whenever(androidDsl.productFlavors).thenReturn(emptyList()) // Will need this later.
    whenever(declaredDependencies.allOutgoingProjectDependencies).thenReturn(project.dependencies)

    return Parameters(gradleProject, basicAndroidProject, androidDsl, declaredDependencies)
  }

  private data class ProjectSetup(
    val moduleId: String,
    val projectType: IdeAndroidProjectType,
    val dependencies: List<String>,
    val defaultVariant: String,
    val variants: List<String>,
  )

  private data class Parameters(
    val basicGradleProject: BasicGradleProject,
    val basicAndroidProject: BasicAndroidProject,
    val androidDsl: AndroidDsl,
    val declaredDependencies: DeclaredDependencies,
  )
}
