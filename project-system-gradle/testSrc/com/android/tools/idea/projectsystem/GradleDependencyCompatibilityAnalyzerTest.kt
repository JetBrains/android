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
package com.android.tools.idea.projectsystem

import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.repository.FakeGoogleMavenRepositoryV2Host
import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepositoryV2
import com.android.testutils.AssumeUtil
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.impl.IdeDeclaredDependenciesImpl
import com.android.tools.idea.gradle.model.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.projectsystem.gradle.GradleDependencyCompatibilityAnalyzer
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.testing.AndroidLibraryDependency
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import junit.framework.AssertionFailedError
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

// b/222127690 : TimeoutException in GradleDependencyCompatibilityAnalyzerTest
// This test has been timing out multiple times on a specific set of test machines with TIMEOUT = 1 second.
// It is unknown if this is a timing issue or there is a underlying cause of the timeout. For now attempt with TIMEOUT = 10 seconds.
private const val TIMEOUT = 10L // seconds

@RunWith(JUnit4::class)
class GradleDependencyCompatibilityAnalyzerTest : AndroidTestCase() {

  private val testDataDir: Path =
    Paths.get(
      AndroidTestBase.getTestDataPath()).resolve("../../project-system-gradle/testData/repoIndex").normalize()

  /**
   * This test is using a fake Maven Repository where we control the available artifacts and versions.
   */
  private val mavenRepository = object : GoogleMavenRepository(
    cacheDir = testDataDir,
    cacheExpiryHours = Int.MAX_VALUE,
    useNetwork = false
  ) {
    override fun readUrlData(url: String, timeout: Int, lastModified: Long) = throw AssertionFailedError("shouldn't try to read!")
    override fun error(throwable: Throwable, message: String?) {}
  }
  private val googleMavenRepositoryV2 = GoogleMavenRepositoryV2.create(FakeGoogleMavenRepositoryV2Host())

  private val repoUrlManager = RepositoryUrlManager(
    googleMavenRepository = mavenRepository,
    cachedGoogleMavenRepository = mavenRepository,
    googleMavenRepositoryV2 = googleMavenRepositoryV2,
    cachedGoogleMavenRepositoryV2 = googleMavenRepositoryV2,
    useEmbeddedStudioRepo = false
  )

  private lateinit var analyzer: GradleDependencyCompatibilityAnalyzer

