/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.kotlin.android.sync.ng

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.testFramework.PlatformTestCase
import org.jetbrains.kotlin.android.sync.ng.utils.TestGradleModuleModels
import org.jetbrains.kotlin.android.sync.ng.utils.allOpen
import org.jetbrains.kotlin.android.sync.ng.utils.androidKotlinModel
import org.jetbrains.kotlin.android.sync.ng.utils.createModuleFile
import org.jetbrains.kotlin.android.sync.ng.utils.kotlinAndroidExtension
import org.jetbrains.kotlin.android.sync.ng.utils.kotlinModel
import org.jetbrains.kotlin.android.sync.ng.utils.noArg
import org.jetbrains.kotlin.android.sync.ng.utils.samWithReceiver
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.model.AllOpen
import org.jetbrains.kotlin.gradle.model.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.model.KotlinProject
import org.jetbrains.kotlin.gradle.model.NoArg
import org.jetbrains.kotlin.gradle.model.SamWithReceiver
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * This test verifies that KotlinSyncModels correctly configures the facet to be as we expect. This test does not cover other IDEA
 * required configuration.
 */
class KotlinSyncModelsTest : PlatformTestCase() {
  @Before
  public override fun setUp() {
    super.setUp()
  }

  @After
  public override fun tearDown() {
    super.tearDown()
  }

  private fun runConfigurationOfKotlinFacet(vararg models: Pair<Class<*>, *>, isAndroid: Boolean = false) {
    val testGradleModuleModels = TestGradleModuleModels(myModule.name)
    models.forEach {
      testGradleModuleModels.addModel(it.first, it.second)
    }
    val ideModifiableModelsProvider = IdeModifiableModelsProviderImpl(myModule.project)

    if (isAndroid) {
      KotlinSyncModels.KotlinAndroidSyncModels.getExtensions()[0]!!.applyModelsToModule(testGradleModuleModels, myModule,
                                                                                        ideModifiableModelsProvider)
    }
    else {
      KotlinSyncModels.KotlinJavaSyncModels.getExtensions()[0]!!.applyModelsToModule(testGradleModuleModels, myModule,
                                                                                     ideModifiableModelsProvider)
    }
    ApplicationManager.getApplication().runWriteAction {
      ideModifiableModelsProvider.commit()
    }
  }

  @Test
  fun `test Ensure Kotlin facet is configured correctly for Kotlin only modules`() {
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel(myModule.name, moduleDir) { })

