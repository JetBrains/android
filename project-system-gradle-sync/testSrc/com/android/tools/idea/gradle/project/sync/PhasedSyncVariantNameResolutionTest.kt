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

import com.android.builder.model.v2.dsl.BuildType
import com.android.builder.model.v2.dsl.ProductFlavor
import com.android.builder.model.v2.ide.BasicVariant
import com.android.builder.model.v2.models.AndroidDsl
import com.android.builder.model.v2.models.AndroidProject
import com.android.builder.model.v2.models.BasicAndroidProject
import com.android.builder.model.v2.models.Versions
import com.android.ide.common.repository.AgpVersion
import com.android.ide.gradle.model.GradlePluginModel
import com.android.ide.gradle.model.dependencies.DeclaredDependencies
import com.android.ide.gradle.model.impl.LegacyAndroidGradlePluginPropertiesImpl
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
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
        ProjectSetup(
          ":app",
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          listOf(":lib2", ":lib4"),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
        ),
        ProjectSetup(
          ":lib1",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
        ),
        ProjectSetup(
          ":lib2",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib1"),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
        ),
        ProjectSetup(
          ":lib3",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib1"),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
        ),
        ProjectSetup(
          ":lib4",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
        ),
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
        ProjectSetup(
          ":app",
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          listOf(":lib2", ":lib4"),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("special")),
        ),
        ProjectSetup(
          ":lib1",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "debug",
          listOf("debug", "release", "special"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("special")),
        ),
        ProjectSetup(
          ":lib2",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib1"),
          "debug",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("special")),
        ),
        ProjectSetup(
          ":lib3",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib1"),
          "release",
          listOf("debug", "release"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("special")),
        ),
        ProjectSetup(
          ":lib4",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "debug",
          listOf("debug", "release", "special"),
          listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("special")),
        ),
      )

    // Verify changing Lib3 variant propagates accordingly
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[3].moduleId) // Lib3 is the switched module
    whenever(switchVariantRequest.variantName).thenReturn("release") // Switching Lib3 to 'release'
    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    // Verify the selected variants.
    assertThat(selectedVariants[":app"]).isEqualTo("debug")
    assertThat(selectedVariants[":lib1"]).isEqualTo("release")
    assertThat(selectedVariants[":lib2"]).isEqualTo("debug")
    assertThat(selectedVariants[":lib3"]).isEqualTo("release")
    assertThat(selectedVariants[":lib4"]).isEqualTo("debug")

    // Verify changing App variant propagates through
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[0].moduleId) // App is the switched module
    whenever(switchVariantRequest.variantName).thenReturn("release") // Switching App to 'release'
    val selectedAppVariants = sortProjectsAndGetSelectedVariants(projects)
    // Verify the selected variants.
    assertThat(selectedAppVariants[":app"]).isEqualTo("release")
    assertThat(selectedAppVariants[":lib2"]).isEqualTo("release")
    assertThat(selectedAppVariants[":lib4"]).isEqualTo("release")
    assertThat(selectedAppVariants[":lib1"]).isEqualTo("release")
  }

  @Test
  fun `variant selection uses buildType matchingFallbacks`() {
    val appBuildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("qa", listOf("release")))
    val libBuildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"))
    val projects =
      listOf(
        ProjectSetup(
          ":app",
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          listOf(":lib"),
          "debug",
          listOf("debug", "qa"),
          appBuildTypes,
          emptyList(),
        ),
        ProjectSetup(
          ":lib",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "debug",
          listOf("debug", "release"),
          libBuildTypes,
          emptyList(),
        ),
      )

    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[0].moduleId) // Switch App variant.
    whenever(switchVariantRequest.variantName)
      .thenReturn("qa") // Request 'qa' for app, which should propagate to lib and use matchingFallbacks

    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedVariants[":app"]).isEqualTo("qa")
    assertThat(selectedVariants[":lib"]).isEqualTo("release")

    whenever(switchVariantRequest.variantName).thenReturn("debug") // Requesting 'debug' for app
    val changedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(changedVariants[":app"]).isEqualTo("debug")
    assertThat(changedVariants[":lib"]).isEqualTo("debug")
  }

  @Test
  fun `variant selection uses productFlavor matchingFallbacks`() {
    val appFlavors =
      listOf(
        TestProductFlavor("paid", "pricing", emptyList()),
        TestProductFlavor("free", "pricing", listOf("prod")),
        TestProductFlavor("member", "pricing", listOf("demo")),
      )
    val lib1Flavors = listOf(TestProductFlavor("demo", "pricing", listOf("paid", "abc")), TestProductFlavor("prod", "pricing"))
    val lib2Flavors = listOf(TestProductFlavor("demo", "pricing", listOf("paid", "random")), TestProductFlavor("prod", "pricing"))

    val appBuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("debug", "release")))
    val lib1BuildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"))
    val lib2BuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("release")))

    val projects =
      listOf(
        ProjectSetup(
          ":app",
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          listOf(":lib1", ":lib2"),
          "paidDebug",
          listOf("paidDebug", "paidRelease", "paidQa", "freeDebug", "freeRelease", "freeQa", "memberDebug", "memberRelease", "memberQa"),
          appBuildTypes,
          appFlavors,
        ),
        ProjectSetup(
          ":lib1",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "demoRelease",
          listOf("demoDebug", "demoRelease", "prodDebug", "prodRelease"),
          lib1BuildTypes,
          lib1Flavors,
        ),
        ProjectSetup(
          ":lib2",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib1"),
          "demoRelease",
          listOf("demoDebug", "demoRelease", "demoQa", "prodDebug", "prodRelease", "prodQa"),
          lib2BuildTypes,
          lib2Flavors,
        ),
      )

    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[0].moduleId) // App is the switched module using default variant here.
    whenever(switchVariantRequest.variantName).thenReturn("paidDebug")

    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Variant resolution conflict: Cannot find a variant for :lib2.")

    // Now, select a variant that will propagate back to the dependencies correctly.
    whenever(switchVariantRequest.variantName).thenReturn("freeDebug")
    val switchedVariantsMatching = sortProjectsAndGetSelectedVariants(projects)

    assertThat(switchedVariantsMatching[":app"]).isEqualTo("freeDebug")
    assertThat(switchedVariantsMatching[":lib1"])
      .isEqualTo("prodDebug") // This is the matching variant that resolves using matchingFallbacks resolution.
    assertThat(switchedVariantsMatching[":lib2"]).isEqualTo("prodDebug")

    // Now, switch variant for lib2. Expectation is that the variant propagation goes to lib1, while app variant is not changed.
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[2].moduleId)
    whenever(switchVariantRequest.variantName).thenReturn("prodRelease")

    val lib2SelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(lib2SelectedVariants[":app"]).isEqualTo("paidDebug")
    assertThat(lib2SelectedVariants[":lib1"])
      .isEqualTo(
        "prodRelease"
      ) // App has debug and release as fallbacks for qa, but lib2 has release only, so we pick release and get no variant conflict at all
    // (unlike what happens in non-phased Sync)
    assertThat(lib2SelectedVariants[":lib2"]).isEqualTo("prodRelease")

    // Now: we  test that we are doing the buildType fallbacks correctly, i.e. prioritizing the fallbacks base don the initial request.
    // If App requests the variant switching, then we prioritize its fallback requests and resolve according to variant switching source,
    // not according to how common they are or other projects dependencies.
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[0].moduleId)
    whenever(switchVariantRequest.variantName).thenReturn("freeQa")

    val sortedFallbacksSelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(sortedFallbacksSelectedVariants[":app"]).isEqualTo("freeQa")
    assertThat(sortedFallbacksSelectedVariants[":lib1"]).isEqualTo("prodDebug")
    assertThat(sortedFallbacksSelectedVariants[":lib2"]).isEqualTo("prodQa")

    // Again, test the same as above, but this time we switch lib2 variant, which should propagate to lib1, but not to app.
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[2].moduleId)
    whenever(switchVariantRequest.variantName).thenReturn("demoQa")

    val lib2FallbacksSelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    // This is the default variant because in this unit test we do not preserve the state of the projects between each of the switching
    // requests
    // because we reconstruct the variants each time.
    assertThat(lib2FallbacksSelectedVariants[":app"]).isEqualTo("paidDebug")
    assertThat(lib2FallbacksSelectedVariants[":lib1"]).isEqualTo("demoRelease")
    assertThat(lib2FallbacksSelectedVariants[":lib2"]).isEqualTo("demoQa")
  }

  @Test
  fun `verify that we do handle the corner cases correctly`() {
    // App has productFlavors, but library doesn't. The expected behavior is to resolve correctly and not fail.
    val appFlavors =
      listOf(TestProductFlavor("paid", "pricing", listOf("member")), TestProductFlavor("free", "pricing", listOf("demo", "member")))
    val lib2Flavors = listOf(TestProductFlavor("demo", "pricing"), TestProductFlavor("member", "pricing"))
    val lib3Flavors = listOf(TestProductFlavor("member", "pricing"))

    // I think that this is probably covered by missingDimensionStrategy ? because in our case there won't be any confusion when resolving
    // lib
    val appBuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("blob", "release")))
    val libBuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("blob", listOf("release")))
    val lib2BuildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"))
    val projects =
      listOf(
        ProjectSetup(
          ":app",
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          listOf(":lib"),
          "paidDebug",
          listOf("paidDebug", "freeDebug", "paidRelease", "freeRelease", "paidQa", "freeQa"),
          appBuildTypes,
          appFlavors,
        ),
        ProjectSetup(
          ":lib",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          listOf(":lib2", ":lib3"),
          "debug",
          listOf("debug", "release", "blob"),
          libBuildTypes,
          emptyList(),
        ),
        ProjectSetup(
          ":lib3",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "memberDebug",
          listOf("memberDebug", "memberRelease", "memberBlob"),
          libBuildTypes,
          lib3Flavors,
        ),
        ProjectSetup(
          ":lib2",
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          emptyList(),
          "demoDebug",
          listOf("demoDebug", "memberDebug", "demoRelease", "memberRelease"),
          lib2BuildTypes,
          lib2Flavors,
        ),
      )

    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[0].moduleId) // Switch App variant.
    whenever(switchVariantRequest.variantName)
      .thenReturn(
        "freeRelease"
      ) // Request free productFlavor for app, which should propagate to lib that doesn't have any productFlavors, and lib2 by using the
    // correct matchingFallbacks.

    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedVariants[":app"]).isEqualTo("freeRelease")
    assertThat(selectedVariants[":lib"]).isEqualTo("release")
    assertThat(selectedVariants[":lib2"]).isEqualTo("demoRelease") // Check transitive propagation to lib2
    assertThat(selectedVariants[":lib3"]).isEqualTo("memberRelease") // Check transitive propagation to lib3

    whenever(switchVariantRequest.variantName).thenReturn("paidQa") // Requesting 'qa' for app
    val changedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(changedVariants[":app"]).isEqualTo("paidQa")
    assertThat(changedVariants[":lib"])
      .isEqualTo("blob") // we propagate to blob for lib, which is the first in the list of matchingFallback
    assertThat(changedVariants[":lib2"])
      .isEqualTo("memberRelease") // We propagate to release buildType, which is the second in the list, but the only match for lib2.
    assertThat(changedVariants[":lib3"]).isEqualTo("memberBlob")

    // Now, check that propagation from lib1 to lib2 also works.
    whenever(switchVariantRequest.moduleId).thenReturn(":" + projects[1].moduleId) // Switch Lib variant.
    whenever(switchVariantRequest.variantName)
      .thenReturn(
        "release"
      ) // Request release variant for lib, which should propagate to lib3 but fail to resolve lib2 because of the productFlavor ambiguity.

    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Cannot resolve variant release. Cannot resolve ambiguity of productFlavors for :lib2.")
  }

  private fun sortProjectsAndGetSelectedVariants(projectsSetups: List<ProjectSetup>): Map<String, String> {
    val projectDataList =
      projectsSetups.map { projectSetup ->
        val projectParamsMock = createMocksForProject(projectSetup)
        val androidProjectData =
          AndroidProjectData(
            versions = Mockito.mock(Versions::class.java),
            modelVersions = projectParamsMock.modelVersions,
            basicAndroidProject = projectParamsMock.basicAndroidProject,
            androidProject = Mockito.mock(AndroidProject::class.java),
            androidDsl = projectParamsMock.androidDsl,
            declaredDependencies = projectParamsMock.declaredDependencies,
            gradlePluginModel = Mockito.mock(GradlePluginModel::class.java),
            gradleTaskModel = Mockito.mock(GradleTaskModel::class.java),
            ideAndroidProject =
              Mockito.mock(IdeAndroidProjectImpl::class.java).apply { whenever(projectType).thenReturn(projectSetup.projectType) },
            selectedVariantName =
              switchVariantRequest.takeIf { it.moduleId == projectParamsMock.basicGradleProject.moduleId() }?.variantName
                ?: projectSetup.defaultVariant,
            shouldSkipRuntimeClassPathForLibraries = false,
            legacyAndroidGradlePluginProperties = projectParamsMock.legacyAndroidGradlePluginPropertiesImpl,
          )
        projectParamsMock.basicGradleProject to androidProjectData
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
    val modelVersions = ModelVersions(agp = AgpVersion.parse("9.0.0"), modelVersion = ModelVersion(20, 0), minimumModelConsumer = null)
    val legacyAndroidGradlePluginPropertiesImpl =
      LegacyAndroidGradlePluginPropertiesImpl(emptyMap(), null, null, false, emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

    val variants =
      project.variants.map { variantName ->
        val variant = Mockito.mock(BasicVariant::class.java)
        whenever(variant.name).thenReturn(variantName)
        whenever(variant.buildType)
          .thenReturn(project.buildTypes.firstOrNull { variantName.lowercase().endsWith(it.name) }?.name ?: variantName)
        whenever(variant.productFlavors)
          .thenReturn(project.productFlavors.filter { variantName.lowercase().contains(it.name) }.map { it.name })
        variant
      }

    val buildTypes =
      project.buildTypes.map { testBuildType ->
        val buildType = Mockito.mock(BuildType::class.java)
        whenever(buildType.name).thenReturn(testBuildType.name)
        whenever(buildType.matchingFallbacks).thenReturn(testBuildType.matchingFallbacks)
        buildType
      }

    val dimensions = mutableListOf<String>()
    val productFlavors =
      project.productFlavors.map { testProductFlavor ->
        dimensions.add(testProductFlavor.dimension)
        val flavor = Mockito.mock(ProductFlavor::class.java)
        whenever(flavor.name).thenReturn(testProductFlavor.name)
        whenever(flavor.dimension).thenReturn(testProductFlavor.dimension)
        whenever(flavor.matchingFallbacks).thenReturn(testProductFlavor.matchingFallbacks)
        flavor
      }

    whenever(gradleProject.path).thenReturn(project.moduleId)
    whenever(gradleProject.projectIdentifier).thenReturn(projectIdentifier)
    whenever(projectIdentifier.buildIdentifier).thenReturn(buildIdentifier)
    whenever(buildIdentifier.rootDir).thenReturn(File(""))
    whenever(basicAndroidProject.variants).thenReturn(variants)
    whenever(androidDsl.buildTypes).thenReturn(buildTypes)
    whenever(androidDsl.productFlavors).thenReturn(productFlavors)
    whenever(androidDsl.flavorDimensions).thenReturn(dimensions)
    whenever(declaredDependencies.allOutgoingProjectDependencies).thenReturn(project.dependencies)

    return Parameters(
      gradleProject,
      basicAndroidProject,
      androidDsl,
      declaredDependencies,
      legacyAndroidGradlePluginPropertiesImpl,
      modelVersions,
    )
  }

  private data class ProjectSetup(
    val moduleId: String,
    val projectType: IdeAndroidProjectType,
    val dependencies: List<String>,
    val defaultVariant: String,
    val variants: List<String>,
    val buildTypes: List<TestAndroidBuildType>,
    val productFlavors: List<TestProductFlavor> = emptyList(),
  )

  private data class TestAndroidBuildType(val name: String, val matchingFallbacks: List<String> = emptyList())

  private data class TestProductFlavor(val name: String, val dimension: String, val matchingFallbacks: List<String> = emptyList())

  private data class Parameters(
    val basicGradleProject: BasicGradleProject,
    val basicAndroidProject: BasicAndroidProject,
    val androidDsl: AndroidDsl,
    val declaredDependencies: DeclaredDependencies,
    val legacyAndroidGradlePluginPropertiesImpl: LegacyAndroidGradlePluginPropertiesImpl,
    val modelVersions: ModelVersions,
  )
}
