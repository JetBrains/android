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
package com.android.tools.idea.execution.common.debug.utils

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isMainModule
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FacetFinderTest {

  private class FakeClientData(private val applicationId: String?, private val processName: String?) :
    ClientData(Mockito.mock(Client::class.java).also { whenever(it.device).thenReturn(mock<IDevice>()) }, -1) {
    override fun getPackageName(): String? = applicationId ?: processName?.removeSuffix(":") // See behaviour in overridden method
    override fun getClientDescription(): String? = processName
  }

  @get:Rule
  val projectRule = AndroidProjectRule
    .withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder(
        projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
        applicationIdFor = { "" },
        mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
        testApplicationId = { "libTestApplicationId" }
      )),
      AndroidModuleModelBuilder(
        ":app",
        "debug",
        AndroidProjectBuilder(
          mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() }
        ).withAndroidModuleDependencyList {
          listOf(AndroidModuleDependency(":lib", "debug"))
        }),
    )

  val project
    get() = projectRule.project

  private lateinit var appFacet: AndroidFacet
  private lateinit var libFacet: AndroidFacet

  private val appManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="applicationId">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivity">
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            </activity>
            <activity android:name=".MainActivity2"
                      android:process=":localFromApp"
                      android:exported="false">
            </activity>
            <activity android:name=".MainActivity3"
                      android:process="globalFromApp"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private val appDebugManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivityDebug"
                      android:process="globalFromAppDebug"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private val appAndroidTestManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivity7"
                      android:process=":localFromAppAndroidTest"
                      android:exported="false">
            </activity>
            <activity android:name=".MainActivity8"
                      android:process="globalFromAppAndroidTest"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private val libManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="applicationId">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivity4"
                      android:process=":localFromLib"
                      android:exported="false">
            </activity>
            <activity android:name=".MainActivity5"
                      android:process="globalFromLib"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private val libAndroidTestManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivity9"
                      android:process=":localFromLibAndroidTest"
                      android:exported="false">
            </activity>
            <activity android:name=".MainActivity10"
                      android:process="globalFromLibAndroidTest"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private val libDebugAndroidTestManifest = """
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android">
        <application android:allowBackup="true"
            android:label="@string/app_name"
            android:supportsRtl="true">
            <activity android:name=".MainActivity12"
                      android:process="globalFromLibDebugAndroidTest"
                      android:exported="false">
            </activity>
        </application>
    </manifest>
  """.trimIndent()

  private fun writeManifestFileContents(module: Module?, manifest: String, sourceSetName: String? = null) {
    val facet = AndroidFacet.getInstance(module!!)!!
    val sourceProviderManager = SourceProviderManager.getInstance(facet)
    val sourceProviders = when {
      module.isMainModule() -> sourceProviderManager.currentSourceProviders
      module.isAndroidTestModule() -> sourceProviderManager.currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST] ?:
                                      throw IllegalArgumentException("expected module to be main or androidTest")
      else -> throw IllegalArgumentException("expected module to be main or androidTest")
    }
    val sourceProvider =
      if (sourceSetName == null) {
        sourceProviders.first()
      }
      else sourceProviders.firstOrNull { it.name == sourceSetName}
           ?: throw IllegalStateException("Unknown source provider $sourceSetName, known names: [${sourceProviders.joinToString(", ") { it.name } }}]")
    runWriteActionAndWait {
      val manifestFile: VirtualFile = sourceProvider.manifestFiles.singleOrNull() ?: let {
        val manifestUrl: String = sourceProvider.manifestFileUrls.first()
        val manifestDirectory: VirtualFile = sourceProvider.manifestDirectories.firstOrNull() ?: let {
          VfsUtil.createDirectories(VfsUtilCore.urlToPath(sourceProvider.manifestDirectoryUrls.first()))!!
        }
        manifestDirectory.createChildData(this, VfsUtil.extractFileName(manifestUrl)!!)
      }
      manifestFile.setBinaryContent(manifest.toByteArray())
    }
  }

  @Before
  fun setUp() {
    appFacet = project.getAndroidFacets().find { it.module.name.contains("app") }!!
    libFacet = project.getAndroidFacets().find { it.module.name.contains("lib") }!!

    writeManifestFileContents(appFacet.mainModule, appManifest)
    writeManifestFileContents(appFacet.mainModule, appDebugManifest, sourceSetName = "debug")
    writeManifestFileContents(appFacet.androidTestModule, appAndroidTestManifest)
    writeManifestFileContents(libFacet.mainModule, libManifest)
    writeManifestFileContents(libFacet.androidTestModule, libAndroidTestManifest)
    writeManifestFileContents(libFacet.androidTestModule, libDebugAndroidTestManifest, sourceSetName = "androidTestDebug")
  }

  @Test
  fun testNotFound() {
    val failure = assertFailsWith<ExecutionException> {
      FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = "applicationIdShouldNotExist", processName = "processNameShouldNotExist"))
    }
    assertThat(failure).hasMessage("Unable to find project context to attach debugger for process processNameShouldNotExist")
  }

  @Test
  fun testNotFoundGlobalProcessOnOlderDevice() {
    val failure = assertFailsWith<ExecutionException> {
      FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = null, processName = "processNameShouldNotExist"))
    }
    assertThat(failure).hasMessage("Unable to find project context to attach debugger for process processNameShouldNotExist")
  }

  @Test
  fun testPackageName() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = "applicationId", processName = "overridden"))
    assertEquals(appFacet.mainModule.androidFacet, result.facet)
    assertEquals("applicationId", result.applicationId)
  }

  @Test
  fun testLocalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = "applicationId", processName = "applicationId:localfromapp"))
    assertEquals(appFacet.mainModule.androidFacet, result.facet)
    assertEquals("applicationId", result.applicationId)
  }

  @Test
  fun testLocalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = "applicationId", processName = "applicationId:localfromlib"))
    assertEquals(appFacet.mainModule.androidFacet, result.facet)
    assertEquals("applicationId", result.applicationId)
  }

  @Test
  fun testGlobalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = null, processName = "globalfromapp"))
    assertEquals(appFacet.mainModule.androidFacet, result.facet)
    assertEquals("applicationId", result.applicationId)
  }

  @Test
  fun testGlobalProcessFromAppAndroidTestModule() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = null, processName = "globalfromappandroidtest"))
    assertEquals(appFacet.androidTestModule!!.androidFacet, result.facet)
    assertEquals("testApplicationId", result.applicationId)
  }

  @Test
  fun testGlobalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = null, processName = "globalfromlib"))
    assertEquals(appFacet.mainModule.androidFacet, result.facet)
    assertEquals("applicationId", result.applicationId)
  }

  @Test
  fun testGlobalProcessFromLibModuleAndroidTest() {
    val result = FacetFinder.findFacetForProcess(project, FakeClientData(applicationId = null, processName = "globalfromlibandroidtest"))
    assertEquals(libFacet.androidTestModule!!.androidFacet, result.facet)
    assertEquals("libTestApplicationId", result.applicationId)
  }

  @Test
  fun testGlobalProcessFromLibModuleAndroidTestDebug() {
    val result = FacetFinder.findFacetForProcess(project,
                                                 FakeClientData(applicationId = null, processName = "globalfromlibdebugandroidtest"))
    assertEquals(libFacet.androidTestModule!!.androidFacet, result.facet)
    assertEquals("libTestApplicationId", result.applicationId)  // Might be imprecise?
  }

  @Test
  fun testTestPackageName() {
    val result = FacetFinder.findFacetForProcess(project,
                                                 FakeClientData(applicationId = "libTestApplicationId", processName = "overridden"))
    assertEquals(libFacet.androidTestModule!!.androidFacet, result.facet)
    assertEquals("libTestApplicationId", result.applicationId)
  }
}