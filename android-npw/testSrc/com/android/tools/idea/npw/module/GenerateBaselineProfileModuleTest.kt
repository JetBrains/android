/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.MockitoKt
import com.android.tools.idea.npw.module.recipes.baselineProfilesModule.generateBaselineProfilesModule
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GenerateBaselineProfileModuleTest {

  companion object {
    private const val MODULE_NAME_APP = "app"
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @get:Rule
  var tmpFolderRule = TemporaryFolder()

  @Test
  fun withKotlinCodeAndBuildGradleKtsAndAgpCurrent() {

    val (rootDir, srcDir) = runTemplateGeneration(
      agpVersion = AgpVersion(8, 3, 0),
      sourceCodeLanguage = Language.Kotlin,
      useGradleKts = true,
      useGmd = true,
      projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
    )

    val buildGradleContent = rootDir.resolve("build.gradle.kts").readText()
    assertThat(buildGradleContent).isEqualTo(FixturesAgpCurrent.BUILD_GRADLE_KTS_WITH_GMD_WITH_AGP_CURRENT)

    val baselineProfileGeneratorContent = srcDir.resolve("BaselineProfileGenerator.kt").readText()
    assertThat(baselineProfileGeneratorContent).isEqualTo(FixturesAgpCurrent.BASELINE_PROFILE_GENERATOR_KOTLIN_WITH_AGP_CURRENT)

    val startupBenchmarksContent = srcDir.resolve("StartupBenchmarks.kt").readText()
    assertThat(startupBenchmarksContent).isEqualTo(FixturesAgpCurrent.STARTUP_BENCHMARKS_KOTLIN_WITH_AGP_CURRENT)
  }

  @Test
  fun withKotlinCodeAndBuildGradleKtsAndAgp810() {

    val (rootDir, srcDir) = runTemplateGeneration(
      agpVersion = AgpVersion(8, 1, 0),
      sourceCodeLanguage = Language.Kotlin,
      useGradleKts = true,
      useGmd = true,
      projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_81
    )

    val buildGradleContent = rootDir.resolve("build.gradle.kts").readText()
    assertThat(buildGradleContent).isEqualTo(FixturesAgp810.BUILD_GRADLE_KTS_WITH_GMD_WITH_AGP_8_1_0)

    val baselineProfileGeneratorContent = srcDir.resolve("BaselineProfileGenerator.kt").readText()
    assertThat(baselineProfileGeneratorContent).isEqualTo(FixturesAgp810.BASELINE_PROFILE_GENERATOR_KOTLIN_WITH_AGP_8_1_0)

    val startupBenchmarksContent = srcDir.resolve("StartupBenchmarks.kt").readText()
    assertThat(startupBenchmarksContent).isEqualTo(FixturesAgp810.STARTUP_BENCHMARKS_KOTLIN_WITH_AGP_8_1_0)
  }

  @Test
  fun withJavaCodeAndBuildGradleGroovyAndAgpCurrent() {

    val (rootDir, srcDir) = runTemplateGeneration(
      agpVersion = AgpVersion(8, 3, 0),
      sourceCodeLanguage = Language.Java,
      useGradleKts = false,
      useGmd = false,
      projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
    )

    val buildGradleContent = rootDir.resolve("build.gradle").readText()
    assertThat(buildGradleContent).isEqualTo(FixturesAgpCurrent.BUILD_GRADLE_GROOVY_WITHOUT_GMD_WITH_AGP_CURRENT)

    val baselineProfileGeneratorContent = srcDir.resolve("BaselineProfileGenerator.java").readText()
    assertThat(baselineProfileGeneratorContent).isEqualTo(FixturesAgpCurrent.BASELINE_PROFILE_GENERATOR_JAVA_WITH_AGP_CURRENT)

    val startupBenchmarksContent = srcDir.resolve("StartupBenchmarks.java").readText()
    assertThat(startupBenchmarksContent).isEqualTo(FixturesAgpCurrent.STARTUP_BENCHMARKS_JAVA_WITH_AGP_CURRENT)
  }

  @Test
  fun withJavaCodeAndBuildGradleGroovyAndAgp810() {

    val (rootDir, srcDir) = runTemplateGeneration(
      agpVersion = AgpVersion(8, 1, 0),
      sourceCodeLanguage = Language.Java,
      useGradleKts = false,
      useGmd = false,
      projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_81
    )

    val buildGradleContent = rootDir.resolve("build.gradle").readText()
    assertThat(buildGradleContent).isEqualTo(FixturesAgp810.BUILD_GRADLE_GROOVY_WITHOUT_GMD_WITH_AGP_8_1_0)

    val baselineProfileGeneratorContent = srcDir.resolve("BaselineProfileGenerator.java").readText()
    assertThat(baselineProfileGeneratorContent).isEqualTo(FixturesAgp810.BASELINE_PROFILE_GENERATOR_JAVA_WITH_AGP_8_1_0)

    val startupBenchmarksContent = srcDir.resolve("StartupBenchmarks.java").readText()
    assertThat(startupBenchmarksContent).isEqualTo(FixturesAgp810.STARTUP_BENCHMARKS_JAVA_WITH_AGP_8_1_0)
  }

  private fun runTemplateGeneration(
    agpVersion: AgpVersion,
    sourceCodeLanguage: Language,
    useGradleKts: Boolean,
    useGmd: Boolean,
    projectRuleAgpVersion: AgpVersionSoftwareEnvironmentDescriptor
  ): Pair<File, File> {
    val name = "baselineprofile"
    val buildApi = ApiVersion(34, "34")
    val targetApi = ApiVersion(34, "34")
    val minApi = ApiVersion(34, "34")
    val kotlinVersion = "1.9.0"
    val packageName = "com.test.packagename"
    val srcDir = tmpFolderRule.root.resolve("src").also { it.mkdir() }
    val rootDir = tmpFolderRule.root

    projectRule.loadProject(projectPath = TestProjectPaths.ANDROIDX_SIMPLE, agpVersion = projectRuleAgpVersion)

    val mockProjectTemplateData = MockitoKt.mock<ProjectTemplateData>()
    MockitoKt.whenever(mockProjectTemplateData.agpVersion).thenReturn(agpVersion)
    val mockModuleTemplateData = MockitoKt.mock<ModuleTemplateData>()
    MockitoKt.whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)

    val renderingContext = RenderingContext(
      project = projectRule.project,
      module = projectRule.getModule(MODULE_NAME_APP),
      commandName = "New Baseline Profile Module",
      templateData = mockModuleTemplateData,
      outputRoot = tmpFolderRule.root,
      moduleRoot = tmpFolderRule.root,
      dryRun = false,
      showErrors = true
    )

    val newModuleTemplateData = ModuleTemplateData(
      projectTemplateData = ProjectTemplateData(
        androidXSupport = true,
        agpVersion = agpVersion,
        additionalMavenRepos = listOf(),
        sdkDir = null,
        language = sourceCodeLanguage,
        kotlinVersion = kotlinVersion,
        rootDir = tmpFolderRule.root,
        applicationPackage = packageName,
        includedFormFactorNames = mapOf(),
        debugKeystoreSha1 = null,
        overridePathCheck = null,
        isNewProject = false,
      ),
      themesData = ThemesData("appname"),
      apis = ApiTemplateData(
        buildApi = buildApi,
        targetApi = targetApi,
        minApi = minApi,
        appCompatVersion = 0
      ),
      srcDir = srcDir,
      resDir = tmpFolderRule.root.resolve("res").also { it.mkdir() },
      manifestDir = tmpFolderRule.root.resolve("manifest").also { it.mkdir() },
      testDir = tmpFolderRule.root.resolve("test").also { it.mkdir() },
      unitTestDir = tmpFolderRule.root.resolve("unitTest").also { it.mkdir() },
      aidlDir = tmpFolderRule.root.resolve("aidl").also { it.mkdir() },
      rootDir = rootDir,
      isNewModule = true,
      name = name,
      isLibrary = false,
      packageName = packageName,
      formFactor = FormFactor.Generic,
      baseFeature = null,
      viewBindingSupport = ViewBindingSupport.NOT_SUPPORTED,
      category = Category.Application,
      isMaterial3 = true,
      isCompose = false,
      useGenericLocalTests = true,
      useGenericInstrumentedTests = true,
    )

    runWriteCommandAction(projectRule.project) {
      DefaultRecipeExecutor(renderingContext).generateBaselineProfilesModule(
        newModule = newModuleTemplateData,
        useGradleKts = useGradleKts,
        useGmd = useGmd,
        targetModule = projectRule.getModule(MODULE_NAME_APP),
      )
    }

    return Pair(rootDir, srcDir)
  }
}

