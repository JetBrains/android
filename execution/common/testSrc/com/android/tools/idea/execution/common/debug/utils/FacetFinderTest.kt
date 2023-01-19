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
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VfsUtil
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
      AndroidModuleModelBuilder(
        ":app",
        "debug",
        AndroidProjectBuilder(
          mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() }
        ).withAndroidModuleDependencyList {
          mutableListOf(AndroidModuleDependency (":lib", "debug"))
        }),
      AndroidModuleModelBuilder(":lib", "debug", AndroidProjectBuilder(
        mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() }
      )),
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

  private fun writeManifestFileContents(facet: AndroidFacet, manifest: String) {
    val sourceProviderManager = SourceProviderManager.getInstance(facet)
    runWriteActionAndWait {
      var manifestFile: VirtualFile? = sourceProviderManager.mainManifestFile
      if (manifestFile == null) {
        val manifestUrl: String = sourceProviderManager.mainIdeaSourceProvider.manifestFileUrls.first()
        val manifestDirectory: VirtualFile = sourceProviderManager.mainIdeaSourceProvider.manifestDirectories.first()
        manifestFile = manifestDirectory.createChildData(this, VfsUtil.extractFileName(manifestUrl)!!)
      }
      manifestFile.setBinaryContent(manifest.toByteArray())
    }
  }

  @Before
  fun setUp() {
    appFacet = project.getAndroidFacets().find { it.module.name.contains("app") }!!
    libFacet = project.getAndroidFacets().find { it.module.name.contains("lib") }!!

    writeManifestFileContents(appFacet, appManifest)
    writeManifestFileContents(libFacet, libManifest)
  }

  @Test
  fun testNotFound() {
    val result = FacetFinder.findFacetForProcess(project, "shouldNotExist")
    assertNull(result)
  }

  @Test
  fun testPackageName() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId")
    assertEquals(appFacet, result)
  }

  @Test
  fun testLocalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId:localfromapp")
    assertEquals(appFacet, result)
  }

  @Test
  fun testLocalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, "applicationId:localfromlib")
    assertEquals(appFacet, result)
  }

  @Test
  fun testGlobalProcessFromAppModule() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromapp")
    assertEquals(appFacet, result)
  }

  @Test
  fun testGlobalProcessFromLibModule() {
    val result = FacetFinder.findFacetForProcess(project, "globalfromlib")
    assertEquals(appFacet, result)
  }
}