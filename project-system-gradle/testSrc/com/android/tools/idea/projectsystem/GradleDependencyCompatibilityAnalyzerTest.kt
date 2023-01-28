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

import com.android.SdkConstants
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.tools.idea.projectsystem.gradle.CHECK_DIRECT_GRADLE_DEPENDENCIES
import com.android.tools.idea.projectsystem.gradle.GradleDependencyCompatibilityAnalyzer
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.ProjectBuildModelHandler
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
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

// b/222127690 : TimeoutException in GradleDependencyCompatibilityAnalyzerTest
// This test has been timing out multiple times on a specific set of test machines with TIMEOUT = 1 second.
// It is unknown if this is a timing issue or there is a underlying cause of the timeout. For now attempt with TIMEOUT = 10 seconds.
private const val TIMEOUT = 10L // seconds

class GradleDependencyCompatibilityAnalyzerTest : AndroidTestCase() {

  /**
   * This test is using a fake Maven Repository where we control the available artifacts and versions.
   */
  private val mavenRepository = object : GoogleMavenRepository(
    cacheDir = Paths.get(AndroidTestBase.getTestDataPath()).resolve("../../project-system-gradle/testData/repoIndex").normalize(),
    cacheExpiryHours = Int.MAX_VALUE,
    useNetwork = false
  ) {
    override fun readUrlData(url: String, timeout: Int): ByteArray = throw AssertionFailedError("shouldn't try to read!")
    override fun error(throwable: Throwable, message: String?) {}
  }

  private val repoUrlManager = RepositoryUrlManager(
    googleMavenRepository = mavenRepository,
    cachedGoogleMavenRepository = mavenRepository,
    forceRepositoryChecksInTests = false,
    useEmbeddedStudioRepo = false
  )

  private lateinit var analyzer: GradleDependencyCompatibilityAnalyzer

  fun testGetAvailableDependency_fallbackToPreview() {
    setupProject()
    // In the test repo NAVIGATION only has a preview version 0.0.1-alpha1
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.NAVIGATION.getCoordinate("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.NAVIGATION.getCoordinate("0.0.1-alpha1"))
  }

  fun testGetAvailableDependency_returnsLatestStable() {
    setupProject()
    // In the test repo CONSTRAINT_LAYOUT has a stable version of 1.0.2 and a beta version of 1.1.0-beta3
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("1.0.2"))
  }

  fun testGetAvailableDependency_returnsNullWhenNoneMatches() {
    setupProject()
    // The test repo does not have any version of PLAY_SERVICES_ADS.
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getCoordinate("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.google.android.gms:play-services-ads:+")
    assertThat(missing).containsExactly(GoogleMavenArtifactId.PLAY_SERVICES_ADS.getCoordinate("+"))
    assertThat(found).isEmpty()
  }

