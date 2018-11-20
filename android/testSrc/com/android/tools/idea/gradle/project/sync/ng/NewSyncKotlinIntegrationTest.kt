/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.CompilerSettings
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.junit.Test

/**
 * This test ensures the new and old sync produce similar Facet configurations with only the expected differences.
 */
class NewSyncKotlinIntegrationTest : AndroidGradleTestCase() {
  @Test
  fun `test Verify new sync and old sync return expected results`() {
    // Ensure we use old sync by changing GradleExperimentalSettings
    val oldSyncValue = GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = false
    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(false)

    // Sync the test project using the old sync
    loadProject(NEW_SYNC_KOTLIN_TEST)

    val modules = ModuleManager.getInstance(project).modules

    val kotlinFacetMap = modules.map { it to KotlinFacet.get(it) }.toMap()
    val gradleFacetMap = modules.map { it to GradleFacet.getInstance(it)?.gradleModuleModel?.copy() }.toMap()
    modules.forEach { it.verifyFacetPresence() }


    StudioFlags.NEW_SYNC_INFRA_ENABLED.override(true)
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = true

    // Sync with the new sync
    requestSyncAndWait()
    modules.forEach { it.verifyFacetPresence() }

    // Compare the new facets with the old ones
    modules.map { it.verifyKotlinConfiguration(kotlinFacetMap[it]) }
    modules.map { it.verifyGradleFacetConfiguration(gradleFacetMap[it]) }

    StudioFlags.NEW_SYNC_INFRA_ENABLED.clearOverride()
    GradleExperimentalSettings.getInstance().USE_SINGLE_VARIANT_SYNC = oldSyncValue
  }

  private fun GradleModuleModel.copy(): GradleModuleModel? =
    GradleModuleModel(moduleName, taskNames, gradlePath, rootFolderPath,
                      gradlePlugins, buildFilePath!!, gradleVersion!!, agpVersion)

  private fun Module.verifyGradleFacetConfiguration(oldGradleModuleModel: GradleModuleModel?) {
    val facet = GradleFacet.getInstance(this)
    if (facet == null) {
      assertNull(oldGradleModuleModel)
      return
    }

    val gradleModuleModel = facet.gradleModuleModel
    if (gradleModuleModel == null) {
      assertNull(oldGradleModuleModel)
      return
    }
    assertNotNull(oldGradleModuleModel)

    assertEquals(oldGradleModuleModel!!.gradlePath, gradleModuleModel.gradlePath)
    // Gradle version in the new Sync is 5.0 vs 5.0-rc-1 for the old sync
    assertEquals(oldGradleModuleModel.gradleVersion, gradleModuleModel.gradleVersion)
    assertEquals(oldGradleModuleModel.rootFolderPath, gradleModuleModel.rootFolderPath)

    // Old task names should contain 4 more tasks than the new one. These tasks are idea specific
    val extraOldSyncTasks = listOf("cleanIdea", "idea", "ideaModule", "cleanIdeaModule").map { "${gradleModuleModel.gradlePath}:$it" }

    assertContainsElements(oldGradleModuleModel.taskNames, gradleModuleModel.taskNames)
    assertContainsElements(oldGradleModuleModel.taskNames, extraOldSyncTasks)
    assertDoesntContain(gradleModuleModel.taskNames, extraOldSyncTasks)
    assertEquals(oldGradleModuleModel.taskNames.size, gradleModuleModel.taskNames.size + extraOldSyncTasks.size)

    assertEquals(oldGradleModuleModel.buildFilePath, gradleModuleModel.buildFilePath)
    // Old plugin list is not populated
    assertSize(0, oldGradleModuleModel.gradlePlugins)
    assertContainsElements(gradleModuleModel.gradlePlugins,
                           if (isKotlinLib()) "org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper"
                           else "org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper")
  }

  private fun Module.verifyKotlinConfiguration(oldFacet: KotlinFacet?) {
    // Project module does not have a KotlinFacet
    if (isProject()) return

    val facet: KotlinFacet? = KotlinFacet.get(this)
    if (facet == null) {
      assertNull(oldFacet)
      return
    }
    assertNotNull(oldFacet)

    val configuration: KotlinFacetConfiguration = facet.configuration
    val settings: KotlinFacetSettings = configuration.settings

    val oldConfiguration: KotlinFacetConfiguration = oldFacet!!.configuration
    val oldSettings: KotlinFacetSettings = oldConfiguration.settings

    assertEquals(oldSettings.useProjectSettings, settings.useProjectSettings)
    assertEquals(oldSettings.languageLevel, settings.languageLevel)
    assertEquals(oldSettings.apiLevel, settings.apiLevel)
    assertEquals(oldSettings.platform, settings.platform)
    assertEquals(oldSettings.coroutineSupport, settings.coroutineSupport)
    assertEquals(oldSettings.implementedModuleNames, settings.implementedModuleNames)
    assertEquals(oldSettings.productionOutputPath, settings.productionOutputPath)
    assertEquals(oldSettings.testOutputPath, settings.testOutputPath)
    assertEquals(oldSettings.kind, settings.kind)
    assertEquals(oldSettings.sourceSetNames, settings.sourceSetNames)

    val compilerArguments: K2JVMCompilerArguments = settings.compilerArguments as K2JVMCompilerArguments
    val oldCompilerArguments: K2JVMCompilerArguments = oldSettings.compilerArguments as K2JVMCompilerArguments

    verifyKotlinCompilerArguments(compilerArguments, oldCompilerArguments)

    val compilerSettings = settings.compilerSettings
    val oldCompilerSettings = oldSettings.compilerSettings

    verifyKotlinCompilerSettings(compilerSettings, oldCompilerSettings)
  }

