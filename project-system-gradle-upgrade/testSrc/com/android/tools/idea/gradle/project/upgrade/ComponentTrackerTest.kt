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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_COMPONENT_EVENT
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_PROCESSOR_EVENT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentEvent
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.Java8DefaultProcessorSettings
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.R8FullModeDefaultProcessorSettings
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.AGP_CLASSPATH_DEPENDENCY
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.ANDROID_MANIFEST_PACKAGE
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.COMPILE_RUNTIME_CONFIGURATION
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.FABRIC_CRASHLYTICS
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GMAVEN_REPOSITORY
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_PLUGINS
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_VERSION
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.JAVA8_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_PACKAGING_OPTIONS
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_ANDROID_RESOURCES
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_EMULATOR_SNAPSHOTS
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_INSTALLATION
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_LINT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_TEST_COVERAGE
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.R8_FULL_MODE_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REDUNDANT_PROPERTIES
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REMOVE_BUILD_TYPE_USE_PROGUARD
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REMOVE_IMPLEMENTATION_PROPERTIES
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REMOVE_SOURCE_SET_JNI
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.RENDER_SCRIPT_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.REWRITE_DEPRECATED_OPERATORS
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FIND_USAGES
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertSize
import org.junit.Test
import com.android.tools.idea.gradle.project.upgrade.REWRITE_DEPRECATED_OPERATORS as REWRITE_DEPRECATED_OPERATORS_INFO