object FixturesAgpCurrent {

  val BUILD_GRADLE_GROOVY_WITHOUT_GMD_WITH_AGP_CURRENT = """
plugins {
}

android {
  namespace 'com.test.packagename'
  compileSdk 34

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  defaultConfig {
        minSdk 34
        targetSdk 34

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
useConnectedDevices = true
}

dependencies {
}

androidComponents {
    onVariants(selector().all()) {  v ->
        def artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}
""".trimIndent()

  val BUILD_GRADLE_KTS_WITH_GMD_WITH_AGP_CURRENT = """
import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
}

android {
  namespace = "com.test.packagename"
  compileSdk = 34

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
        jvmTarget = "1.8"
    }

  defaultConfig {
        minSdk = 34
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "google"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
managedDevices += "pixel6Api34"
useConnectedDevices = false
}

dependencies {
}

androidComponents {
    onVariants {  v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}
""".trimIndent()

  val BASELINE_PROFILE_GENERATOR_JAVA_WITH_AGP_CURRENT = """
package com.test.packagename;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test class generates a basic startup baseline profile for the target package.
 * <p>
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the <a href="https://d.android.com/topic/performance/baselineprofiles">baseline profile documentation</a>
 * for more information.
 * <p>
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent {@code generateBaselineProfile} gradle task:
 * <pre>
 * ./gradlew :app:generateReleaseBaselineProfile
 * </pre>
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 * <p>
 * Check <a href="https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args">documentation</a>
 * for more information about instrumentation arguments.
 * <p>
 * After you run the generator, you can verify the improvements running the {@link StartupBenchmarks} benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BaselineProfileGenerator {
    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generate() {
        // The application id for the running build variant is read from the instrumentation arguments.
String targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId");
if (targetAppId == null) {
    throw new RuntimeException("targetAppId not passed as instrumentation runner arg");
}
        baselineProfileRule.collect(
            /* packageName = */ targetAppId,
            /* maxIterations = */ 15,
            /* stableIterations = */ 3,
            /* outputFilePrefix = */ null,
            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            /* includeInStartupProfile = */ true,
            scope -> {
                // This block defines the app's critical user journey. Here we are interested in
                // optimizing for app startup. But you can also navigate and scroll
                // through your most important UI.

                // Start default activity for your app
                scope.pressHome();
                scope.startActivityAndWait();

                // TODO Write more interactions to optimize advanced journeys of your app.
                // For example:
                // 1. Wait until the content is asynchronously loaded
                // 2. Scroll the feed content
                // 3. Navigate to detail screen

                // Check UiAutomator documentation for more information how to interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator

                return Unit.INSTANCE;
        });
    }
}
""".trimIndent()