  fun testAddSupportDependencyWithMatchInSubModule() {
    setupProject(
      appDependOnLibrary = true,
      additionalLibrary1DeclaredDependencies = "implementation 'com.android.support:appcompat-v7:+'",
      additionalLibrary1ResolvedDependencies = listOf(ideAndroidLibrary("com.android.support:appcompat-v7:23.1.1"))
    )
    // Check that the version is picked up from one of the sub modules
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("23.1.1"))
  }

  fun testAddSupportDependencyWithMatchInAppModule() {
    setupProject(
      appDependOnLibrary = true,
      additionalAppResolvedDependencies = listOf(ideAndroidLibrary("com.android.support:recyclerview-v7:22.2.1")),
      additionalLibrary1DeclaredDependencies = "implementation 'com.android.support:appcompat-v7:+'"
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("22.2.1"))
  }

  fun testProjectWithIncompatibleDependencies() {
    // NOTE: This test sets up an impossible environment. Two different versions of the same library cannot be present.
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:2.0.0"),
        ideAndroidLibrary("androidx.appcompat:appcompat:1.2.0")
      ),
      additionalLibrary1DeclaredDependencies = """
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.appcompat:appcompat:1.2.0'
        """
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("""
      Inconsistencies in the existing project dependencies found.
      Version incompatibility between:
      -   androidx.appcompat:appcompat:1.2.0
      and:
      -   androidx.appcompat:appcompat:2.0.0
    """.trimIndent())
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("androidx.fragment", "fragment", "2.0.0"))
  }

  fun testProjectWithIncompatibleIndirectDependencies() {
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:2.0.0"),
        ideAndroidLibrary("androidx.core:core:1.0.0")
      ),
      additionalLibrary1DeclaredDependencies = """
          implementation 'androidx.appcompat:appcompat:2.0.0'
          implementation 'androidx.core:core:1.0.0'
        """
    )
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

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
    assertThat(found).containsExactly(GradleCoordinate("androidx.fragment", "fragment", "2.0.0"))
  }

  fun testTwoArtifactsWithConflictingDependencies() {
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("com.google.android.material:material:1.3.0")
      ),
      additionalLibrary1DeclaredDependencies = """
          implementation 'com.google.android.material:material:1.3.0'
        """
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

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
    assertThat(found).containsExactly(GradleCoordinate("com.acme.pie", "pie", "1.0.0-alpha1"))
  }

  fun testTwoArtifactsWithConflictingDependenciesInDifferentModules() {
    setupProject(
      appDependOnLibrary = true,
      additionalLibrary1ResolvedDependencies = listOf(ideAndroidLibrary("com.google.android.material:material:1.3.0")),
      additionalLibrary1DeclaredDependencies = """
          implementation 'com.google.android.material:material:1.3.0'
        """,
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

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
    assertThat(found).containsExactly(GradleCoordinate("com.acme.pie", "pie", "1.0.0-alpha1"))
  }

  fun testPreviewsAreAcceptedIfNoStableExists() {
    setupProject(
      additionalLibrary1DeclaredDependencies = """
          implementation 'androidx.appcompat:appcompat:2.0.0'
        """
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("com.acme.pie", "pie", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("com.acme.pie:pie:1.0.0-alpha1"))
    assertThat(missing).isEmpty()
  }

  fun testNewestSameMajorIsChosenFromExistingIndirectDependency() {
    setupProject(
      additionalAppResolvedDependencies = listOf(
        ideAndroidLibrary("androidx.appcompat:appcompat:1.0.0")
      ),
      additionalLibrary1DeclaredDependencies = """
          implementation 'androidx.appcompat:appcompat:1.0.0'
        """
    )

    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.fragment", "fragment", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate.parseCoordinateString("androidx.fragment:fragment:1.2.0"))
    assertThat(missing).isEmpty()
  }

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
      listOf(GradleCoordinate("com.google.android.material", "material", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("com.google.android.material:material:1.3.0"))
    assertThat(missing).isEmpty()
  }

  fun testAddingKotlinStdlibDependenciesFromMultipleSources() {
    // We are adding 2 kotlin dependencies which depend on different kotlin stdlib
    // versions: 1.2.50 and 1.3.0. Make sure the dependencies can be added without errors.
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("androidx.core", "core-ktx", "1.0.0"),
             GradleCoordinate("androidx.navigation", "navigation-runtime-ktx", "2.0.0"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(found).containsExactly(
      GradleCoordinate.parseCoordinateString("androidx.core:core-ktx:1.0.0"),
      GradleCoordinate.parseCoordinateString("androidx.navigation:navigation-runtime-ktx:2.0.0"))
    assertThat(missing).isEmpty()
  }

  fun testGetAvailableDependencyWithRequiredVersionMatching() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "+")))
      .get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).hasSize(1)

    val foundDependency = found.first()
    assertThat(foundDependency.artifactId).isEqualTo(SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID)
    assertThat(foundDependency.groupId).isEqualTo(SdkConstants.SUPPORT_LIB_GROUP_ID)
    assertThat(foundDependency.lowerBoundVersion!!.major).isEqualTo(23)

    // TODO: b/129297171
    @Suppress("ConstantConditionIf")
    if (CHECK_DIRECT_GRADLE_DEPENDENCIES) {
      // When we were checking the parsed gradle file we were able to detect a specified "+" in the version.
      assertThat(foundDependency.acceptsGreaterRevisions()).isTrue()
    }
    else {
      // Now that we are using the resolved gradle version we are no longer able to detect a "+" in the version.
      assertThat(foundDependency.lowerBoundVersion!!.minor).isEqualTo(1)
      assertThat(foundDependency.lowerBoundVersion!!.micro).isEqualTo(1)
    }
  }

  fun testGetAvailableDependencyWhenUnavailable() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate("nonexistent", "dependency123", "+"))).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: nonexistent:dependency123:+")
    assertThat(missing).containsExactly(GradleCoordinate("nonexistent", "dependency123", "+"))
    assertThat(found).isEmpty()
  }

  fun testWithExplicitVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "23.1.0"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "23.1.0"))
  }

  fun testWithExplicitPreviewVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.CONSTRAINT_LAYOUT_LIB_GROUP_ID, SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID, "1.1.0-beta3"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support.constraint", "constraint-layout", "1.1.0-beta3"))
  }

  fun testWithExplicitNonExistingVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "22.17.3"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEqualTo("The dependency was not found: com.android.support:appcompat-v7:22.17.3")
    assertThat(missing).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "22.17.3"))
    assertThat(found).isEmpty()
  }

  fun testWithMajorVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "23.+"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "23.1.1"))
  }

  fun testWithMajorMinorVersion() {
    setupProject()
    val (found, missing, warning) = analyzer.analyzeDependencyCompatibility(
      listOf(GradleCoordinate(SdkConstants.SUPPORT_LIB_GROUP_ID, SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID, "22.2.+"))
    ).get(TIMEOUT, TimeUnit.SECONDS)

    assertThat(warning).isEmpty()
    assertThat(missing).isEmpty()
    assertThat(found).containsExactly(GradleCoordinate("com.android.support", "appcompat-v7", "22.2.1"))
  }

  private fun setupProject(
    appDependOnLibrary: Boolean = false,
    additionalAppDeclaredDependencies: String? = null,
    additionalAppResolvedDependencies: List<AndroidLibraryDependency> = emptyList(),
    additionalLibrary1DeclaredDependencies: String? = null,
    additionalLibrary1ResolvedDependencies: List<AndroidLibraryDependency> = emptyList(),
  ) {

    fun config(declaredDependencies: String?): String =
      if (declaredDependencies != null)
        """
        dependencies {
          $declaredDependencies
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
                  url 'file://${mavenRepository.cacheDir}'
              }
          }
      }
      """
    )

    myFixture.addFileToProject(
      "app/build.gradle",
      config(
        listOfNotNull(
          if (appDependOnLibrary) "\napi project(':library1')\n" else null,
          additionalAppDeclaredDependencies
        )
          .joinToString(separator = "\n")
          .takeUnless { it.isEmpty() }
      )
    )

    myFixture.addFileToProject(
      "library1/build.gradle",
      config(additionalLibrary1DeclaredDependencies)
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
          androidLibraryDependencyList = { additionalAppResolvedDependencies }
        )
      ),
      AndroidModuleModelBuilder(
        ":library1",
        "debug",
        AndroidProjectBuilder(
          projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
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

private fun ideAndroidLibrary(artifactAddress: String) =
  AndroidLibraryDependency(
    IdeAndroidLibraryImpl(
      artifactAddress = artifactAddress,
      name = "",
      folder = File("libraryFolder").resolve(artifactAddress.replace(':', '-')),
      _manifest = "manifest.xml",
      _compileJarFiles = listOf("file.jar"),
      _runtimeJarFiles = listOf("api.jar"),
      _resFolder = "res",
      _resStaticLibrary = "libraryFolder/res.apk",
      _assetsFolder = "assets",
      _jniFolder = "jni",
      _aidlFolder = "aidl",
      _renderscriptFolder = "renderscriptFolder",
      _proguardRules = "proguardRules",
      _lintJar = "lint.jar",
      _srcJar = "src.jar",
      _docJar = "doc.jar",
      _samplesJar = "sample.jar",
      _externalAnnotations = "externalAnnotations",
      _publicResources = "publicResources",
      _artifact = "artifactFile",
      _symbolFile = "symbolFile"
    )
  )
