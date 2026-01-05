/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.runsGradleProjectsystem

import com.android.tools.idea.gradle.dsl.model.GradleModelSource
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleCachedVersionCatalogTest {
  // we need to have project structure like modules that's the reason of having heavy Gradle rule
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  private val project get() = projectRule.project

  @Test
  fun testReturnSameInstance() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val appModule = project.findAppModule()

    val catalogsModel1 = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)

    val catalogsModel2 = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)

    assertThat(catalogsModel1).isSameAs(catalogsModel2)
  }

  @Test
  fun testCatalogRemoved() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val appModule = project.findAppModule()
    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val catalogsModelBefore = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore.catalogNames()).containsExactly("libs", "libsTest") // check for catalog updates
    val settings = root.findFileByRelativePath("settings.gradle")!!
    val settingsContent = settings.readText()

    val start = settingsContent.indexOf("libsTest")
    val finish = settingsContent.indexOf("}", start)

    ApplicationManager.getApplication().runWriteAction {
      settings.writeTextAndCommit(settingsContent.removeRange(start, finish + 1))
    }

    val catalogsModelAfter = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore).isNotSameAs(catalogsModelAfter)
    assertThat(catalogsModelAfter.catalogNames()).containsExactly("libs")
  }

  @Test
  fun testCatalogFileRemoved() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val appModule = project.findAppModule()
    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val catalogsModelBefore = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore.catalogNames()).containsExactly("libs", "libsTest") // check for catalog updates
    val libsTest = root.findFileByRelativePath("gradle/libsTest.versions.toml")!!
    ApplicationManager.getApplication().runWriteAction {
      libsTest.delete(this)
    }

    val catalogsModelAfter = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore).isNotSameAs(catalogsModelAfter)
    assertThat(catalogsModelAfter.catalogNames()).containsExactly("libs")
  }

  @Test
  fun testCatalogUpdated() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val appModule = project.findAppModule()
    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val catalogsModelBefore = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore.catalogNames()).containsExactly("libs", "libsTest") // check for catalog updates
    val catalog = root.findFileByRelativePath("gradle/libs.versions.toml")!!
    val catalogContent = catalog.readText()

    ApplicationManager.getApplication().runWriteAction {
      catalog.writeTextAndCommit(catalogContent.replace("[libraries]", "[libraries]\njunit=\"junit:junit:4.0.0\"\n"))
    }

    val catalogsModelAfter = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModelBefore).isNotSameAs(catalogsModelAfter)
    val versionCatalog = catalogsModelAfter.getVersionCatalogModel("libs")
    assertThat(versionCatalog).isNotNull()
    assertThat(versionCatalog!!.libraryDeclarations().getAll().keys).contains("junit")
  }

  @Test
  fun testCachedVersionCatalogSettingsMissed() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val appModule = project.findAppModule()

    val root = StandardFileSystems.local().findFileByPath(project.basePath!!)!!
    val settings = root.findFileByRelativePath("settings.gradle")!!
    ApplicationManager.getApplication().runWriteAction {
      settings.delete(this)
    }
    val catalogsModel = GradleModelSource.getInstance().getCachedVersionCatalogsModel(appModule)
    assertThat(catalogsModel.catalogNames()).containsExactly("libs")
  }

  private fun VirtualFile.writeTextAndCommit(text: String) {
    findDocument()?.reloadFromDisk()
    writeText(text)
    findDocument()?.commitToPsi(projectRule.project)
  }

}