  val BASELINE_PROFILE_GENERATOR_KOTLIN_WITH_AGP_CURRENT = """
package com.test.packagename

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The application id for the running build variant is read from the instrumentation arguments.
        rule.collect(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: throw Exception("targetAppId not passed as instrumentation runner arg"),

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            // TODO Write more interactions to optimize advanced journeys of your app.
            // For example:
            // 1. Wait until the content is asynchronously loaded
            // 2. Scroll the feed content
            // 3. Navigate to detail screen

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}
""".trimIndent()

  val STARTUP_BENCHMARKS_JAVA_WITH_AGP_CURRENT = """
package com.test.packagename;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing {@code CompilationMode.None}, which represents the app with no Baseline
 * Profiles optimizations, and {@code CompilationMode.Partial}, which uses Baseline Profiles.
 * <p>
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * <pre>
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * </pre>
 * <p>
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 * <p>
 * For more information, see the <a href="https://d.android.com/macrobenchmark#create-macrobenchmark">Macrobenchmark documentation</a>
 * and the <a href="https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args">instrumentation arguments documentation</a>.
 **/
@RunWith(AndroidJUnit4.class)
@LargeTest
public class StartupBenchmarks {

    @Rule
    public MacrobenchmarkRule rule = new MacrobenchmarkRule();

    @Test
    public void startupCompilationNone() {
        benchmark(new CompilationMode.None());
    }

    @Test
    public void startupCompilationBaselineProfiles() {
        benchmark(new CompilationMode.Partial(BaselineProfileMode.Require));
    }

    private void benchmark(CompilationMode compilationMode) {
        // The application id for the running build variant is read from the instrumentation arguments.
String targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId");
if (targetAppId == null) {
    throw new RuntimeException("targetAppId not passed as instrumentation runner arg");
}
        rule.measureRepeated(
            targetAppId,
            Collections.singletonList(new StartupTimingMetric()),
            compilationMode,
            StartupMode.COLD,
            10,
            setupScope -> {
                setupScope.pressHome();
                return Unit.INSTANCE;
            },
            measureScope -> {
                measureScope.startActivityAndWait();

                // TODO Add interactions to wait for when your app is fully drawn.
                // The app is fully drawn when Activity.reportFullyDrawn is called.
                // For Jetpack Compose, you can use ReportDrawn, ReportDrawnWhen and ReportDrawnAfter
                // from the AndroidX Activity library.

                // Check the UiAutomator documentation for more information on how to
                // interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator
                return Unit.INSTANCE;
            }
        );
    }
}
""".trimIndent()