  override fun setUp() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    super.setUp()
  }

  @Test
  fun testGetAvailableDependency_fallbackToPreview() {
    setupProject()
    // In the test repo ANDROIDX_NAVIGATION_RUNTIME only has a preview version 0.0.1-alpha1
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME.getDependency("+"),
      GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME.getComponent("0.0.1-alpha1")
    )
  }

  @Test
  fun testGetAvailableDependency_returnsLatestStable() {
    setupProject()
    // In the test repo CONSTRAINT_LAYOUT has a stable version of 1.0.2 and a beta version of 1.1.0-beta3
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("+"),
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getComponent("1.0.2")
    )
  }

  @Test
  fun testGetAvailableDependency_returnsNullWhenNoneMatches() {
    setupProject()
    // The test repo does not have any version of PLAY_SERVICES_ADS.
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.google.android.gms:play-services-ads:+")
    assertThat(missing).containsExactly(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getDependency("+"))
    assertThat(found).isEmpty()
  }

  @Test
  fun testAddSupportDependencyWithMatchInSubModule() {
    setupProject(
      appDependOnLibrary = true,
      additionalLibrary1DeclaredDependencies = listOf("com.android.support:appcompat-v7:23.1.1"),
      additionalLibrary1ResolvedDependencies = listOf(ideAndroidLibrary("com.android.support:appcompat-v7:23.1.1"))
    )
    // Check that the version is picked up from one of the sub modules
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getDependency("+"),
      GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getComponent("23.1.1")
    )
  }

  @Test
  fun testAddSupportDependencyWithMatchInAppModule() {
    setupProject(
      appDependOnLibrary = true,
      additionalAppResolvedDependencies = listOf(ideAndroidLibrary("com.android.support:recyclerview-v7:22.2.1")),
      additionalLibrary1DeclaredDependencies = listOf("com.android.support:appcompat-v7:+")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getDependency("+"),
      GoogleMavenArtifactId.SUPPORT_RECYCLERVIEW_V7.getComponent("22.2.1")
    )
  }

  @Test
  fun testProjectWithIncompatibleDependencies() {
    // NOTE: This test sets up an impossible environment. Two different versions of the same library cannot be present.
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:2.0.0"),
        ideAndroidLibrary("androidx.appcompat:appcompat:1.2.0")
      ),
      additionalAppDeclaredDependencies = listOf("androidx.appcompat:appcompat:2.0.0", "androidx.appcompat:appcompat:1.2.0")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("""
      Inconsistencies in the existing project dependencies found.
      Version incompatibility between:
      -   androidx.appcompat:appcompat:1.2.0
      and:
      -   androidx.appcompat:appcompat:2.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"),
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getComponent("2.0.0")
    )
  }

  @Test
  fun testProjectWithIncompatibleIndirectDependencies() {
    setupProject(
      appDependOnLibrary = true,
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:2.0.0"),
        ideAndroidLibrary("androidx.core:core:1.0.0")
      ),
      additionalLibrary1DeclaredDependencies = listOf("androidx.appcompat:appcompat:2.0.0", "androidx.core:core:1.0.0")
    )
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("""
      Inconsistencies in the existing project dependencies found.
      Version incompatibility between:
      -   androidx.core:core:1.0.0
      and:
      -   androidx.appcompat:appcompat:2.0.0

      With the dependency:
      -   androidx.core:core:1.0.0
      versus:
      -   androidx.core:core:2.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"),
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getComponent("2.0.0")
    )
  }

  @Test
  fun testTwoArtifactsWithConflictingDependencies() {
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("com.google.android.material:material:1.3.0")
      ),
      additionalAppDeclaredDependencies = listOf("com.google.android.material:material:1.3.0")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(Dependency.parse("com.acme.pie:pie:+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("""
      Version incompatibility between:
      -   com.acme.pie:pie:1.0.0-alpha1
      and:
      -   com.google.android.material:material:1.3.0

      With the dependency:
      -   androidx.core:core:2.0.0
      versus:
      -   androidx.core:core:1.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      Dependency.parse("com.acme.pie:pie:+"),
      Component.parse("com.acme.pie:pie:1.0.0-alpha1")
    )
  }

  @Test
  fun testTwoArtifactsWithConflictingDependenciesInDifferentModules() {
    setupProject(
      appDependOnLibrary = true,
      additionalLibrary1ResolvedDependencies = listOf(ideAndroidLibrary("com.google.android.material:material:1.3.0")),
      additionalLibrary1DeclaredDependencies = listOf("com.google.android.material:material:1.3.0")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(Dependency.parse("com.acme.pie:pie:+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("""
      Version incompatibility between:
      -   com.acme.pie:pie:1.0.0-alpha1 in module testTwoArtifactsWithConflictingDependenciesInDifferentModules.app
      and:
      -   com.google.android.material:material:1.3.0 in module testTwoArtifactsWithConflictingDependenciesInDifferentModules.library1

      With the dependency:
      -   androidx.core:core:2.0.0
      versus:
      -   androidx.core:core:1.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      Dependency.parse("com.acme.pie:pie:+"),
      Component.parse("com.acme.pie:pie:1.0.0-alpha1")
    )
  }

  @Test
  fun testPreviewsAreAcceptedIfNoStableExists() {
    setupProject(
      additionalLibrary1DeclaredDependencies = listOf("androidx.appcompat:appcompat:2.0.0")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(Dependency.parse("com.acme.pie:pie:+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      Dependency.parse("com.acme.pie:pie:+"),
      Component.parse("com.acme.pie:pie:1.0.0-alpha1")
    )
  }

  @Test
  fun testNewestSameMajorIsChosenFromExistingIndirectDependency() {
    setupProject(
      appDependOnLibrary = true,
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:1.0.0")
      ),
      additionalLibrary1DeclaredDependencies = listOf("androidx.appcompat:appcompat:1.0.0")
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getDependency("+"),
      GoogleMavenArtifactId.ANDROIDX_FRAGMENT.getComponent("1.2.0")
    )
  }

  @Test
  fun testAddingImcompatibleTestDependenciesFromMultipleSources() {
    // We are adding 2 dependencies that have conflicting test dependencies.
    // We should ignore test dependencies with analyzing compatibility issues.
    // In this case recyclerview and material have conflicting dependencies on mockito,
    // recyclerview on mockito-core:2.19.0 and material on mockito-core:1.9.5
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.recyclerview:recyclerview:1.2.0")
      )
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.MATERIAL.getDependency("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.MATERIAL.getDependency("+"),
      GoogleMavenArtifactId.MATERIAL.getComponent("1.3.0")
    )
  }

  @Test
  fun testAddingKotlinStdlibDependenciesFromMultipleSources() {
    // We are adding 2 kotlin dependencies which depend on different kotlin stdlib
    // versions: 1.2.50 and 1.3.0. Make sure the dependencies can be added without errors.
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.ANDROIDX_CORE_KTX.getDependency("1.0.0"),
             GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME_KTX.getDependency("2.0.0"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.ANDROIDX_CORE_KTX.getDependency("1.0.0"),
      GoogleMavenArtifactId.ANDROIDX_CORE_KTX.getComponent("1.0.0"),
      GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME_KTX.getDependency("2.0.0"),
      GoogleMavenArtifactId.ANDROIDX_NAVIGATION_RUNTIME_KTX.getComponent("2.0.0"))
    assertThat(missing).isEmpty()
  }

  @Test
  fun testGetAvailableDependencyWithRequiredVersionMatching() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("+")))
      .get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("+"),
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getComponent("23.1.1")
    )
  }

  @Test
  fun testGetAvailableDependencyWhenUnavailable() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(Dependency.parse("nonexistent:dependency123:+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: nonexistent:dependency123:+")
    assertThat(missing).containsExactly(Dependency.parse("nonexistent:dependency123:+"))
    assertThat(found).isEmpty()
  }

  @Test
  fun testGetAvailableDependenciesWhenUnavailable() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(Dependency.parse("nonexistent:dependency123:+"),
             Dependency.parse("nonexistent:dependency456:+"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo(
      """
       The dependencies were not found:
          nonexistent:dependency123:+
          nonexistent:dependency456:+
      """.trimIndent()
    )
    assertThat(missing).containsExactly(
      Dependency.parse("nonexistent:dependency123:+"),
      Dependency.parse("nonexistent:dependency456:+")
    )
    assertThat(found).isEmpty()
  }

  @Test
  fun testWithExplicitVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("23.1.0"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("23.1.0"),
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getComponent("23.1.0")
    )
  }

  @Test
  fun testWithExplicitPreviewVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("1.1.0-beta3"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getDependency("1.1.0-beta3"),
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getComponent("1.1.0-beta3")
    )
  }

  @Test
  fun testWithExplicitNonExistingVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("[22.17.3,22.17.4)!!"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.android.support:appcompat-v7:[22.17.3,22.17.4)!!")
    assertThat(missing).containsExactly(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("[22.17.3,22.17.4)!!"))
    assertThat(found).isEmpty()
  }

  @Test
  fun testComponentWithExplicitNonExistingVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeComponentCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getComponent("22.17.3"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.android.support:appcompat-v7:22.17.3")
    assertThat(missing).containsExactly(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("[22.17.3,22.17.4)!!"))
    assertThat(found).isEmpty()
  }

  @Test
  fun testWithMajorVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("23.+"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("23.+"),
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getComponent("23.1.1")
    )
  }

  @Test
  fun testWithMajorMinorVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("22.2.+"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getDependency("22.2.+"),
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getComponent("22.2.1")
    )
  }

  private fun setupProject(
    appDependOnLibrary: Boolean = false,
    additionalAppDeclaredDependencies: List<String> = emptyList(),
    additionalAppResolvedDependencies: List<AndroidLibraryDependency> = emptyList(),
    additionalLibrary1DeclaredDependencies: List<String> = emptyList(),
    additionalLibrary1ResolvedDependencies: List<AndroidLibraryDependency> = emptyList(),
  ) {

    fun config(declaredDependencies: List<Pair<String,String>>): String =
      if (declaredDependencies.isNotEmpty())
        """
        dependencies {
          ${declaredDependencies.joinToString("\n") { "  ${it.first} ${it.second}" }}
        }
        """
      else ""

    myFixture.addFileToProject(
      "settings.gradle",
      """
        include (":app")
        include (":library1")
      """
    )

    myFixture.addFileToProject(
      "build.gradle",
      """
      allprojects {
          repositories {
              maven {
                  url 'file://${testDataDir}'
              }
          }
      }
      """
    )

    myFixture.addFileToProject(
      "app/build.gradle",
      config(
        (if (appDependOnLibrary) listOf("api" to "project(':library1')") else listOf()) +
          additionalAppDeclaredDependencies.map { "implementation" to "'$it'" }
      )
    )

    myFixture.addFileToProject(
      "library1/build.gradle",
      config(additionalLibrary1DeclaredDependencies.map { "implementation" to "'$it'" })
    )

    setupTestProjectFromAndroidModel(
      project,
      @Suppress("DEPRECATION")
      project.baseDir!!.toIoFile(),
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(
        ":app",
        "debug",
        AndroidProjectBuilder(
          androidModuleDependencyList = {
            if (appDependOnLibrary) listOf(AndroidModuleDependency(":library1", "debug"))
            else emptyList()
          },
          declaredDependencies = { IdeDeclaredDependenciesImpl(additionalAppDeclaredDependencies) },
          androidLibraryDependencyList = { additionalAppResolvedDependencies }
        )
      ),
      AndroidModuleModelBuilder(
        ":library1",
        "debug",
        AndroidProjectBuilder(
          projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
          declaredDependencies = { IdeDeclaredDependenciesImpl(additionalLibrary1DeclaredDependencies) },
          androidLibraryDependencyList = { additionalLibrary1ResolvedDependencies }
        )
      )
    )

    myModule = project.gradleModule(":app")?.getMainModule()

    analyzer = GradleDependencyCompatibilityAnalyzer(
      moduleSystem = myModule.getModuleSystem() as GradleModuleSystem,
      projectBuildModelHandler = ProjectBuildModelHandler(project),
      repoUrlManager = repoUrlManager
    )
  }
}

