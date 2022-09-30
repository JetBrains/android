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
package com.android.tools.idea.gradle.project.upgrade

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@RunsInEdt
class AgpUpgradeRefactoringProcessorTest : UpgradeGradleFileModelTestCase() {
  @Before
  fun replaceSyncInvoker() {
    // At the moment, the AgpUpgrade refactoring processor itself runs sync, which means that we need to
    // replace the invoker with a fake one here (because we are not working on complete projects which
    // sync correctly).
    //
    // The location of the sync invoker might move out from here, perhaps to a subscriber to a message bus
    // listening for refactoring events, at which point this would no longer be necessary (though we might
    // need to make sure that there is no listener triggering sync while running unit tests).
    val ideComponents = IdeComponents(projectRule.fixture)
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
  }

  private fun everythingDisabledNoEffectOn(filename: String) {
    writeToBuildFile(TestFileName(filename))
    val latestKnownVersion = ANDROID_GRADLE_PLUGIN_VERSION
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("1.0.0"), AgpVersion.parse(latestKnownVersion))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processor.run()
    verifyFileContents(buildFile, TestFileName(filename))
  }

  private fun everythingEnabledNoEffectOn(filename: String) {
    writeToBuildFile(TestFileName(filename))
    val latestKnownVersion = ANDROID_GRADLE_PLUGIN_VERSION
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("1.0.0"), AgpVersion.parse(latestKnownVersion))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = true }
    processor.run()
    verifyFileContents(buildFile, TestFileName(filename))
  }

  // At the moment, the only processor which adds content to build files (as opposed to modifying or deleting existing content) is the
  // Java8 processor.
  private fun everythingButJava8EnabledNoEffectOn(filename: String) {
    writeToBuildFile(TestFileName(filename))
    val latestKnownVersion = ANDROID_GRADLE_PLUGIN_VERSION
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("1.0.0"), AgpVersion.parse(latestKnownVersion))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it !is Java8DefaultRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName(filename))
  }

  @Test
  fun testEverythingDisabledNoEffectOnAgpVersion() {
    everythingDisabledNoEffectOn("AgpVersion/VersionInLiteral")
  }

  @Test
  fun testEverythingDisabledNoEffectOnJava8Default() {
    everythingDisabledNoEffectOn("Java8Default/SimpleApplicationNoLanguageLevel")
  }

  @Test
  fun testEverythingDisabledNoEffectOnCompileRuntimeConfiguration() {
    everythingDisabledNoEffectOn("CompileRuntimeConfiguration/SimpleApplication")
  }

  @Test
  fun testEverythingDisabledNoEffectOnGMavenRepository() {
    everythingDisabledNoEffectOn("GMavenRepository/AGP2Project")
  }

  @Ignore("gradle-wrapper.properties is not a build file") // TODO(b/152854665)
  fun testEverythingDisabledNoEffectOnGradleVersion() {
    everythingDisabledNoEffectOn("GradleVersion/OldGradleVersion")
  }

  @Test
  fun testEverythingEnabledNoEffectOnEmpty() {
    everythingEnabledNoEffectOn("AgpUpgrade/Empty")
  }

  @Test
  fun testEverythingButJava8EnabledNoEffectOnEmpty() {
    everythingButJava8EnabledNoEffectOn("AgpUpgrade/Empty")
  }

  @Test
  fun testEverythingButJava8EnabledNoEffectOnMinimal() {
    everythingButJava8EnabledNoEffectOn("AgpUpgrade/Minimal")
  }

  @Test
  fun testEnabledEffectOnAgpVersion() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is AgpVersionRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteralExpected"))
  }

  @Test
  fun testEnabledAgpVersionHasTarget() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is AgpVersionRefactoringProcessor }
    processor.run()
    assertThat(processor.targets).isNotEmpty()
  }

  @Test
  fun testDisabledHasNoEffectOnAgpVersion() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processor.run()
    verifyFileContents(buildFile, TestFileName("AgpVersion/VersionInLiteral"))
  }

  @Test
  fun testDisabledAgpVersionStillHasTarget() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = false }
    processor.run()
    assertThat(processor.targets).isNotEmpty()
  }

  @Test
  fun testEnabledEffectOnJava8Default() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.1.2"), AgpVersion.parse("4.2.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is Java8DefaultRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("Java8Default/SimpleApplicationNoLanguageLevelExpected"))
  }

  @Test
  fun testEnabledEffectOnCompileRuntimeConfiguration() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("5.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is CompileRuntimeConfigurationRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("CompileRuntimeConfiguration/SimpleApplicationExpected"))
  }

  @Test
  fun testEnabledEffectOnGMavenRepository() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP2Project"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("2.3.2"), AgpVersion.parse("4.2.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is GMavenRepositoryRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("GMavenRepository/AGP2ProjectExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateBuildFeatures() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateBuildFeaturesRefactoringProcessor() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_TO_BUILD_FEATURES_INFO

    writeToBuildFile(TestFileName("MigrateToBuildFeatures/ViewBindingEnabledLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.6.0"), AgpVersion.parse("7.0.0"))
    assumeTrue(processor.componentRefactoringProcessors.any { it.isMigrateBuildFeaturesRefactoringProcessor() }) // b/175097233
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateBuildFeaturesRefactoringProcessor() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateToBuildFeatures/ViewBindingEnabledLiteralExpected"))
  }

  @Test
  fun testEnabledEffectOnRemoveSourceSetJni() {
    fun AgpUpgradeComponentRefactoringProcessor.isRemoveSourceSetJni() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == REMOVE_SOURCE_SET_JNI_INFO

    writeToBuildFile(TestFileName("RemoveSourceSetJni/SingleBlock"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isRemoveSourceSetJni() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveSourceSetJni/SingleBlockExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateAaptResources() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateAaptResources() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_AAPT_OPTIONS_TO_ANDROID_RESOURCES

    writeToBuildFile(TestFileName("MigrateAaptOptionsToAndroidResources/AaptOptionsToAndroidResources"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateAaptResources() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateAaptOptionsToAndroidResources/AaptOptionsToAndroidResourcesExpected"))
  }

  @Test
  fun testEnabledEffectOnRemoveUseProguard() {
    fun AgpUpgradeComponentRefactoringProcessor.isRemoveUseProguard() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == REMOVE_BUILD_TYPE_USE_PROGUARD_INFO

    writeToBuildFile(TestFileName("RemoveBuildTypeUseProguard/TwoBuildTypes"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isRemoveUseProguard() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("RemoveBuildTypeUseProguard/TwoBuildTypesExpected"))
  }

  @Ignore("gradle-wrapper.properties is not a build file") // TODO(b/152854665)
  fun testEnabledEffectOnGradleVersion() {
    writeToBuildFile(TestFileName("GradleVersion/OldGradleVersion"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is GradleVersionRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradleVersion/OldGradleVersion410Expected"))
  }

  @Test
  fun testEnabledEffectOnGradlePlugins() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.4.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is GradlePluginsRefactoringProcessor }
    processor.run()
    verifyFileContents(buildFile, TestFileName("GradlePlugins/KotlinPluginVersionInLiteralExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateAdbOptions() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateAdbOptions() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_ADB_OPTIONS_TO_INSTALLATION

    writeToBuildFile(TestFileName("MigrateAdbOptionsToInstallation/AdbOptionsToInstallation"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateAdbOptions() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateAdbOptionsToInstallation/AdbOptionsToInstallationExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateFailureRetention() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateFailureRetention() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_FAILURE_RETENTION_TO_EMULATOR_SNAPSHOTS

    writeToBuildFile(TestFileName("MigrateFailureRetentionToEmulatorSnapshots/FailureRetentionToEmulatorSnapshots"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateFailureRetention() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateFailureRetentionToEmulatorSnapshots/FailureRetentionToEmulatorSnapshotsExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateJacoco() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateJacoco() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_JACOCO_TO_TEST_COVERAGE

    writeToBuildFile(TestFileName("MigrateJacocoToTestCoverage/JacocoToTestCoverage"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateJacoco() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateJacocoToTestCoverage/JacocoToTestCoverageExpected"))
  }

  @Test
  fun testEnabledEffectOnMigratePackagingOptions() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/MultipleLiteralProperties"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach {
      it.isEnabled = it is MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor
    }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigratePackagingOptions/MultipleLiteralPropertiesExpected"))
  }

  @Test
  fun testEnabledEffectOnMigrateLintOptions() {
    fun AgpUpgradeComponentRefactoringProcessor.isMigrateLintOptions() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == MIGRATE_LINT_OPTIONS_TO_LINT

    writeToBuildFile(TestFileName("MigrateLintOptionsToLint/LintOptionsToLint"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.0.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isMigrateLintOptions() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("MigrateLintOptionsToLint/LintOptionsToLintExpected"))
  }

  @Test
  fun testEnabledEffectOnRewriteDeprecatedOperators() {
    fun AgpUpgradeComponentRefactoringProcessor.isRewriteDeprecatedOperators() =
      this is PropertiesOperationsRefactoringInfo.RefactoringProcessor && info == REWRITE_DEPRECATED_OPERATORS

    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/ResConfigs"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("4.2.0"), AgpVersion.parse("8.0.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it.isRewriteDeprecatedOperators() }
    processor.run()
    verifyFileContents(buildFile, TestFileName("RewriteDeprecatedOperators/ResConfigsExpected"))
  }
}