    // Check the all the facet configuration is as expected.
    // Note we only check a subset of properties that we expect the KotlinSyncModel to setup.
    val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
    assertNotNull(kotlinFacet)
    val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
    val kotlinSettings = kotlinConfig.settings
    assertFalse(kotlinSettings.useProjectSettings)
    assertNotNull(kotlinSettings)
    val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
    assertEquals("1.2", compilerArguments.apiVersion)
    assertEquals("1.2", compilerArguments.languageVersion)
    assertEquals("warn", compilerArguments.coroutinesState)
    assertEquals("1.8", compilerArguments.jvmTarget)
    assertEquals("test Ensure Kotlin facet is configured correctly for Kotlin only modules", compilerArguments.moduleName)
    assertEquals(createModuleFile(moduleDir, "build", "classes", "kotlin", "main").absolutePath, compilerArguments.destination)
    assertFalse(compilerArguments.autoAdvanceApiVersion)
    assertFalse(compilerArguments.autoAdvanceLanguageVersion)
    assertEquals(0, compilerArguments.pluginClasspaths!!.size)
    assertEquals(0, compilerArguments.pluginOptions!!.size)
  }

  @Test
  fun `test Ensure Kotlin facet is configured correctly for Android Kotlin modules`() {
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    val kotlinModel = androidKotlinModel(myModule.name, moduleDir) { }
    val androidExtensionsModel = kotlinAndroidExtension(myModule.name) { }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel, KotlinAndroidExtension::class.java to androidExtensionsModel,
                                  isAndroid = true)

    val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
    assertNotNull(kotlinFacet)
    val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
    val kotlinSettings = kotlinConfig.settings
    assertFalse(kotlinSettings.useProjectSettings)
    assertNotNull(kotlinSettings)
    val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
    assertEquals("1.2", compilerArguments.apiVersion)
    assertEquals("1.2", compilerArguments.languageVersion)
    assertEquals("warn", compilerArguments.coroutinesState)
    assertEquals("1.6", compilerArguments.jvmTarget)
    assertEquals("test Ensure Kotlin facet is configured correctly for Android Kotlin modules", compilerArguments.moduleName)
    assertEquals(createModuleFile(moduleDir, "build", "tmp", "kotlin-classes", "debug").absolutePath, compilerArguments.destination)
    assertFalse(compilerArguments.autoAdvanceApiVersion)
    assertFalse(compilerArguments.autoAdvanceLanguageVersion)
    assertFalse(compilerArguments.noJdk) // The Kotlin plugin seems to fail to parse this argument, no idea why.
    assertTrue(compilerArguments.noReflect)
    assertTrue(compilerArguments.noStdlib)
    assertEquals("/some/really/cool/jar.jar:/some/other/really/cool/jar.jar", compilerArguments.classpath)
    assertContainsElements(compilerArguments.pluginOptions!!.toList(),
                           "plugin:org.jetbrains.kotlin.android:experimental=false",
                           "plugin:org.jetbrains.kotlin.android:enabled=true",
                           "plugin:org.jetbrains.kotlin.android:defaultCacheImplem" +
                           "entation=hashMap")
    assertSize(3, compilerArguments.pluginOptions!!.toList())
    assertSize(1, compilerArguments.pluginClasspaths!!)
    assertContainsElements(compilerArguments.pluginClasspaths!!.toList(), "/some/path/to/extensions/plugin")
  }

  @Test
  fun `test Ensure Kotlin Facet is configured when converting from a Kotlin Android module to a plain Kotlin one`() {
    // First setup as Kotlin Android
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    val kotlinModel = androidKotlinModel(myModule.name, moduleDir) { }
    val androidExtensionsModel = kotlinAndroidExtension(myModule.name) { }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel, KotlinAndroidExtension::class.java to androidExtensionsModel,
                                  isAndroid = true)

    run {
      val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
      assertNotNull(kotlinFacet)
      val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
      val kotlinSettings = kotlinConfig.settings
      assertFalse(kotlinSettings.useProjectSettings)
      assertNotNull(kotlinSettings)
      val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
      assertEquals("1.2", compilerArguments.apiVersion)
      assertEquals("1.2", compilerArguments.languageVersion)
      assertEquals("warn", compilerArguments.coroutinesState)
      assertEquals("1.6", compilerArguments.jvmTarget)
      assertEquals("test Ensure Kotlin Facet is configured when converting from a Kotlin Android module to a plain Kotlin one", compilerArguments.moduleName)
      assertEquals(createModuleFile(moduleDir, "build", "tmp", "kotlin-classes", "debug").absolutePath, compilerArguments.destination)
      assertFalse(compilerArguments.autoAdvanceApiVersion)
      assertFalse(compilerArguments.autoAdvanceLanguageVersion)
      assertEquals("/some/really/cool/jar.jar:/some/other/really/cool/jar.jar", compilerArguments.classpath)
      assertContainsElements(compilerArguments.pluginOptions!!.toList(),
                             "plugin:org.jetbrains.kotlin.android:experimental=false",
                             "plugin:org.jetbrains.kotlin.android:enabled=true",
                             "plugin:org.jetbrains.kotlin.android:defaultCacheImplem" +
                             "entation=hashMap")
      assertSize(3, compilerArguments.pluginOptions!!.toList())
      assertSize(1, compilerArguments.pluginClasspaths!!)
      assertContainsElements(compilerArguments.pluginClasspaths!!.toList(), "/some/path/to/extensions/plugin")
    }

    // Then re-run setup with a plain Kotlin model
    val plainKotlinModel = kotlinModel(myModule.name, moduleDir) { }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to plainKotlinModel)

    // Check they everything is as it should be, we take extra care to ensure that the plugin options have been removed.
    run {
      val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
      assertNotNull(kotlinFacet)
      val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
      val kotlinSettings = kotlinConfig.settings
      assertFalse(kotlinSettings.useProjectSettings)
      assertNotNull(kotlinSettings)
      val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
      assertEquals("1.2", compilerArguments.apiVersion)
      assertEquals("1.2", compilerArguments.languageVersion)
      assertEquals("warn", compilerArguments.coroutinesState)
      assertEquals("1.8", compilerArguments.jvmTarget)
      assertEquals("test Ensure Kotlin Facet is configured when converting from a Kotlin Android module to a plain Kotlin one", compilerArguments.moduleName)
      assertEquals(createModuleFile(moduleDir, "build", "classes", "kotlin", "main").absolutePath, compilerArguments.destination)
      assertFalse(compilerArguments.autoAdvanceApiVersion)
      assertFalse(compilerArguments.autoAdvanceLanguageVersion)
      assertFalse(compilerArguments.noJdk) // The Kotlin plugin seems to fail to parse this argument, no idea why.
      assertTrue(compilerArguments.noReflect)
      assertTrue(compilerArguments.noStdlib)
      assertEquals(0, compilerArguments.pluginClasspaths!!.size)
      assertEquals(0, compilerArguments.pluginOptions!!.size)
    }
  }

  @Test
  fun `test Ensure All Open Kotlin compiler plugin options are correctly populated in the facet`() {
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    val kotlinModel = kotlinModel(myModule.name, moduleDir) {
      for (name in listOf("main", "test")) {
        sourceSet(name) {
          compilerArgs {
            val newArgs = listOf("-classpath", "/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar",
                                 "-Xplugin=/some/path/kotlin-allopen-1.2.71.jar,/some/path/kotlin-scripting-compiler-embeddable-1.2.71.jar",
                                 "-P", "plugin:org.jetbrains.kotlin.allopen:annotation=com.some.test.package")
            currentArguments.addAll(newArgs)
            defaultArguments.addAll(newArgs)
          }
        }
      }
    }
    val allOpenModel = allOpen(myModule.name) {
      annotations.add("com.some.test.package")
      presets.add("jpa")
    }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel, AllOpen::class.java to allOpenModel)

    val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
    assertNotNull(kotlinFacet)
    val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
    val kotlinSettings = kotlinConfig.settings
    assertFalse(kotlinSettings.useProjectSettings)
    assertNotNull(kotlinSettings)
    val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
    assertEquals("1.2", compilerArguments.apiVersion)
    assertEquals("1.2", compilerArguments.languageVersion)
    assertEquals("warn", compilerArguments.coroutinesState)
    assertEquals("1.8", compilerArguments.jvmTarget)
    assertEquals("test Ensure All Open Kotlin compiler plugin options are correctly populated in the facet", compilerArguments.moduleName)
    assertEquals(createModuleFile(moduleDir, "build", "classes", "kotlin", "main").absolutePath, compilerArguments.destination)
    assertFalse(compilerArguments.autoAdvanceApiVersion)
    assertFalse(compilerArguments.autoAdvanceLanguageVersion)
    assertEquals("/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar", compilerArguments.classpath)
    assertContainsElements(compilerArguments.pluginOptions!!.toList(),
                           "plugin:org.jetbrains.kotlin.allopen:annotation=com.some.test.package",
                           "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Entity",
                           "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.Embeddable",
                           "plugin:org.jetbrains.kotlin.allopen:annotation=javax.persistence.MappedSuperclass")
    assertSize(4, compilerArguments.pluginOptions!!.toList())
    assertSize(1, compilerArguments.pluginClasspaths!!)
    assertTrue("Plugin classpath contains allopen plugin", compilerArguments.pluginClasspaths!![0].endsWith("allopen-compiler-plugin.jar"))
  }

  @Test
  fun `test Ensure No Arg Kotlin compiler plugin options are correctly populated in the facet`() {
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    val kotlinModel = kotlinModel(myModule.name, moduleDir) {
      for (name in listOf("main", "test")) {
        sourceSet(name) {
          compilerArgs {
            val newArgs = listOf("-classpath", "/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar",
                                 "-Xplugin=/some/path/kotlin-noarg-1.2.71.jar,/some/path/kotlin-scripting-compiler-embeddable-1.2.71.jar",
                                 "-P",
                                 "plugin:org.jetbrains.kotlin.noarg:com.some.test.package," +
                                 "plugin:org.jetbrains.kotlin.noarg:invokeInitializers=true")
            currentArguments.addAll(newArgs)
            defaultArguments.addAll(newArgs)
          }
        }
      }
    }
    val noArgModel = noArg(myModule.name) {
      annotations.add("com.some.test.package")
      isInvokeInitializers = true
    }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel, NoArg::class.java to noArgModel)

    val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
    assertNotNull(kotlinFacet)
    val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
    val kotlinSettings = kotlinConfig.settings
    assertFalse(kotlinSettings.useProjectSettings)
    assertNotNull(kotlinSettings)
    val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
    assertEquals("1.2", compilerArguments.apiVersion)
    assertEquals("1.2", compilerArguments.languageVersion)
    assertEquals("warn", compilerArguments.coroutinesState)
    assertEquals("1.8", compilerArguments.jvmTarget)
    assertEquals("test Ensure No Arg Kotlin compiler plugin options are correctly populated in the facet", compilerArguments.moduleName)
    assertEquals(createModuleFile(moduleDir, "build", "classes", "kotlin", "main").absolutePath, compilerArguments.destination)
    assertFalse(compilerArguments.autoAdvanceApiVersion)
    assertFalse(compilerArguments.autoAdvanceLanguageVersion)
    assertEquals("/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar", compilerArguments.classpath)
    assertContainsElements(compilerArguments.pluginOptions!!.toList(),
                           "plugin:org.jetbrains.kotlin.noarg:annotation=com.some.test.package",
                           "plugin:org.jetbrains.kotlin.noarg:invokeInitializers=true")
    assertSize(2, compilerArguments.pluginOptions!!.toList())
    assertSize(1, compilerArguments.pluginClasspaths!!)
    assertTrue("Plugin classpath contains noarg plugin", compilerArguments.pluginClasspaths!![0].endsWith("noarg-compiler-plugin.jar"))
  }

  @Test
  fun `test Ensure Sam with Receiver Kotlin compiler plugin options are correctly populated in the facet`() {
    val moduleDir = File(myModule.moduleFile!!.parent.path)
    val kotlinModel = kotlinModel(myModule.name, moduleDir) {
      for (name in listOf("main", "test")) {
        sourceSet(name) {
          compilerArgs {
            val newArgs = listOf("-classpath", "/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar",
                                 "-Xplugin=/some/path/kotlin-sam-with-receiver-1.2.71.jar,/some/path/kotlin-scripting-compiler-embeddable-1.2.71.jar",
                                 "-P",
                                 "plugin:org.jetbrains.kotlin.samWithReceiver:annotation=com.some.test.package")
            currentArguments.addAll(newArgs)
            defaultArguments.addAll(newArgs)
          }
        }
      }
    }
    val samWithReceiver = samWithReceiver(myModule.name) {
      annotations.add("com.some.test.package")
    }
    runConfigurationOfKotlinFacet(KotlinProject::class.java to kotlinModel, SamWithReceiver::class.java to samWithReceiver)

    val kotlinFacet: KotlinFacet = KotlinFacet.get(myModule)!!
    assertNotNull(kotlinFacet)
    val kotlinConfig: KotlinFacetConfiguration = kotlinFacet.configuration
    val kotlinSettings = kotlinConfig.settings
    assertFalse(kotlinSettings.useProjectSettings)
    assertNotNull(kotlinSettings)
    val compilerArguments = kotlinSettings.compilerArguments as K2JVMCompilerArguments
    assertEquals("1.2", compilerArguments.apiVersion)
    assertEquals("1.2", compilerArguments.languageVersion)
    assertEquals("warn", compilerArguments.coroutinesState)
    assertEquals("1.8", compilerArguments.jvmTarget)
    assertEquals("test Ensure Sam with Receiver Kotlin compiler plugin options are correctly populated in the facet",
                 compilerArguments.moduleName)
    assertEquals(createModuleFile(moduleDir, "build", "classes", "kotlin", "main").absolutePath, compilerArguments.destination)
    assertFalse(compilerArguments.autoAdvanceApiVersion)
    assertFalse(compilerArguments.autoAdvanceLanguageVersion)
    assertEquals("/some/path/kotlin-stdlib-jdk8-1.2.71:/some/path/annotations-13.0.jar", compilerArguments.classpath)
    assertContainsElements(compilerArguments.pluginOptions!!.toList(),
                           "plugin:org.jetbrains.kotlin.samWithReceiver:annotation=com.some.test.package")
    assertSize(1, compilerArguments.pluginOptions!!.toList())
    assertSize(1, compilerArguments.pluginClasspaths!!)
    assertTrue("Plugin classpath contains Sam with Receiver plugin",
               compilerArguments.pluginClasspaths!![0].endsWith("sam-with-receiver-compiler-plugin.jar"))
  }
}