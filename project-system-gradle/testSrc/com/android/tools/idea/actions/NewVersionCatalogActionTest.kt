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
package com.android.tools.idea.actions

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.writeChild
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.IdeView
import com.intellij.ide.actions.TestDialogBuilder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.lang.RuntimeException


@RunsInEdt
class NewVersionCatalogActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testNewVersionCatalogActionDefault() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { p ->
      assertThat(p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")).isNull()
      val dataContext = createTestDataContext(p, "libs")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val event = AnActionEvent(null, dataContext, "", Presentation(), manager, 0)
      CommandProcessor.getInstance().executeCommand(p, { action.actionPerformed(event) }, "New Version Catalog", null)

      val libs = p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")
      assertThat(libs).isNotNull()
      val libsText = VfsUtil.loadText(libs!!)
      assertThat(libsText).contains("[plugins]")
      assertThat(libsText).contains("[versions]")
      val settingsText = VfsUtil.loadText(p.baseDir?.findChild("settings.gradle")!!)
      assertThat(settingsText).doesNotContainMatch("files.*libs\\.versions\\.toml")
    }
  }

  @Test
  fun testNewVersionCatalogActionOther() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { p ->
      assertThat(p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")).isNull()
      assertThat(p.baseDir?.findChild("gradle")?.findChild("foo.versions.toml")).isNull()
      val dataContext = createTestDataContext(p, "foo")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val event = AnActionEvent(null, dataContext, "", Presentation(), manager, 0)
      CommandProcessor.getInstance().executeCommand(p, { action.actionPerformed(event) }, "New Version Catalog", null)

      val foo = p.baseDir?.findChild("gradle")?.findChild("foo.versions.toml")
      assertThat(foo).isNotNull()
      val libsText = VfsUtil.loadText(foo!!)
      assertThat(libsText).contains("[plugins]")
      assertThat(libsText).contains("[versions]")
      val settingsText = VfsUtil.loadText(p.baseDir?.findChild("settings.gradle")!!)
      assertThat(settingsText).containsMatch("files.*foo\\.versions\\.toml")
      assertThat(settingsText).contains("versionCatalogs")
    }
  }

  @Test
  fun testNewVersionCatalogActionInvalid() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { p ->
      assertThat(p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")).isNull()
      assertThat(p.baseDir?.findChild("gradle")?.findChild("x.versions.toml")).isNull()
      val dataContext = createTestDataContext(p, "x")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val event = AnActionEvent(null, dataContext, "", Presentation(), manager, 0)
      try {
        CommandProcessor.getInstance().executeCommand(p, { action.actionPerformed(event) }, "New Version Catalog", null)
        fail()
      }
      catch (e: RuntimeException) {
        val x = p.baseDir?.findChild("gradle")?.findChild("x.versions.toml")
        assertThat(x).isNull()
        val settingsText = VfsUtil.loadText(p.baseDir?.findChild("settings.gradle")!!)
        assertThat(settingsText).doesNotContainMatch("files.*x\\.versions\\.toml")
      }
    }
  }

  @Test
  fun testNewVersionCatalogActionAlreadyExistsInFilesystem() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val dir = VfsUtil.createDirectoryIfMissing(preparedProject.root.toVirtualFile(), "gradle")
    dir.writeChild("libs.versions.toml", "[libraries]\n")

    preparedProject.open { p ->
      assertThat(p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")).isNotNull()
      val dataContext = createTestDataContext(p, "libs")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val event = AnActionEvent(null, dataContext, "", Presentation(), manager, 0)
      try {
        CommandProcessor.getInstance().executeCommand(p, { action.actionPerformed(event) }, "New Version Catalog", null)
        fail()
      }
      catch (e: RuntimeException) {
        val libs = p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")
        val libsText = VfsUtil.loadText(libs!!)
        assertThat(libsText).isEqualTo("[libraries]\n")
      }
    }
  }

  @Test
  fun testNewVersionCatalogActionAlreadyExistsInSettings() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val dir = VfsUtil.createDirectoryIfMissing(preparedProject.root.toVirtualFile(), "gradle")
    dir.writeChild("bar.versions.toml", "[libraries]\n")

    preparedProject.root.resolve("settings.gradle").toVirtualFile()!!.let { settings ->
      val settingsText = VfsUtil.loadText(settings)
      runWriteAction {
        VfsUtil.saveText(settings, """
        $settingsText

        dependencyResolutionManagement {
          versionCatalogs {
            foo {
              from files('gradle/bar.versions.toml')
            }
          }
        }
      """.trimIndent())
      }
    }

    preparedProject.open { p ->
      assertThat(p.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")).isNull()
      assertThat(p.baseDir?.findChild("gradle")?.findChild("bar.versions.toml")).isNotNull()
      assertThat(p.baseDir?.findChild("gradle")?.findChild("foo.versions.toml")).isNull()
      val dataContext = createTestDataContext(p, "foo")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val event = AnActionEvent(null, dataContext, "", Presentation(), manager, 0)
      CommandProcessor.getInstance().executeCommand(p, { action.actionPerformed(event) }, "New Version Catalog", null)
      val foo = p.baseDir?.findChild("gradle")?.findChild("foo.versions.toml")
      val fooText = VfsUtil.loadText(foo!!)
      assertThat(fooText).contains("[libraries]")
      assertThat(fooText).contains("[plugins]")
      assertThat(fooText).contains("[versions]")
      val settingsText = VfsUtil.loadText(p.baseDir?.findChild("settings.gradle")!!)
      assertThat(settingsText).containsMatch("files.*foo.versions.toml")
      assertThat(settingsText).contains("foo1")

    }
  }

  @Test
  fun testActionIsNormallyEnabled() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { p ->
      val dataContext = createTestDataContext(p, "foo")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val presentation = Presentation()
      presentation.isEnabledAndVisible = true
      val event = AnActionEvent(null, dataContext, "", presentation, manager, 0)
      action.update(event)
      assertThat(presentation.isEnabled).isTrue()
      assertThat(presentation.isVisible).isTrue()
    }
  }

  @Test
  fun testActionIsDisabledForMultipleRoots() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS)
    preparedProject.open { p ->
      val dataContext = createTestDataContext(p, "foo")
      val action = NewVersionCatalogAction()
      val manager = ActionManager.getInstance()
      val presentation = Presentation()
      presentation.isEnabledAndVisible = true
      val event = AnActionEvent(null, dataContext, "", presentation, manager, 0)
      action.update(event)
      assertThat(presentation.isEnabled).isFalse()
      assertThat(presentation.isVisible).isFalse()
    }
  }

  private fun PreparedTestProject.Context.createTestDataContext(project: Project, name: String): DataContext = DataContext {
    when (it) {
      TestDialogBuilder.TestAnswers.KEY.name -> TestDialogBuilder.TestAnswers(name, "Version Catalog File")
      LangDataKeys.IDE_VIEW.name -> object : IdeView {
        val directory = PsiManager.getInstance(project).findDirectory(project.baseDir)!!
        override fun getDirectories(): Array<PsiDirectory> = Array(1) { directory }
        override fun getOrChooseDirectory(): PsiDirectory? = directory
      }
      CommonDataKeys.PROJECT.name -> project
      CommonDataKeys.EDITOR.name -> fixture.editor
      else -> null
    }
  }
}