  val STARTUP_BENCHMARKS_KOTLIN_WITH_AGP_CURRENT = """
package com.test.packagename

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing [CompilationMode.None], which represents the app with no Baseline
 * Profiles optimizations, and [CompilationMode.Partial], which uses Baseline Profiles.
 *
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 *
 * For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark)
 * and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() =
        benchmark(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() =
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) {
        // The application id for the running build variant is read from the instrumentation arguments.
        rule.measureRepeated(
            packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: throw Exception("targetAppId not passed as instrumentation runner arg"),
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()

                // TODO Add interactions to wait for when your app is fully drawn.
                // The app is fully drawn when Activity.reportFullyDrawn is called.
                // For Jetpack Compose, you can use ReportDrawn, ReportDrawnWhen and ReportDrawnAfter
                // from the AndroidX Activity library.

                // Check the UiAutomator documentation for more information on how to
                // interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator
            }
        )
    }
}
""".trimIndent()
}

object FixturesAgp810 {

  val BUILD_GRADLE_GROOVY_WITHOUT_GMD_WITH_AGP_8_1_0 = """
plugins {
}

android {
  namespace 'com.test.packagename'
  compileSdk 34

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  defaultConfig {
        minSdk 34
        targetSdk 34

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
useConnectedDevices = true
}

dependencies {
}
""".trimIndent()

  val BUILD_GRADLE_KTS_WITH_GMD_WITH_AGP_8_1_0 = """
import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
}

android {
  namespace = "com.test.packagename"
  compileSdk = 34

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions {
        jvmTarget = "1.8"
    }

  defaultConfig {
        minSdk = 34
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "google"
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
managedDevices += "pixel6Api34"
useConnectedDevices = false
}

dependencies {
}
""".trimIndent()

  val BASELINE_PROFILE_GENERATOR_JAVA_WITH_AGP_8_1_0 = """
package com.test.packagename;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test class generates a basic startup baseline profile for the target package.
 * <p>
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the <a href="https://d.android.com/topic/performance/baselineprofiles">baseline profile documentation</a>
 * for more information.
 * <p>
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent {@code generateBaselineProfile} gradle task:
 * <pre>
 * ./gradlew :app:generateReleaseBaselineProfile
 * </pre>
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 * <p>
 * Check <a href="https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args">documentation</a>
 * for more information about instrumentation arguments.
 * <p>
 * After you run the generator, you can verify the improvements running the {@link StartupBenchmarks} benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BaselineProfileGenerator {
    @Rule
    public BaselineProfileRule baselineProfileRule = new BaselineProfileRule();

    @Test
    public void generate() {
        // This example works only with the variant with application id `com.example.google.androidx`.
        baselineProfileRule.collect(
            /* packageName = */ "com.example.google.androidx",
            /* maxIterations = */ 15,
            /* stableIterations = */ 3,
            /* outputFilePrefix = */ null,
            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            /* includeInStartupProfile = */ true,
            scope -> {
                // This block defines the app's critical user journey. Here we are interested in
                // optimizing for app startup. But you can also navigate and scroll
                // through your most important UI.

                // Start default activity for your app
                scope.pressHome();
                scope.startActivityAndWait();

                // TODO Write more interactions to optimize advanced journeys of your app.
                // For example:
                // 1. Wait until the content is asynchronously loaded
                // 2. Scroll the feed content
                // 3. Navigate to detail screen

                // Check UiAutomator documentation for more information how to interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator

                return Unit.INSTANCE;
        });
    }
}
""".trimIndent()

