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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.facet.java.JavaFacet
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.sdk.Jdks
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testing.AndroidGradleTests.getEmbeddedJdk8Path
import com.android.tools.idea.testing.AndroidGradleTests.syncProject
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.util.function.Consumer

class OpenProjectIntegrationTest : GradleSyncIntegrationTestCase(), GradleIntegrationTest {
  fun testReopenProject() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project)
    }
  }

  fun testReopenKaptProject() {
    prepareGradleProject(TestProjectPaths.KOTLIN_KAPT, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project)
    }
  }

  fun testReopenProjectAfterFailedSync() {
    val root = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    val buildFile = VfsUtil.findFileByIoFile(root.resolve("app/build.gradle"), true)!!

    val lastSyncFinishedTimestamp = openPreparedProject("project") { project ->
      runWriteAction {
        buildFile.setBinaryContent("*bad*".toByteArray())
      }
      syncProject(project, GradleSyncInvoker.Request.testRequest())
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp
    }

    openPreparedProject(
      "project",
      verifyOpened = { project ->
        assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.FAILURE)
      }
    ) {
      // Make sure we tried to sync.
      assertThat(GradleSyncState.getInstance(project).lastSyncFinishedTimeStamp).isNotEqualTo(lastSyncFinishedTimestamp)
    }
  }

  fun testReopenCompositeBuildProject() {
    prepareGradleProject(TestProjectPaths.COMPOSITE_BUILD, "project")
    openPreparedProject("project") { }
    openPreparedProject("project") { project ->
      verifySyncSkipped(project)
    }
  }

  fun testReopenWithoutModules() {
    val projectRoot = prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")
    openPreparedProject("project") { }

    val projectRootVirtualFile = VfsUtil.findFileByIoFile(projectRoot, false)!!
    // Tests always run in do not generate *.iml files mode.
    assertThat(projectRootVirtualFile.findFileByRelativePath(".idea/modules.xml")).isNull()

    runWriteAction {
      projectRootVirtualFile.findFileByRelativePath(".idea/modules/app/project.app.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath(".idea/modules/project.iml")!!.delete("test")
    }

    openPreparedProject("project") { project ->
      assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SUCCESS)
      project.verifyModelsAttached()
    }
  }

  fun testOpen36Project() {
    addJdk8ToTable()
    prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    openPreparedProject("project") { project ->
      val androidTestRunConfiguration =
        RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<AndroidTestRunConfiguration>().singleOrNull()
      assertThat(androidTestRunConfiguration?.name).isEqualTo("All Tests Sub 36")

      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app",
        "sub36" to "My36.app.sub36",
        "All Tests Sub 36" to "My36.app.sub36"
      ))
    }
  }

  fun testOpen36ProjectWithoutModules() {
    addJdk8ToTable()
    val projectRoot = prepareGradleProject(TestProjectPaths.RUN_APP_36, "project")
    runWriteAction {
      val projectRootVirtualFile = VfsUtil.findFileByIoFile(projectRoot, false)!!
      projectRootVirtualFile.findFileByRelativePath(".idea/modules.xml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/app.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("app/sub36/sub36.iml")!!.delete("test")
      projectRootVirtualFile.findFileByRelativePath("My36.iml")!!.delete("test")
    }

    openPreparedProject("project") { project ->
      val runConfigurations = RunManagerEx.getInstanceEx(project).allConfigurationsList.filterIsInstance<ModuleBasedConfiguration<*, *>>()
      assertThat(runConfigurations.associate { it.name to it.configurationModule?.module?.name }).isEqualTo(mapOf(
        "app" to "My36.app",
        "sub36" to "My36.app.sub36",
        "All Tests Sub 36" to "My36.app.sub36"
      ))
    }
  }

  private fun addJdk8ToTable() {
    val jdkTable = ProjectJdkTable.getInstance();
    val jdk = Jdks.getInstance().createJdk(getEmbeddedJdk8Path())
    assertThat(jdk).isNotNull()
    runWriteAction {
      jdkTable.addJdk(jdk!!)
    }
  }

  private fun verifySyncSkipped(project: Project) {
    assertThat(project.getProjectSystem().getSyncManager().getLastSyncResult()).isEqualTo(ProjectSystemSyncManager.SyncResult.SKIPPED)
    project.verifyModelsAttached()
    var completed = false
    project.runWhenSmartAndSynced(testRootDisposable, callback = Consumer {
      completed = true
    })
    assertThat(completed).isTrue()
  }
}

inline fun <reified F, reified M> Module.verifyModel(getFacet: Module.() -> F?, getModel: F.() -> M) {
  val facet = getFacet()
  if (facet != null) {
    val model = facet.getModel()
    assertThat(model).named("${M::class.simpleName} for ${F::class.simpleName} in ${name} module").isNotNull()
  }
}

private fun Project.verifyModelsAttached() {
  ModuleManager.getInstance(this).modules.forEach { module ->
    module.verifyModel(GradleFacet::getInstance, GradleFacet::getGradleModuleModel)
    if (GradleFacet.getInstance(module) != null) {
      // Java facets are not created for modules without GradleFacet even if there is a JavaModuleModel.
      module.verifyModel(JavaFacet::getInstance, JavaFacet::getJavaModuleModel)
    }
    module.verifyModel(AndroidFacet::getInstance, AndroidModuleModel::get)
    module.verifyModel({ NdkFacet.getInstance(this) }, { ndkModuleModel })
  }
}