private fun ideAndroidLibrary(artifactAddress: String, folder: File = File("libraryFolder").resolve(artifactAddress.replace(':', '-'))) =
  AndroidLibraryDependency(
    IdeAndroidLibraryImpl.create(
      artifactAddress = artifactAddress,
      component = Component.parse(artifactAddress),
      name = "",
      folder = folder,
      manifest = folder.resolve("manifest.xml").path,
      compileJarFiles = listOf(folder.resolve("file.jar").path),
      runtimeJarFiles = listOf(folder.resolve("api.jar").path),
      resFolder = folder.resolve("res").path,
      resStaticLibrary = folder.resolve("res.apk"),
      assetsFolder = folder.resolve("assets").path,
      jniFolder = folder.resolve("jni").path,
      aidlFolder = folder.resolve("aidl").path,
      renderscriptFolder = folder.resolve("renderscriptFolder").path,
      proguardRules = folder.resolve("proguardRules").path,
      lintJar = folder.resolve("lint.jar").path,
      srcJars = listOf(folder.resolve("src.jar").path, folder.resolve("sample.jar").path),
      docJar = folder.resolve("doc.jar").path,
      externalAnnotations = folder.resolve("externalAnnotations").path,
      publicResources = folder.resolve("publicResources").path,
      artifact = folder.resolve("artifactFile"),
      symbolFile = folder.resolve("symbolFile").path
    ) { this }
  )
