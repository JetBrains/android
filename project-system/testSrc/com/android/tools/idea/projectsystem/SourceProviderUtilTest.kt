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
package com.android.tools.idea.projectsystem

import com.android.tools.idea.io.FilePaths
import com.google.common.truth.Truth.assertThat
import org.jetbrains.jps.util.JpsPathUtil.urlToFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceProviderUtilTest {

  @get:Rule
  val tempDirectory: TemporaryFolder = TemporaryFolder()

  private lateinit var main: NamedIdeaSourceProvider
  private lateinit var debug: NamedIdeaSourceProvider
  private lateinit var release: NamedIdeaSourceProvider
  private lateinit var test: NamedIdeaSourceProvider
  private lateinit var testDebug: NamedIdeaSourceProvider
  private lateinit var testRelease: NamedIdeaSourceProvider
  private lateinit var androidTest: NamedIdeaSourceProvider
  private lateinit var androidTestDebug: NamedIdeaSourceProvider

  @Before
  fun setup() {
    main = createSourceProviderAt("main", ScopeType.MAIN)
    debug = createSourceProviderAt("debug", ScopeType.MAIN)
    release = createSourceProviderAt("release", ScopeType.MAIN)
    test = createSourceProviderAt("test", ScopeType.UNIT_TEST)
    testDebug = createSourceProviderAt("testDebug", ScopeType.UNIT_TEST)
    testRelease = createSourceProviderAt("testRelease", ScopeType.UNIT_TEST)
    androidTest = createSourceProviderAt("androidTest", ScopeType.ANDROID_TEST)
    androidTestDebug = createSourceProviderAt("androidTestDebug", ScopeType.ANDROID_TEST)
  }

  @Test
  fun templates_forMain() {
    val sourceProviders = createSourceProviders()
    val templates = sourceProviders.buildNamedModuleTemplatesFor(moduleRoot, listOf(main))
    assertThat(templates).hasSize(1)
    val mainTemplate = templates[0]
    assertThat(mainTemplate.name).isEqualTo("main")
    assertThat(mainTemplate.paths.getSrcDirectory("com.example"))
      .isEqualTo(main.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.getUnitTestDirectory("com.example"))
      .isEqualTo(test.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.getTestDirectory("com.example"))
      .isEqualTo(androidTest.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.mlModelsDirectories).isEqualTo(main.mlModelsDirectoryUrls.map { urlToFile(it) } )
  }

  @Test
  fun templates_forDebug() {
    val sourceProviders = createSourceProviders()
    val templates = sourceProviders.buildNamedModuleTemplatesFor(moduleRoot, listOf(debug))
    assertThat(templates).hasSize(1)
    val mainTemplate = templates[0]
    assertThat(mainTemplate.name).isEqualTo("debug")
    assertThat(mainTemplate.paths.getSrcDirectory("com.example"))
      .isEqualTo(debug.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.getUnitTestDirectory("com.example"))
      .isEqualTo(testDebug.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.getTestDirectory("com.example"))
      .isEqualTo(androidTestDebug.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
  }

  @Test
  fun templates_forRelease() {
    val sourceProviders = createSourceProviders()
    val templates = sourceProviders.buildNamedModuleTemplatesFor(moduleRoot, listOf(release))
    assertThat(templates).hasSize(1)
    val mainTemplate = templates[0]
    assertThat(mainTemplate.name).isEqualTo("release")
    assertThat(mainTemplate.paths.getSrcDirectory("com.example"))
      .isEqualTo(release.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    // `testRelease` source provider is not available in any collection and therefore test directory location in unknown.
    assertThat(mainTemplate.paths.getUnitTestDirectory("com.example")).isNull()
    // `androidTestRelease` source provider is not available in any collection and therefore test directory location in unknown.
    assertThat(mainTemplate.paths.getTestDirectory("com.example")).isNull()
  }

  @Test
  fun templates_forTest() {
    val sourceProviders = createSourceProviders()
    val templates = sourceProviders.buildNamedModuleTemplatesFor(moduleRoot, listOf(test))
    assertThat(templates).hasSize(1)
    val mainTemplate = templates[0]
    assertThat(mainTemplate.name).isEqualTo("test")
    // No source directory is available for test source providers.
    assertThat(mainTemplate.paths.getSrcDirectory("com.example")).isNull()
    assertThat(mainTemplate.paths.getUnitTestDirectory("com.example"))
      .isEqualTo(test.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
    assertThat(mainTemplate.paths.getTestDirectory("com.example"))
      .isEqualTo(androidTest.javaDirectoryUrls.first().let { urlToFile(it) }.resolve("com/example"))
  }

  private val moduleRoot: File get() = tempDirectory.root

  private fun createSourceProviders(): SourceProviders =
    SourceProvidersImpl(
      mainIdeaSourceProvider = main,
      currentSourceProviders = listOf(main, debug),
      currentUnitTestSourceProviders = listOf(test, testDebug),
      currentAndroidTestSourceProviders = listOf(androidTest, androidTestDebug),
      currentTestFixturesSourceProviders = listOf(),
      currentAndSomeFrequentlyUsedInactiveSourceProviders = listOf(main, debug, release),
      mainAndFlavorSourceProviders = listOf(main),
      generatedSources = emptySourceProvider(ScopeType.MAIN),
      generatedUnitTestSources = emptySourceProvider(ScopeType.UNIT_TEST),
      generatedAndroidTestSources = emptySourceProvider(ScopeType.ANDROID_TEST),
      generatedTestFixturesSources = emptySourceProvider(ScopeType.TEST_FIXTURES)
    )

  private fun createSourceProviderAt(
    name: String,
    scopeType: ScopeType,
    root: File = moduleRoot.resolve(name),
    manifestFile: File = File("AndroidManifest.xml"),
    javaDirectories: List<File> = listOf(File("java")),
    kotlinDirectories: List<File> = listOf(File("java"), File("kotlin")),
    resourcesDirectories: List<File> = listOf(File("resources")),
    aidlDirectories: List<File> = listOf(File("aidl")),
    renderScriptDirectories: List<File> = listOf(File("rs")),
    jniDirectories: List<File> = listOf(File("jni")),
    resDirectories: List<File> = listOf(File("res")),
    assetsDirectories: List<File> = listOf(File("assets")),
    shadersDirectories: List<File> = listOf(File("shaders")),
    mlModelsDirectories: List<File> = listOf(File("ml")),
    customSourceDirectories: Map<String, List<File>> = mapOf("toml" to listOf(File("toml"))),
    baselineProfileDirectories: List<File> = listOf(File("baslineProfiles")),
  ) =
    NamedIdeaSourceProviderImpl(
      name,
      scopeType,
      core = object : NamedIdeaSourceProviderImpl.Core {
        override val manifestFileUrl: String get() = root.resolve(manifestFile).toIdeaUrl()
        override val javaDirectoryUrls: Sequence<String> get() = javaDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val kotlinDirectoryUrls: Sequence<String> get() = kotlinDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val resourcesDirectoryUrls: Sequence<String> get() = resourcesDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val aidlDirectoryUrls: Sequence<String> get() = aidlDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val renderscriptDirectoryUrls: Sequence<String> get() = renderScriptDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val jniLibsDirectoryUrls: Sequence<String> get() = emptySequence()
        override val resDirectoryUrls: Sequence<String> get() = resDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val assetsDirectoryUrls: Sequence<String> get() = assetsDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val shadersDirectoryUrls: Sequence<String> get() = shadersDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val mlModelsDirectoryUrls: Sequence<String> get() = mlModelsDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
        override val customSourceDirectories: Map<String, Sequence<String>>
          get() = customSourceDirectories.mapValues { entry -> entry.value.map { root.resolve(it).toIdeaUrl() }.asSequence() }
        override val baselineProfileDirectoryUrls: Sequence<String> get() = baselineProfileDirectories.map { root.resolve(it).toIdeaUrl() }.asSequence()
      }
    )
}

private fun File.toIdeaUrl(): String = FilePaths.pathToIdeaUrl(this)