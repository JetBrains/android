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
import com.android.builder.model.v2.ide.ProjectType
import com.android.builder.model.v2.ide.TestedTargetVariant
import com.android.builder.model.v2.ide.Variant
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
  fun `projects data sorted according to topological layering`() {
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib2", ":lib4"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = emptyList(),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = emptyList(),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = emptyList(),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = emptyList(),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib4",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = emptyList(),
          productFlavors = emptyList(),
        ),
      )

    // Setup: lib3 is the switch target.
    setSwitchVariantRequest(":lib3", "")

    val nodes =
      projects.map { project ->
        val params = createMocksForProject(project)
        ProjectNode(project.moduleId, params.basicGradleProject.moduleId(), project.projectType, project.dependencies)
      }

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
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib2", ":lib4"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "special"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("special"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib1"),
          defaultVariant = "release",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib4",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "special"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("special"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
      )

    setSwitchVariantRequest(":lib3", "release")
    val selectedLib3Variants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(selectedLib3Variants[":lib3"]).isEqualTo("release")
    assertThat(selectedLib3Variants[":app"]).isEqualTo("debug")
    assertThat(selectedLib3Variants[":lib2"]).isEqualTo("debug")
    assertThat(selectedLib3Variants[":lib4"]).isEqualTo("debug")
    assertThat(selectedLib3Variants[":lib1"]).isEqualTo("release")

    setSwitchVariantRequest(":app", "release")
    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(selectedVariants[":app"]).isEqualTo("release")
    assertThat(selectedVariants[":lib1"]).isEqualTo("release")
    assertThat(selectedVariants[":lib2"]).isEqualTo("release")
    assertThat(selectedVariants[":lib3"]).isEqualTo("release")
    assertThat(selectedVariants[":lib4"]).isEqualTo("release")
  }

  @Test
  fun `variant selection uses buildType matchingFallbacks`() {
    // Setup:
    // :app (Priority 1) -> :lib
    // :app has a qa buildType that defines release as a fallback
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "debug",
          variants = listOf("debug", "qa"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("qa", listOf("release"))),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
      )

    // Request 'qa' for app, which should propagate to lib and use matchingFallbacks
    setSwitchVariantRequest(":app", "qa")

    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedVariants[":app"]).isEqualTo("qa")
    assertThat(selectedVariants[":lib"]).isEqualTo("release")

    setSwitchVariantRequest(":app", "debug") // Requesting 'debug' for app
    val changedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(changedVariants[":app"]).isEqualTo("debug")
    assertThat(changedVariants[":lib"]).isEqualTo("debug")
  }

  @Test
  fun `variant selection uses productFlavor matchingFallbacks`() {
    // Setup:
    // :app -> :lib1, :lib2
    // :lib2 -> :lib1
    // :app has a 'free' productFlavor that defines 'prod' as a fallback, and 'member' productFlavor with 'demo' as a fallback
    val appFlavors =
      listOf(
        TestProductFlavor("paid", "pricing", emptyList()),
        TestProductFlavor("free", "pricing", listOf("prod")),
        TestProductFlavor("member", "pricing", listOf("demo")),
      )
    val lib1Flavors =
      listOf(
        TestProductFlavor("demo", "pricing", listOf("paid", "abc")),
        TestProductFlavor("prod", "pricing"),
        TestProductFlavor("paid", "pricing"),
      ) // lib1 now has a direct match for 'paid'
    val lib2Flavors = listOf(TestProductFlavor("demo", "pricing", listOf("paid", "random")), TestProductFlavor("prod", "pricing"))

    val appBuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("debug", "release")))
    val lib1BuildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"))
    val lib2BuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("release")))

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib1", ":lib2"),
          defaultVariant = "paidDebug",
          variants =
            listOf("paidDebug", "paidRelease", "paidQa", "freeDebug", "freeRelease", "freeQa", "memberDebug", "memberRelease", "memberQa"),
          buildTypes = appBuildTypes,
          productFlavors = appFlavors,
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "demoRelease",
          variants = listOf("demoDebug", "demoRelease", "prodDebug", "prodRelease", "paidDebug"), // lib1 has paidDebug variant
          buildTypes = lib1BuildTypes,
          productFlavors = lib1Flavors,
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib1"),
          defaultVariant = "demoRelease",
          variants = listOf("demoDebug", "demoRelease", "demoQa", "prodDebug", "prodRelease", "prodQa"),
          buildTypes = lib2BuildTypes,
          productFlavors = lib2Flavors,
        ),
        ProjectSetup(
          moduleId = ":app2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "qa"),
          buildTypes =
            listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("release"))),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib4"),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "qa"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("debug"))),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib4",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
      )
    setSwitchVariantRequest(":app", "paidRelease") // App is the switched module using default variant here.

    // Check that we indeed failed to resolve lib2 because there was no matchingFallback specified for paid by APP.
    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Variant Conflict: Could not resolve ProductFlavors ambiguity for project: :lib2.")

    // Now, select a variant that will propagate back to the dependencies correctly.
    setSwitchVariantRequest(":app", "freeDebug")
    val switchedVariantsMatching = sortProjectsAndGetSelectedVariants(projects)

    assertThat(switchedVariantsMatching[":app"]).isEqualTo("freeDebug")
    // This is the matching variant that resolves using matchingFallbacks resolution.
    assertThat(switchedVariantsMatching[":lib1"]).isEqualTo("prodDebug")
    assertThat(switchedVariantsMatching[":lib2"]).isEqualTo("prodDebug")

    // Now, switch variant for lib2. Expectation is that the variant propagation goes to lib1, while app variant is not changed.
    setSwitchVariantRequest(":lib2", "prodRelease")

    val lib2SelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(lib2SelectedVariants[":app"]).isEqualTo("paidDebug")
    // App has debug and release as fallbacks for qa, but lib2 has release only, so we pick release and get no variant conflict at all
    assertThat(lib2SelectedVariants[":lib1"]).isEqualTo("prodRelease")
    // (unlike what happens in non-phased Sync)
    assertThat(lib2SelectedVariants[":lib2"]).isEqualTo("prodRelease")

    // Now: we  test that we are doing the buildType fallbacks correctly, i.e. prioritizing the fallbacks based on the initial request.
    // If App requests the variant switching, then we prioritize its fallback requests and resolve according to variant switching source,
    // not according to how common they are or other projects dependencies.
    setSwitchVariantRequest(":app", "freeQa")

    val sortedFallbacksSelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    assertThat(sortedFallbacksSelectedVariants[":app"]).isEqualTo("freeQa")
    assertThat(sortedFallbacksSelectedVariants[":lib1"]).isEqualTo("prodDebug")
    assertThat(sortedFallbacksSelectedVariants[":lib2"]).isEqualTo("prodQa")

    // Again, test the same as above, but this time we switch lib2 variant, which should propagate to lib1, but not to app.
    setSwitchVariantRequest(":lib2", "demoQa")

    val lib2FallbacksSelectedVariants = sortProjectsAndGetSelectedVariants(projects)
    // This is the default variant because in this unit test we do not preserve the state of the projects between each of the switching
    // requests because we reconstruct the variants each time.
    assertThat(lib2FallbacksSelectedVariants[":app"]).isEqualTo("paidDebug")
    assertThat(lib2FallbacksSelectedVariants[":lib1"]).isEqualTo("demoRelease")
    assertThat(lib2FallbacksSelectedVariants[":lib2"]).isEqualTo("demoQa")

    // Now, Switch Variant for Lib3 and expect that the matchingFallbacks strategy used would be from Lib3 and not from App2 (that gets
    // resolved first).
    setSwitchVariantRequest(":lib3", "qa")

    val lib3FallbacksSelectedVariants = sortProjectsAndGetSelectedVariants(projects.filter { it.moduleId != ":lib2" })
    assertThat(lib3FallbacksSelectedVariants[":lib3"]).isEqualTo("qa")
    assertThat(lib3FallbacksSelectedVariants[":lib4"]).isEqualTo("debug")
  }

  @Test
  fun `verify that we do not pass intermediate projects missingDimensionStrategy attributes through the dependency graph`() {
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib2", ":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib5"),
          defaultVariant = "release",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("dim5" to listOf("flav51")),
        ),
        ProjectSetup(
          moduleId = ":lib5",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "flav52Debug",
          variants = listOf("flav51Debug", "flav51Release", "flav52Debug", "flav52Release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = listOf(TestProductFlavor("flav51", "dim5"), TestProductFlavor("flav52", "dim5")),
        ),
      )

    setSwitchVariantRequest(":app", "release")
    val newException = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(newException).hasMessageThat().contains("Variant Conflict: Could not resolve ProductFlavors ambiguity for project: :lib5.")
  }

  @Test
  fun `verify that we do not pass intermediate projects flavors attributes through the dependency graph`() {
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib2", ":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug", "release"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib3"),
          defaultVariant = "flav11Release",
          variants = listOf("flav11Debug", "flav11Release"), // lib1 has paidDebug variant
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = listOf(TestProductFlavor("flav11", "dim1")),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "flav11Release",
          variants = listOf("flav11Debug", "flav11Release", "flav12Debug", "flav12Release"), // lib1 has paidDebug variant
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = listOf(TestProductFlavor("flav11", "dim1"), TestProductFlavor("flav12", "dim1")),
        ),
      )

    setSwitchVariantRequest(":app", "release")
    val newException = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(newException).hasMessageThat().contains("Variant Conflict: Could not resolve ProductFlavors ambiguity for project: :lib3.")
  }

  @Test
  fun `verify that we do handle the corner cases correctly`() {
    // Setup:
    // :app -> :lib
    // :lib -> :lib2, :lib3
    // App has productFlavors, but library doesn't. The expected behavior is to resolve correctly and not fail.
    val appFlavors =
      listOf(TestProductFlavor("paid", "pricing", listOf("member")), TestProductFlavor("free", "pricing", listOf("demo", "member")))

    val libBuildTypes =
      listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("blob", listOf("release")))
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "paidDebug",
          variants = listOf("paidDebug", "freeDebug", "paidRelease", "freeRelease", "paidQa", "freeQa"),
          buildTypes =
            listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("blob", "release"))),
          productFlavors = appFlavors,
        ),
        ProjectSetup(
          moduleId = ":lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib2", ":lib3"),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "blob"),
          buildTypes = libBuildTypes,
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "memberDebug",
          variants = listOf("memberDebug", "memberRelease", "memberBlob"),
          buildTypes = libBuildTypes,
          productFlavors = listOf(TestProductFlavor("member", "pricing")),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "demoDebug",
          variants = listOf("demoDebug", "memberDebug", "demoRelease", "memberRelease"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release")),
          productFlavors = listOf(TestProductFlavor("demo", "pricing"), TestProductFlavor("member", "pricing")),
        ),
      )

    verifyThatSwitchingAppVariantMatchesEachProjectAccordingToItsAttributes(projects)
    verifyThatLib2ResolutionFailsWhenSwitchingProjectDoesNotSpecifyStrategiesToUse(projects)
  }

  @Test
  fun `variants resolution uses attributes matching to validate variant matching`() {
    // New case: Name matches but attributes don't match.
    // App: variant 'specialDebug', no flavors.
    // Lib: variant 'specialDebug', flavor 'extra' in dimension 'other'.
    val lib4Flavors = listOf(TestProductFlavor("extra", "other"))
    // Note: lib:specialDebug variant will have NO flavors in our mock because lib only has 'extra' flavor,
    // but its variant setup 'extraDebug' has 'extra'.
    // Wait, let's make it more explicit.
    val projects3 =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "specialDebug",
          variants = listOf("specialDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("specialDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = lib4Flavors,
        ),
      )
    setSwitchVariantRequest(":app", "specialDebug")

    // It should fail in resolveVariantAttributes because name match failed attribute check (Lib has dimension 'other', App doesn't),
    // and resolution failed because no strategy was provided for 'other'.
    val newException = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects3) }
    assertThat(newException).hasMessageThat().contains("Variant Conflict: Unable to find a variant to Sync for project: :lib.")
  }

  @Test
  fun `missingDimensionStrategy is project specific and does not leak`() {
    // Setup:
    // :app1 -> :lib1 (Strategy: color -> red)
    // :app2 -> :lib2 (Strategy: color -> green)
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug", listOf("faq"))),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("color" to listOf("red")),
        ),
        ProjectSetup(
          moduleId = ":app2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib2"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug", listOf("qa"))),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("color" to listOf("green")),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "blueQa",
          variants = listOf("blueFaq", "redFaq", "blueQa", "redQa"),
          buildTypes = listOf(TestAndroidBuildType("faq"), TestAndroidBuildType("qa")),
          productFlavors = listOf(TestProductFlavor("blue", "color"), TestProductFlavor("red", "color")),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "blueFaq",
          variants = listOf("blueQa", "greenQa", "blueFaq", "greenFaq"),
          buildTypes = listOf(TestAndroidBuildType("faq"), TestAndroidBuildType("qa")),
          productFlavors = listOf(TestProductFlavor("blue", "color"), TestProductFlavor("green", "color")),
        ),
      )

    setSwitchVariantRequest(":app1", "debug")

    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedVariants[":lib1"]).isEqualTo("redFaq")
    assertThat(selectedVariants[":lib2"]).isEqualTo("greenQa")
  }

  @Test
  fun `missingDimensionStrategy prioritises fallbacks when multiple consumers exist`() {
    // Setup:
    // :app1 -> :lib (Strategy: color -> red)
    // :app2 -> :lib (Strategy: color -> blue)
    val libFlavors = listOf(TestProductFlavor("blue", "color"), TestProductFlavor("red", "color"), TestProductFlavor("green", "color"))

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("color" to listOf("red")),
        ),
        ProjectSetup(
          moduleId = ":app2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("color" to listOf("blue")),
        ),
        ProjectSetup(
          moduleId = ":lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "greenDebug",
          variants = listOf("blueDebug", "redDebug", "greenDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = libFlavors,
        ),
      )

    setSwitchVariantRequest(":app1", "debug")
    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    // In our implementation, the first consumer defines the variant and the strategies.
    // App1 processed -> :lib gets color -> [red]
    // :lib processed -> picks redDebug because red is App1 fallback.
    assertThat(selectedVariants[":lib"]).isEqualTo("redDebug")

    // Now, switch App2 variant.
    setSwitchVariantRequest(":app2", "debug")
    val selectedApp2Variants = sortProjectsAndGetSelectedVariants(projects)

    // App2 processed -> :lib gets color -> [blue]
    // :lib processed -> picks redDebug because red is App1 fallback.
    assertThat(selectedApp2Variants[":lib"]).isEqualTo("blueDebug")
  }

  @Test
  fun `comprehensive missingDimensionStrategy propagation and switching`() {
    // Setup:
    // :app1 -> :lib -> :lib-internal
    // :app2 -> :lib -> :lib-internal
    // Complex flavor-level strategies

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("api" to listOf("api24")), // Only defaultConfig
        ),
        ProjectSetup(
          moduleId = ":app2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib"),
          defaultVariant = "paidDebug",
          variants = listOf("paidDebug", "freeDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors =
            listOf(
              TestProductFlavor("paid", "tier", missingDimensionStrategy = mapOf("api" to listOf("api28"))),
              TestProductFlavor("free", "tier", missingDimensionStrategy = mapOf("api" to listOf("api21"))),
            ),
          missingDimensionStrategy = mapOf("api" to listOf("api21")), // defaultConfig
        ),
        ProjectSetup(
          moduleId = ":lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib-internal"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = emptyList(),
          missingDimensionStrategy = mapOf("api" to emptyList()),
        ),
        ProjectSetup(
          moduleId = ":lib-internal",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "api21Debug",
          variants = listOf("api21Debug", "api24Debug", "api28Debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = listOf(TestProductFlavor("api21", "api"), TestProductFlavor("api24", "api"), TestProductFlavor("api28", "api")),
        ),
      )

    // Scenario 1: User switches to :app1 debug.
    setSwitchVariantRequest(":app1", "debug")
    val res1 = sortProjectsAndGetSelectedVariants(projects)
    assertThat(res1[":lib-internal"]).isEqualTo("api24Debug")

    // Scenario 2: User switches to :app2 paidDebug.
    setSwitchVariantRequest(":app2", "paidDebug")
    val res2 = sortProjectsAndGetSelectedVariants(projects)
    // :app2 productFlavor strategy (28) should override its defaultConfig strategy (21)
    assertThat(res2[":lib-internal"]).isEqualTo("api28Debug")

    // Scenario 3: User switches to :app2 freeDebug
    setSwitchVariantRequest(":app2", "freeDebug")
    val res3 = sortProjectsAndGetSelectedVariants(projects)
    // :app2 freeDebug strategy (21)
    assertThat(res3[":lib-internal"]).isEqualTo("api21Debug")
  }

  @Test
  fun `priority aware resolution in shared dependency with complex attributes`() {
    // Setup:
    // App1 (Switched) -> Lib1 -> SharedLib (transitive path, priority 0)
    // App2 (Normal) -> SharedLib (direct path, priority 1)

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug", listOf("minified"))),
          missingDimensionStrategy = mapOf("api" to listOf("v28")), // Wants v28
        ),
        ProjectSetup(
          moduleId = ":app2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":shared-lib"),
          defaultVariant = "qa",
          variants = listOf("qa", "faq"),
          buildTypes = listOf(TestAndroidBuildType("qa", listOf("minified")), TestAndroidBuildType("faq")),
          missingDimensionStrategy = mapOf("api" to listOf("v21")), // Wants v21
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":shared-lib"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug", listOf("minified"))),
          missingDimensionStrategy = emptyMap(),
        ),
        ProjectSetup(
          moduleId = ":shared-lib",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "v21Minified",
          variants = listOf("v21Minified", "v28Minified"),
          buildTypes = listOf(TestAndroidBuildType("minified")),
          productFlavors = listOf(TestProductFlavor("v21", "api"), TestProductFlavor("v28", "api")),
        ),
      )

    // Scenario 1: User switches App1 to 'debug'.
    // Result: SharedLib MUST pick v28Minified because App1's strategy (28) and fallback (minified)
    // are higher priority (0) than App2's (1).
    setSwitchVariantRequest(":app1", "debug")

    val res1 = sortProjectsAndGetSelectedVariants(projects)
    assertThat(res1[":shared-lib"]).isEqualTo("v28Minified")

    // Scenario 2: User switches App2 to 'qa'.
    // Result: SharedLib should pick v21Minified because App2 is now the switch source (Priority 0).
    setSwitchVariantRequest(":app2", "qa")

    val res2 = sortProjectsAndGetSelectedVariants(projects)
    assertThat(res2[":shared-lib"]).isEqualTo("v21Minified")

    // Now, switch variant for App2, which will fail to resolve shared-lib because there is no buildType specified for it as a
    // matchingFallbacks.
    setSwitchVariantRequest(":app2", "faq")

    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Variant Conflict: Could not resolve BuildTypes ambiguity for project: :shared-lib.")
  }

  @Test
  fun `resolution engine handles graph cycles gracefully`() {
    // Setup: libA -> libB -> libA (Circular dependency)
    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":libA"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":libA",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":libB"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":libB",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":libA"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
      )

    val nodes =
      projects.map { project ->
        ProjectNode(
          path = project.moduleId,
          moduleId = project.moduleId,
          projectType = project.projectType,
          outgoingDependencies = project.dependencies,
        )
      }

    // This should not throw or loop infinitely
    val sortedBatches = sortProjectsByPriority(nodes, { it }, syncOptions)

    // Both libA and libB should end up in the fallback Batch 1000
    assertThat(sortedBatches[1000]!!.map { it.path }).containsAllOf(":libA", ":libB")
  }

  @Test
  fun `missingDimensionStrategy dominance and cross-propagation`() {
    // app -> lib1 -> lib2
    //        lib1 -> lib3
    // app: D1 -> [red]
    // lib1: D1 -> [invalid], D2 -> [blue]
    // lib2: has D1. Must resolve to red.
    // lib3: has D2. Must resolve to blue.

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":lib1"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          missingDimensionStrategy = mapOf("D1" to listOf("red"), "D2" to listOf("yellow")),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":lib2", ":lib3"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          // lib1 has a local strategy for D1 that is "invalid", and a valid one for D2.
          missingDimensionStrategy = mapOf("D1" to listOf("green"), "D2" to listOf("blue")),
        ),
        ProjectSetup(
          moduleId = ":lib2",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "greenDebug",
          variants = listOf("redDebug", "greenDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = listOf(TestProductFlavor("red", "D1"), TestProductFlavor("green", "D1")),
        ),
        ProjectSetup(
          moduleId = ":lib3",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "yellowDebug",
          variants = listOf("blueDebug", "yellowDebug"),
          buildTypes = listOf(TestAndroidBuildType("debug")),
          productFlavors = listOf(TestProductFlavor("blue", "D2"), TestProductFlavor("yellow", "D2")),
        ),
      )

    setSwitchVariantRequest(":app", "debug")
    val selected = sortProjectsAndGetSelectedVariants(projects)

    // 1. lib2 uses App's strategy for D1 (Dominance)
    assertThat(selected[":lib2"]).isEqualTo("redDebug")

    // 2. lib3 uses lib1's strategy for D2 (Augmentation)
    assertThat(selected[":lib3"]).isEqualTo("yellowDebug")
  }

  @Test
  fun `test project synchronizes with its target app variant`() {
    // Setup:
    // App (:app) -> Root App
    // Library (:lib1)
    // Test Project (:test-project) -> targets :app

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "qa"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("release"))),
        ),
        ProjectSetup(
          moduleId = ":lib1",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "qa"),
          buildTypes = listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("qa", listOf("faq"))),
        ),
        ProjectSetup(
          moduleId = ":test-project",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_TEST,
          dependencies = listOf(":app"),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "faq"),
          buildTypes =
            listOf(TestAndroidBuildType("debug"), TestAndroidBuildType("release"), TestAndroidBuildType("faq", listOf("release"))),
          testedTargetProject = ":app", // Special link
        ),
      )

    // Scenario: User switches App to 'release'.
    // Even though :lib1 (Priority 2) might want :test-project:release,
    // the target App must ensure it mirrors :app.
    setSwitchVariantRequest(":app", "release")

    val selected = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selected[":app"]).isEqualTo("release")
    assertThat(selected[":test-project"]).isEqualTo("release")

    setSwitchVariantRequest(":lib1", "qa")

    val selectedFromLib1 = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedFromLib1[":lib1"]).isEqualTo("qa")
    // The variant resolved here for the test project is debug as it doesn't know anything about the custom APP variants.
    assertThat(selectedFromLib1[":test-project"]).isEqualTo("debug")
    assertThat(selected[":app"]).isEqualTo("release")
  }

  @Test
  fun `dynamic feature synchronizes with its base app variant`() {
    // Setup:
    // Dynamic Feature (:feature) -> :app
    // Logical Flow: :feature must match :app variant selection.

    val projects =
      listOf(
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          // PN: normally APP do not have a dependency on the DF projects explicitly, but these are automatically marked as
          // declared dependencies in the real world (see [VariantSelection.generateDynamicFeatureDependencie]), so the best way to
          // replicate that is through here.
          dependencies = listOf(":feature"),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "prod", "qa"),
          buildTypes =
            listOf(
              TestAndroidBuildType("debug"),
              TestAndroidBuildType("release"),
              TestAndroidBuildType("qa"),
              TestAndroidBuildType("prod", listOf("faq")),
            ),
        ),
        ProjectSetup(
          moduleId = ":feature",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE,
          dependencies = listOf(":app"),
          defaultVariant = "debug",
          variants = listOf("debug", "release", "faq", "qa"),
          buildTypes =
            listOf(
              TestAndroidBuildType("debug"),
              TestAndroidBuildType("release"),
              TestAndroidBuildType("qa"),
              TestAndroidBuildType("faq", listOf("debug")),
            ),
        ),
      )

    // User switches App to 'release'.
    setSwitchVariantRequest(":app", "release")

    val selected = sortProjectsAndGetSelectedVariants(projects)

    // Verify: Does the feature module mirror the app despite the technical dependency?
    assertThat(selected[":app"]).isEqualTo("release")
    assertThat(selected[":feature"]).isEqualTo("release")

    // Do another change for APP that will not have a direct variant match for the dynamic feature project.
    setSwitchVariantRequest(":app", "prod")

    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Variant conflict: Unable to find variant \"prod\" to Sync for project: :feature.")

    // Now change variant for Dynamic feature project.
    setSwitchVariantRequest(":feature", "qa")

    val selectedFromFeature1 = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedFromFeature1[":app"]).isEqualTo("qa")
    assertThat(selectedFromFeature1[":feature"]).isEqualTo("qa")

    // Now change the dynamic feature variant to something that does not directly match in App.
    // The expectation here is that the matchingFallback specified by the Dynamic Feature project will not be used to resolve APP.
    setSwitchVariantRequest(":feature", "faq")

    val newException = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(newException).hasMessageThat().contains("Variant conflict: Unable to find variant \"faq\" to Sync for project: :app.")
  }

  @Test
  fun testTopologicalOrderWithMixedPriorityEntryPoints() {
    // This test enforces the sorting logic (found when a a bug was introduced) where a P2 project (LibA) gets processed before a P1 project
    // (App) could cause a shared dep (LibB) to be incorrectly assigned to a lower batch.
    // Dependencies tree:
    // App (P1) -> LibB
    // LibA (P2) -> LibB
    // LibB should be in Batch 3 (App[1] -> LibA[2] -> LibB[3]).

    val setup =
      listOf(
        // LibA with no incoming deps but is P2.
        ProjectSetup(
          moduleId = ":libA",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = listOf(":libB"),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
        // App project is P1.
        ProjectSetup(
          moduleId = ":app",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_APP,
          dependencies = listOf(":libB"), // APP -> libB.
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
        ProjectSetup(
          moduleId = ":libB",
          projectType = IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          dependencies = emptyList(),
          defaultVariant = "debug",
          variants = listOf("debug"),
          buildTypes = emptyList(),
        ),
      )

    val projects = setup.map { createMocksForProject(it) }
    val projectsWithNodes =
      projects.map { params ->
        params.basicGradleProject to
          ProjectNode(
            path = params.basicGradleProject.path,
            moduleId = params.basicGradleProject.path,
            projectType =
              if (params.basicGradleProject.path == ":app") IdeAndroidProjectType.PROJECT_TYPE_APP
              else IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
            outgoingDependencies = setup.first { it.moduleId == params.basicGradleProject.path }.dependencies,
          )
      }

    val batches = sortProjectsByPriority(projectsWithNodes, { it.second }, syncOptions)

    // Find the batch number for each project.
    val batchByProject = mutableMapOf<String, Int>()
    batches.forEach { (batch, list) -> list.forEach { batchByProject[it.first.path] = batch } }

    val libABatch = batchByProject[":libA"]!!
    val libBBatch = batchByProject[":libB"]!!

    // Since LibB depends on LibA, it MUST be in a strictly higher batch.
    assertThat(libBBatch).isGreaterThan(libABatch)
  }

  private fun setSwitchVariantRequest(moduleId: String, variantName: String) {
    whenever(switchVariantRequest.moduleId).thenReturn(":" + moduleId)
    whenever(switchVariantRequest.variantName).thenReturn(variantName)
  }

  private fun verifyThatSwitchingAppVariantMatchesEachProjectAccordingToItsAttributes(projects: List<ProjectSetup>) {
    setSwitchVariantRequest(":app", "freeRelease") // Switch App variant.
    // Request free productFlavor for app, which should propagate to lib that doesn't have any productFlavors, and lib2 by using the correct
    // matchingFallbacks.

    val selectedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(selectedVariants[":app"]).isEqualTo("freeRelease")
    assertThat(selectedVariants[":lib"]).isEqualTo("release")
    assertThat(selectedVariants[":lib2"]).isEqualTo("demoRelease") // Check transitive propagation to lib2
    assertThat(selectedVariants[":lib3"]).isEqualTo("memberRelease") // Check transitive propagation to lib3

    setSwitchVariantRequest(":app", "paidQa") // Requesting 'qa' for app
    val changedVariants = sortProjectsAndGetSelectedVariants(projects)

    assertThat(changedVariants[":app"]).isEqualTo("paidQa")
    // we propagate to blob for lib, which is the first in the list of matchingFallback
    assertThat(changedVariants[":lib"]).isEqualTo("blob")
    // We propagate to release buildType, which is the second in the list, but the only match for lib2.
    assertThat(changedVariants[":lib2"]).isEqualTo("memberRelease")
    assertThat(changedVariants[":lib3"]).isEqualTo("memberBlob")
  }

  private fun verifyThatLib2ResolutionFailsWhenSwitchingProjectDoesNotSpecifyStrategiesToUse(projects: List<ProjectSetup>) {
    // Check that propagation from lib to lib2 also works by failing to resolve lib2 due to ambiguity.
    setSwitchVariantRequest(":lib", "release")

    val exception = assertFailsWith(Exception::class) { sortProjectsAndGetSelectedVariants(projects) }
    assertThat(exception).hasMessageThat().contains("Variant Conflict: Could not resolve ProductFlavors ambiguity for project: :lib2.")
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
            androidProject = projectParamsMock.androidProject,
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

    val variantsResolutionIssues = mutableMapOf<BasicGradleProject, Throwable>()
    setupProjectsVariantsAndConsume(projectDataList, syncOptions, modelConsumer, cachedModels, variantsResolutionIssues)

    // Re-throw any captured issues so that assertFailsWith works correctly.
    variantsResolutionIssues.values.firstOrNull()?.let { throw it }

    return projectDataList.associate { it.first.path to it.second.selectedVariantName }
  }

  private fun createMocksForProject(project: ProjectSetup): Parameters {
    val gradleProject = Mockito.mock(BasicGradleProject::class.java)
    val projectIdentifier = Mockito.mock(ProjectIdentifier::class.java)
    val buildIdentifier = Mockito.mock(BuildIdentifier::class.java)
    val basicAndroidProject = Mockito.mock(BasicAndroidProject::class.java)
    val androidProject = Mockito.mock(AndroidProject::class.java)
    val androidDsl = Mockito.mock(AndroidDsl::class.java)
    val declaredDependencies = Mockito.mock(DeclaredDependencies::class.java)
    val modelVersions = ModelVersions(agp = AgpVersion.parse("9.0.0"), modelVersion = ModelVersion(21, 0), minimumModelConsumer = null)
    val legacyAndroidGradlePluginPropertiesImpl =
      LegacyAndroidGradlePluginPropertiesImpl(emptyMap(), null, null, false, emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap())

    val defaultConfig = Mockito.mock(ProductFlavor::class.java)
    whenever(androidDsl.defaultConfig).thenReturn(defaultConfig)
    whenever(defaultConfig.missingDimensionStrategy).thenReturn(project.missingDimensionStrategy)

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

    val androidProjectVariants =
      project.variants.map { variantName ->
        val variant = Mockito.mock(Variant::class.java)
        if (project.testedTargetProject != null) {
          val ttv = Mockito.mock(TestedTargetVariant::class.java)
          whenever(ttv.targetProjectPath).thenReturn(project.testedTargetProject)
          whenever(variant.testedTargetVariant).thenReturn(ttv)
        }
        variant
      }
    whenever(androidProject.variants).thenReturn(androidProjectVariants)

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
        whenever(flavor.missingDimensionStrategy).thenReturn(testProductFlavor.missingDimensionStrategy)
        flavor
      }

    whenever(gradleProject.path).thenReturn(project.moduleId)
    whenever(gradleProject.projectIdentifier).thenReturn(projectIdentifier)
    whenever(projectIdentifier.buildIdentifier).thenReturn(buildIdentifier)
    whenever(buildIdentifier.rootDir).thenReturn(File(""))
    whenever(basicAndroidProject.variants).thenReturn(variants)
    whenever(basicAndroidProject.projectType)
      .thenReturn(
        when (project.projectType) {
          IdeAndroidProjectType.PROJECT_TYPE_TEST -> ProjectType.TEST
          IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE -> ProjectType.DYNAMIC_FEATURE
          IdeAndroidProjectType.PROJECT_TYPE_APP -> ProjectType.APPLICATION
          else -> ProjectType.LIBRARY
        }
      )
    whenever(androidDsl.buildTypes).thenReturn(buildTypes)
    whenever(androidDsl.productFlavors).thenReturn(productFlavors)
    whenever(androidDsl.flavorDimensions).thenReturn(dimensions)
    whenever(declaredDependencies.allOutgoingProjectDependencies).thenReturn(project.dependencies)

    return Parameters(
      gradleProject,
      basicAndroidProject,
      androidProject,
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
    val missingDimensionStrategy: Map<String, List<String>> = emptyMap(),
    val testedTargetProject: String? = null,
  )

  private data class TestAndroidBuildType(val name: String, val matchingFallbacks: List<String> = emptyList())

  private data class TestProductFlavor(
    val name: String,
    val dimension: String,
    val matchingFallbacks: List<String> = emptyList(),
    val missingDimensionStrategy: Map<String, List<String>> = emptyMap(),
  )

  private data class Parameters(
    val basicGradleProject: BasicGradleProject,
    val basicAndroidProject: BasicAndroidProject,
    val androidProject: AndroidProject,
    val androidDsl: AndroidDsl,
    val declaredDependencies: DeclaredDependencies,
    val legacyAndroidGradlePluginPropertiesImpl: LegacyAndroidGradlePluginPropertiesImpl,
    val modelVersions: ModelVersions,
  )
}