  private fun verifyKotlinCompilerSettings(compilerSettings: CompilerSettings?, oldCompilerSettings: CompilerSettings?) {
    if (compilerSettings == null) {
      assertNull(oldCompilerSettings)
      return
    }
    assertNotNull(oldCompilerSettings)

    assertEquals(oldCompilerSettings!!.additionalArguments, compilerSettings.additionalArguments)
    assertEquals(oldCompilerSettings.scriptTemplates, compilerSettings.scriptTemplates)
    assertEquals(oldCompilerSettings.scriptTemplatesClasspath, compilerSettings.scriptTemplatesClasspath)
    assertEquals(oldCompilerSettings.copyJsLibraryFiles, compilerSettings.copyJsLibraryFiles)
    assertEquals(oldCompilerSettings.outputDirectoryForJsLibraryFiles, compilerSettings.outputDirectoryForJsLibraryFiles)
  }

  private fun <T> assertArrayEquals(expected: Array<T>?, actual: Array<T>?) {
    if (expected == null) {
      assertNull(actual)
      return
    }
    assertNotNull(actual)
    assertThat(actual).asList().containsExactlyElementsIn(expected)
  }

  private fun verifyKotlinCompilerArguments(compilerArguments: K2JVMCompilerArguments, oldCompilerArguments: K2JVMCompilerArguments) {
    assertEquals(oldCompilerArguments.autoAdvanceLanguageVersion, compilerArguments.autoAdvanceLanguageVersion)
    assertEquals(oldCompilerArguments.autoAdvanceApiVersion, compilerArguments.autoAdvanceApiVersion)
    assertEquals(oldCompilerArguments.languageVersion, compilerArguments.languageVersion)
    assertEquals(oldCompilerArguments.kotlinHome, compilerArguments.kotlinHome)
    assertEquals(oldCompilerArguments.progressiveMode, compilerArguments.progressiveMode)
    assertArrayEquals(oldCompilerArguments.pluginOptions, compilerArguments.pluginOptions)
    assertEquals(oldCompilerArguments.noInline, compilerArguments.noInline)
    assertEquals(oldCompilerArguments.skipRuntimeVersionCheck, compilerArguments.skipRuntimeVersionCheck)
    assertEquals(oldCompilerArguments.allowKotlinPackage, compilerArguments.allowKotlinPackage)
    assertEquals(oldCompilerArguments.reportOutputFiles, compilerArguments.reportOutputFiles)
    assertArrayEquals(oldCompilerArguments.pluginClasspaths, compilerArguments.pluginClasspaths)
    assertEquals(oldCompilerArguments.multiPlatform, compilerArguments.multiPlatform)
    assertEquals(oldCompilerArguments.noCheckActual, compilerArguments.noCheckActual)
    assertEquals(oldCompilerArguments.intellijPluginRoot, compilerArguments.intellijPluginRoot)
    assertEquals(oldCompilerArguments.coroutinesState, compilerArguments.coroutinesState)
    assertEquals(oldCompilerArguments.newInference, compilerArguments.newInference)
    assertEquals(oldCompilerArguments.legacySmartCastAfterTry, compilerArguments.legacySmartCastAfterTry)
    assertEquals(oldCompilerArguments.effectSystem, compilerArguments.effectSystem)
    assertEquals(oldCompilerArguments.readDeserializedContracts, compilerArguments.readDeserializedContracts)
    assertArrayEquals(oldCompilerArguments.experimental, compilerArguments.experimental)
    assertArrayEquals(oldCompilerArguments.useExperimental, compilerArguments.useExperimental)
    assertEquals(oldCompilerArguments.properIeee754Comparisons, compilerArguments.properIeee754Comparisons)
    assertEquals(oldCompilerArguments.reportPerf, compilerArguments.reportPerf)
    assertEquals(oldCompilerArguments.dumpPerf, compilerArguments.dumpPerf)
    assertEquals(oldCompilerArguments.metadataVersion, compilerArguments.metadataVersion)
    assertArrayEquals(oldCompilerArguments.commonSources, compilerArguments.commonSources)
    assertEquals(oldCompilerArguments.destination, compilerArguments.destination)
    assertEquals(oldCompilerArguments.classpath, compilerArguments.classpath)
    assertEquals(oldCompilerArguments.includeRuntime, compilerArguments.includeRuntime)
    assertEquals(oldCompilerArguments.jdkHome, compilerArguments.jdkHome)
    assertEquals(oldCompilerArguments.noJdk, compilerArguments.noJdk)
    assertEquals(oldCompilerArguments.noStdlib, compilerArguments.noStdlib)
    assertEquals(oldCompilerArguments.noReflect, compilerArguments.noReflect)
    assertEquals(oldCompilerArguments.script, compilerArguments.script)
    assertArrayEquals(oldCompilerArguments.scriptTemplates, compilerArguments.scriptTemplates)
    assertEquals(oldCompilerArguments.moduleName, compilerArguments.moduleName)
    assertEquals(oldCompilerArguments.jvmTarget, compilerArguments.jvmTarget)
    assertEquals(oldCompilerArguments.javaParameters, compilerArguments.javaParameters)
    assertEquals(oldCompilerArguments.useIR, compilerArguments.useIR)
    assertEquals(oldCompilerArguments.javaModulePath, compilerArguments.javaModulePath)
    assertArrayEquals(oldCompilerArguments.additionalJavaModules, compilerArguments.additionalJavaModules)
    assertEquals(oldCompilerArguments.noCallAssertions, compilerArguments.noCallAssertions)
    assertEquals(oldCompilerArguments.noReceiverAssertions, compilerArguments.noReceiverAssertions)
    assertEquals(oldCompilerArguments.noParamAssertions, compilerArguments.noParamAssertions)
    assertEquals(oldCompilerArguments.strictJavaNullabilityAssertions, compilerArguments.strictJavaNullabilityAssertions)
    assertEquals(oldCompilerArguments.noOptimize, compilerArguments.noOptimize)
    assertEquals(oldCompilerArguments.constructorCallNormalizationMode, compilerArguments.constructorCallNormalizationMode)
    assertEquals(oldCompilerArguments.assertionsMode, compilerArguments.assertionsMode)
    assertEquals(oldCompilerArguments.buildFile, compilerArguments.buildFile)
    assertEquals(oldCompilerArguments.inheritMultifileParts, compilerArguments.inheritMultifileParts)
    assertEquals(oldCompilerArguments.useTypeTable, compilerArguments.useTypeTable)
    assertEquals(oldCompilerArguments.skipRuntimeVersionCheck, compilerArguments.skipRuntimeVersionCheck)
    assertEquals(oldCompilerArguments.useOldClassFilesReading, compilerArguments.useOldClassFilesReading)
    assertEquals(oldCompilerArguments.declarationsOutputPath, compilerArguments.declarationsOutputPath)
    assertEquals(oldCompilerArguments.singleModule, compilerArguments.singleModule)
    assertEquals(oldCompilerArguments.addCompilerBuiltIns, compilerArguments.addCompilerBuiltIns)
    assertEquals(oldCompilerArguments.loadBuiltInsFromDependencies, compilerArguments.loadBuiltInsFromDependencies)
    assertArrayEquals(oldCompilerArguments.scriptResolverEnvironment, compilerArguments.scriptResolverEnvironment)
    assertEquals(oldCompilerArguments.useJavac, compilerArguments.useJavac)
    assertEquals(oldCompilerArguments.compileJava, compilerArguments.compileJava)
    assertArrayEquals(oldCompilerArguments.javacArguments, compilerArguments.javacArguments)
    assertArrayEquals(oldCompilerArguments.jsr305, compilerArguments.jsr305)
    assertEquals(oldCompilerArguments.supportCompatqualCheckerFrameworkAnnotations,
                 compilerArguments.supportCompatqualCheckerFrameworkAnnotations)
    assertEquals(oldCompilerArguments.noExceptionOnExplicitEqualsForBoxedNull, compilerArguments.noExceptionOnExplicitEqualsForBoxedNull)
    assertEquals(oldCompilerArguments.jvmDefault, compilerArguments.jvmDefault)
    assertEquals(oldCompilerArguments.disableDefaultScriptingPlugin, compilerArguments.disableDefaultScriptingPlugin)
    assertEquals(oldCompilerArguments.disableStandardScript, compilerArguments.disableStandardScript)
    assertArrayEquals(oldCompilerArguments.friendPaths, compilerArguments.friendPaths)
  }

  private fun Module.isKotlinLib(): Boolean = name == "contentLib"

  private fun Module.isProject(): Boolean = name == project.name

  private fun Module.verifyFacetPresence() {
    val facets = FacetManager.getInstance(this).allFacets
    // The main project module should only have one Java facet
    if (isProject()) {
      assertSize(1, facets)
      val javaFacet = facets[0]
      assertInstanceOf(javaFacet, JavaFacet::class.java)
      return
    }

    // All of the other modules should have three Facets.
    // A GradleFacet an AndroidFacet and a KotlinFacet
    assertSize(3, facets)
    containsAllInstancesOf(facets.toList(), GradleFacet::class.java,
                           if (isKotlinLib()) JavaFacet::class.java else AndroidFacet::class.java,
                           KotlinFacet::class.java)
  }

  private fun containsAllInstancesOf(list: List<Any>, vararg types: Class<*>) {
    types.forEach { type ->
      assertEquals("Looking for type ${type.simpleName} in $list", 1, list.count { type.isInstance(it) })
    }
  }
}