@RunsInEdt
class ComponentTrackerTest : UpgradeGradleFileModelTestCase() {
  @Test
  fun testVersionInLiteralUsageTracker() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("4.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(AGP_CLASSPATH_DEPENDENCY).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("4.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(AGP_CLASSPATH_DEPENDENCY).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testAGP2ProjectUsageTracker() {
    writeToBuildFile(TestFileName("GMavenRepository/AGP2Project"))
    val processor = GMavenRepositoryRefactoringProcessor(project, GradleVersion.parse("2.3.2"), GradleVersion.parse("4.2.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("2.3.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GMAVEN_REPOSITORY).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("2.3.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GMAVEN_REPOSITORY).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testNoGradleWrapperUsageTracker() {
    val processor = GradleVersionRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("3.6.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("3.6.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GRADLE_VERSION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(0).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("3.6.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GRADLE_VERSION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(0).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testKotlinPluginVersionInLiteral() {
    writeToBuildFile(TestFileName("GradlePlugins/KotlinPluginVersionInLiteral"))
    val processor = GradlePluginsRefactoringProcessor(project, GradleVersion.parse("3.4.0"), GradleVersion.parse("4.1.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.4.0").setNewAgpVersion("4.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GRADLE_PLUGINS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.4.0").setNewAgpVersion("4.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(GRADLE_PLUGINS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

    @Test
  fun testSimpleApplicationNoLanguageLevelUsageTracker() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(JAVA8_DEFAULT).setIsEnabled(true)
                            .setJava8DefaultSettings(Java8DefaultProcessorSettings.newBuilder()
                                                       .setNoLanguageLevelAction(
                                                         Java8DefaultProcessorSettings.NoLanguageLevelAction.INSERT_OLD_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(2).setFiles(2))
        .build(),

      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(JAVA8_DEFAULT).setIsEnabled(true)
                            .setJava8DefaultSettings(Java8DefaultProcessorSettings.newBuilder()
                                                       .setNoLanguageLevelAction(
                                                         Java8DefaultProcessorSettings.NoLanguageLevelAction.INSERT_OLD_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(2).setFiles(2))
        .build()
    )
  }

  @Test
  fun testSimpleApplicationNoLanguageLevelAcceptNewUsageTracker() {
    writeToBuildFile(TestFileName("Java8Default/SimpleApplicationNoLanguageLevel"))
    val processor = Java8DefaultRefactoringProcessor(project, GradleVersion.parse("4.1.2"), GradleVersion.parse("4.2.0"))
    processor.noLanguageLevelAction = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(JAVA8_DEFAULT).setIsEnabled(true)
                            .setJava8DefaultSettings(Java8DefaultProcessorSettings.newBuilder()
                                                       .setNoLanguageLevelAction(
                                                         Java8DefaultProcessorSettings.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(2).setFiles(2))
        .build(),

      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.2").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(JAVA8_DEFAULT).setIsEnabled(true)
                            .setJava8DefaultSettings(Java8DefaultProcessorSettings.newBuilder()
                                                       .setNoLanguageLevelAction(
                                                         Java8DefaultProcessorSettings.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(2).setFiles(2))
        .build()
    )
  }

  @Test
  fun testSimpleApplicationUsageTracker() {
    writeToBuildFile(TestFileName("CompileRuntimeConfiguration/SimpleApplication"))
    val processor = CompileRuntimeConfigurationRefactoringProcessor(project, GradleVersion.parse("3.5.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(COMPILE_RUNTIME_CONFIGURATION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(6).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(COMPILE_RUNTIME_CONFIGURATION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(6).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testClasspathDependenciesUsageTracker() {
    writeToBuildFile(TestFileName("FabricCrashlytics/FabricClasspathDependencies"))
    val processor = FabricCrashlyticsRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.0.0").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(FABRIC_CRASHLYTICS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(5).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.0.0").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(FABRIC_CRASHLYTICS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(5).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testRemoveJniSingleBlockUsageTracker() {
    writeToBuildFile(TestFileName("RemoveSourceSetJni/SingleBlock"))
    val processor = REMOVE_SOURCE_SET_JNI_INFO.RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_SOURCE_SET_JNI).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_SOURCE_SET_JNI).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testAaptOptionsToAndroidResourcesUsageTracker() {
    writeToBuildFile(TestFileName("MigrateAaptOptionsToAndroidResources/AaptOptionsToAndroidResources"))
    val processor =
      MIGRATE_AAPT_OPTIONS_TO_ANDROID_RESOURCES.RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_ANDROID_RESOURCES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(6).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_ANDROID_RESOURCES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(6).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testRemoveUseProguardTwoBuildTypesUsageTracker() {
    writeToBuildFile(TestFileName("RemoveBuildTypeUseProguard/TwoBuildTypes"))
    val processor =
      REMOVE_BUILD_TYPE_USE_PROGUARD_INFO.RefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_BUILD_TYPE_USE_PROGUARD).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(2).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_BUILD_TYPE_USE_PROGUARD).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(2).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testDynamicFeature420TemplateUsageTracker() {
    writeToBuildFile(TestFileName("RemoveImplementationProperties/DynamicFeature420Template"))
    val processor = RemoveImplementationPropertiesRefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("7.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_IMPLEMENTATION_PROPERTIES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(4).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("7.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REMOVE_IMPLEMENTATION_PROPERTIES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(4).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testAdbOptionsToInstallationUsageTracker() {
    writeToBuildFile(TestFileName("MigrateAdbOptionsToInstallation/AdbOptionsToInstallation"))
    val processor =
      MIGRATE_ADB_OPTIONS_TO_INSTALLATION.RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_INSTALLATION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(3).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_INSTALLATION).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(3).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testFailureRetentionToEmulatorSnapshotsUsageTracker() {
    writeToBuildFile(TestFileName("MigrateFailureRetentionToEmulatorSnapshots/FailureRetentionToEmulatorSnapshots"))
    val processor = MIGRATE_FAILURE_RETENTION_TO_EMULATOR_SNAPSHOTS
      .RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_EMULATOR_SNAPSHOTS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(3).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_EMULATOR_SNAPSHOTS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(3).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testJacocoToTestCoverageUsageTracker() {
    writeToBuildFile(TestFileName("MigrateJacocoToTestCoverage/JacocoToTestCoverage"))
    val processor =
      MIGRATE_JACOCO_TO_TEST_COVERAGE.RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_TEST_COVERAGE).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(2).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_TEST_COVERAGE).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(2).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testMultipleLiteralPropertiesUsageTracker() {
    writeToBuildFile(TestFileName("MigratePackagingOptions/MultipleLiteralProperties"))
    val processor = MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor(
      project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_PACKAGING_OPTIONS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(8).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_PACKAGING_OPTIONS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(8).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testLintOptionsToLintExhaustiveUsageTracker() {
    writeToBuildFile(TestFileName("MigrateLintOptionsToLint/LintOptionsToLintExhaustive"))
    val processor = MIGRATE_LINT_OPTIONS_TO_LINT.RefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_LINT).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(33).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(MIGRATE_TO_LINT).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(33).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testResConfigs() {
    writeToBuildFile(TestFileName("RewriteDeprecatedOperators/ResConfigs"))
    val processor =
      REWRITE_DEPRECATED_OPERATORS_INFO.RefactoringProcessor(project, GradleVersion.parse("4.2.0"), GradleVersion.parse("9.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REWRITE_DEPRECATED_OPERATORS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(2).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.2.0").setNewAgpVersion("9.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REWRITE_DEPRECATED_OPERATORS).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(2).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testBuildToolsVersion41() {
    writeToBuildFile(TestFileName("RedundantProperties/BuildToolsVersion41"))
    val processor = RedundantPropertiesRefactoringProcessor(project, GradleVersion.parse("4.1.0"), GradleVersion.parse("7.1.0"))
    processor.run()
    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.0").setNewAgpVersion("7.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REDUNDANT_PROPERTIES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.1.0").setNewAgpVersion("7.1.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(REDUNDANT_PROPERTIES).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testNoAndroidManifestsUsageTracker() {
    val processor = AndroidManifestPackageToNamespaceRefactoringProcessor(project, GradleVersion.parse("4.0.0"), GradleVersion.parse("4.2.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.0.0").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(ANDROID_MANIFEST_PACKAGE).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(0).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("4.0.0").setNewAgpVersion("4.2.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(ANDROID_MANIFEST_PACKAGE).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(0).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testR8FullModeNoGradlePropertiesUsageTracker() {
    val processor = R8FullModeDefaultRefactoringProcessor(project, GradleVersion.parse("7.3.0"), GradleVersion.parse("8.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.3.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(
          UpgradeAssistantComponentInfo.newBuilder().setKind(R8_FULL_MODE_DEFAULT).setIsEnabled(true)
            .setR8FullModeDefaultSettings(R8FullModeDefaultProcessorSettings.newBuilder()
                                            .setNoPropertyPresentAction(
                                              R8FullModeDefaultProcessorSettings.NoPropertyPresentAction.INSERT_OLD_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.3.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(
          UpgradeAssistantComponentInfo.newBuilder().setKind(R8_FULL_MODE_DEFAULT).setIsEnabled(true)
            .setR8FullModeDefaultSettings(R8FullModeDefaultProcessorSettings.newBuilder()
                                            .setNoPropertyPresentAction(
                                              R8FullModeDefaultProcessorSettings.NoPropertyPresentAction.INSERT_OLD_DEFAULT)))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
    )
  }

  @Test
  fun testNoRenderScriptUsageTracker() {
    val processor = RenderScriptDefaultRefactoringProcessor(project, GradleVersion.parse("7.0.0"), GradleVersion.parse("8.0.0"))
    processor.run()

    checkComponentEvents(
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(RENDER_SCRIPT_DEFAULT).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(0).setFiles(2))
        .build(),
      UpgradeAssistantComponentEvent.newBuilder().setUpgradeUuid(processor.uuid).setCurrentAgpVersion("7.0.0").setNewAgpVersion("8.0.0")
        .setComponentInfo(UpgradeAssistantComponentInfo.newBuilder().setKind(RENDER_SCRIPT_DEFAULT).setIsEnabled(true))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(0).setFiles(2))
        .build(),
    )
  }

  private fun checkComponentEvents(vararg expectedEvents: UpgradeAssistantComponentEvent) {
    val events = tracker.usages
      .filter { it.studioEvent.kind == UPGRADE_ASSISTANT_COMPONENT_EVENT || it.studioEvent.kind == UPGRADE_ASSISTANT_PROCESSOR_EVENT }
      .sortedBy { it.timestamp }
      .map { it.studioEvent }
    val processorEvents = events.filter { it.kind == UPGRADE_ASSISTANT_PROCESSOR_EVENT }
    assertSize(0, processorEvents)
    val componentEvents = events.filter { it.kind == UPGRADE_ASSISTANT_COMPONENT_EVENT }
    assertSize(expectedEvents.size, componentEvents)
    assertThat(componentEvents.map { it.upgradeAssistantComponentEvent }.toList()).isEqualTo(expectedEvents.toList())
  }
}