  val BASELINE_PROFILE_GENERATOR_KOTLIN_WITH_AGP_8_1_0 = """
package com.test.packagename

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class generates a basic startup baseline profile for the target package.
 *
 * We recommend you start with this but add important user flows to the profile to improve their performance.
 * Refer to the [baseline profile documentation](https://d.android.com/topic/performance/baselineprofiles)
 * for more information.
 *
 * You can run the generator with the "Generate Baseline Profile" run configuration in Android Studio or
 * the equivalent `generateBaselineProfile` gradle task:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 * The run configuration runs the Gradle task and applies filtering to run only the generators.
 *
 * Check [documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args)
 * for more information about available instrumentation arguments.
 *
 * After you run the generator, you can verify the improvements running the [StartupBenchmarks] benchmark.
 *
 * When using this class to generate a baseline profile, only API 33+ or rooted API 28+ are supported.
 *
 * The minimum required version of androidx.benchmark to generate a baseline profile is 1.2.0.
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // This example works only with the variant with application id `com.example.google.androidx`."
        rule.collect(
            packageName = "com.example.google.androidx",

            // See: https://d.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
            includeInStartupProfile = true
        ) {
            // This block defines the app's critical user journey. Here we are interested in
            // optimizing for app startup. But you can also navigate and scroll through your most important UI.

            // Start default activity for your app
            pressHome()
            startActivityAndWait()

            // TODO Write more interactions to optimize advanced journeys of your app.
            // For example:
            // 1. Wait until the content is asynchronously loaded
            // 2. Scroll the feed content
            // 3. Navigate to detail screen

            // Check UiAutomator documentation for more information how to interact with the app.
            // https://d.android.com/training/testing/other-components/ui-automator
        }
    }
}
""".trimIndent()

  val STARTUP_BENCHMARKS_JAVA_WITH_AGP_8_1_0 = """
package com.test.packagename;

import androidx.benchmark.macro.BaselineProfileMode;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import kotlin.Unit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing {@code CompilationMode.None}, which represents the app with no Baseline
 * Profiles optimizations, and {@code CompilationMode.Partial}, which uses Baseline Profiles.
 * <p>
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * <pre>
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * </pre>
 * <p>
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 * <p>
 * For more information, see the <a href="https://d.android.com/macrobenchmark#create-macrobenchmark">Macrobenchmark documentation</a>
 * and the <a href="https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args">instrumentation arguments documentation</a>.
 **/
@RunWith(AndroidJUnit4.class)
@LargeTest
public class StartupBenchmarks {

    @Rule
    public MacrobenchmarkRule rule = new MacrobenchmarkRule();

    @Test
    public void startupCompilationNone() {
        benchmark(new CompilationMode.None());
    }

    @Test
    public void startupCompilationBaselineProfiles() {
        benchmark(new CompilationMode.Partial(BaselineProfileMode.Require));
    }

    private void benchmark(CompilationMode compilationMode) {
        // This example works only with the variant with application id `com.example.google.androidx`.
        rule.measureRepeated(
            "com.example.google.androidx",
            Collections.singletonList(new StartupTimingMetric()),
            compilationMode,
            StartupMode.COLD,
            10,
            setupScope -> {
                setupScope.pressHome();
                return Unit.INSTANCE;
            },
            measureScope -> {
                measureScope.startActivityAndWait();

                // TODO Add interactions to wait for when your app is fully drawn.
                // The app is fully drawn when Activity.reportFullyDrawn is called.
                // For Jetpack Compose, you can use ReportDrawn, ReportDrawnWhen and ReportDrawnAfter
                // from the AndroidX Activity library.

                // Check the UiAutomator documentation for more information on how to
                // interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator
                return Unit.INSTANCE;
            }
        );
    }
}
""".trimIndent()

  val STARTUP_BENCHMARKS_KOTLIN_WITH_AGP_8_1_0 = """
package com.test.packagename

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing [CompilationMode.None], which represents the app with no Baseline
 * Profiles optimizations, and [CompilationMode.Partial], which uses Baseline Profiles.
 *
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 *
 * For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark)
 * and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 **/
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() =
        benchmark(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() =
        benchmark(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun benchmark(compilationMode: CompilationMode) {
        // This example works only with the variant with application id `com.example.google.androidx`."
        rule.measureRepeated(
            packageName = "com.example.google.androidx",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()

                // TODO Add interactions to wait for when your app is fully drawn.
                // The app is fully drawn when Activity.reportFullyDrawn is called.
                // For Jetpack Compose, you can use ReportDrawn, ReportDrawnWhen and ReportDrawnAfter
                // from the AndroidX Activity library.

                // Check the UiAutomator documentation for more information on how to
                // interact with the app.
                // https://d.android.com/training/testing/other-components/ui-automator
            }
        )
    }
}
""".trimIndent()
}