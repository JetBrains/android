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
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FacetFinderTest {

  @get:Rule
  val projectRule = AndroidProjectRule
    .withAndroidModels(
      JavaModuleModelBuilder.rootModuleBuilder,
      AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder(
        //projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY }, // TODO(b/262868739): Avoid logging errors
        applicationIdFor = { "todo_library_project_should_not_have_application_id" }, // TODO(b/262868739): Remove once project type library is set
        mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
        testApplicationId = { "libTestApplicationId" }
      )),
      AndroidModuleModelBuilder(
        ":app",
        "debug",
        AndroidProjectBuilder(
          mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() }
        ).withAndroidModuleDependencyList {
          mutableListOf(AndroidModuleDependency (":lib", "debug"))
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
      module.isAndroidTestModule() -> sourceProviderManager.currentAndroidTestSourceProviders
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
    val result = FacetFinder.findFacetForProcess(project, "shouldNotExist")
    assertNull(result)
  }

  @Test
  fun testPackageName() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId")
    assertEquals(appFacet.mainModule.androidFacet, result)
  }

  @Test
  fun testLocalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId:localfromapp")
    assertEquals(appFacet.mainModule.androidFacet, result)
  }

  @Test
  fun testLocalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId:localfromlib")
    assertEquals(appFacet.mainModule.androidFacet, result)
  }

  @Test
  fun testGlobalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromapp")
    assertEquals(appFacet.mainModule.androidFacet, result)
  }

  @Test
  fun testGlobalProcessFromAppAndroidTestModule() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromappandroidtest")
    assertEquals(null, result) // TODO(b/262868739): Should be appFacet.androidTestModule
  }

  @Test
  fun testGlobalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromlib")
    assertEquals(libFacet.mainModule.androidFacet, result) // TODO(b/262868739): Currently brittle to module iteration order - should be appFacet.mainModule
  }

  @Test
  fun testGlobalProcessFromLibModuleAndroidTest() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromlibandroidtest")
    assertEquals(null, result) // TODO(b/262868739): Should be libFacet.androidTestModule
  }

  @Test
  fun testGlobalProcessFromLibModuleAndroidTestDebug() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromlibdebugandroidtest")
    assertEquals(null, result) // TODO(b/262868739): Should be libFacet.androidTestModule
  }

  @Test
  fun testTestPackageName() {
    val result = FacetFinder.findFacetForProcess(project, "libTestApplicationId")
    assertEquals(libFacet.androidTestModule!!.androidFacet, result)
  }
}