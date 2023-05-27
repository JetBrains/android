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
package com.android.tools.idea.ui.resourcemanager

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.res.addAndroidModule
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild
import com.android.tools.idea.ui.resourcemanager.explorer.NoFacetView
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Test
import javax.swing.JLabel
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@org.junit.Ignore("b/253353307")
class ResourceExplorerToolWindowTest {

  lateinit var fixture: CodeInsightTestFixture
  lateinit var module: Module
  var facet: AndroidFacet? = null

  val project: Project get() = fixture.project

  @Before
  fun before() {
    fixture = createHeavyFixture()
    fixture.setUp()
    module = fixture.module
    TestProjectSystem(project, lastSyncResult = ProjectSystemSyncManager.SyncResult.UNKNOWN).useInTests()
    ClearResourceCacheAfterFirstBuild.getInstance(module.project).syncSucceeded()
  }

  @After
  fun after() {
    if (facet != null) {
      runInEdtAndWait {
        val facetManager = FacetManager.getInstance(module)
        val facetModel = facetManager.createModifiableModel()
        facetModel.removeFacet(facet)
        ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
      }
    }
    fixture.tearDown()
  }

  private fun initFacet() {
    val facetManager = FacetManager.getInstance(module)
    facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null)
    runInEdtAndWait {
      val facetModel = facetManager.createModifiableModel()
      facetModel.addFacet(facet)
      ApplicationManager.getApplication().runWriteAction { facetModel.commit() }
    }
  }

  private fun createHeavyFixture(): CodeInsightTestFixture {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(
      AndroidTestCase.AndroidModuleFixtureBuilder::class.java,
      AndroidTestCase.AndroidModuleFixtureBuilderImpl::class.java)

    val projectBuilder = IdeaTestFixtureFactory
      .getFixtureFactory()
      .createFixtureBuilder(ResourceExplorerToolWindowTest::class.java.simpleName)
    val tempDirFixture = TempDirTestFixtureImpl()
    val javaCodeInsightTestFixture = JavaTestFixtureFactory
      .getFixtureFactory()
      .createCodeInsightFixture(projectBuilder.fixture, tempDirFixture)
    val moduleFixtureBuilder = projectBuilder.addModule(AndroidTestCase.AndroidModuleFixtureBuilder::class.java)
    AndroidTestCase.initializeModuleFixtureBuilderWithSrcAndGen(moduleFixtureBuilder, javaCodeInsightTestFixture.tempDirPath)
    return javaCodeInsightTestFixture
  }

  @Test
  fun createWithoutAndroidFacet() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    runInEdtAndWait {
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    }
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(NoFacetView::class.java)
  }

  @Test
  fun createWithAndroidFacet() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    runInEdtAndWait {
      (project.getProjectSystem() as TestProjectSystem).emulateSync(ProjectSystemSyncManager.SyncResult.SUCCESS)
      ClearResourceCacheAfterFirstBuild.getInstance(module.project).syncSucceeded()
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    }
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(ResourceExplorer::class.java)
  }

  @Test
  fun createWithLoadingMessage() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    runInEdtAndWait {
      (DumbService.getInstance(project) as DumbServiceImpl).isDumb = true
    }
    resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    val content = toolWindow.contentManager.contents[0].component
    val label = UIUtil.findComponentOfType<JLabel>(content, JLabel::class.java)
    assertNotNull(label)
    assertNull(label.icon)
    assertThat(label.text).isEqualTo("Loading...")
  }

  @Test
  fun createWithWaitingForSyncMessage() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    (project.getProjectSystem() as TestProjectSystem).emulateSync(ProjectSystemSyncManager.SyncResult.FAILURE)
    val resourceCache = ClearResourceCacheAfterFirstBuild.getInstance(module.project)
    resourceCache.syncFailed()
    runInEdtAndWait {
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
      assertThat(toolWindow.contentManager.contents).isNotEmpty()
      val content = toolWindow.contentManager.contents[0].component
      val label = UIUtil.findComponentOfType<JLabel>(content, JLabel::class.java)
      assertNotNull(label)
      assertNotNull(label.icon)
      assertThat(label.text).isEqualTo("Waiting for successful sync...")
    }
  }

  @Test
  fun createWithWaitingForBuildMessage() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    runInEdtAndWait {
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    }
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    val content = toolWindow.contentManager.contents[0].component
    val label = UIUtil.findComponentOfType<JLabel>(content, JLabel::class.java)
    assertNotNull(label)
    assertNull(label.icon)
    assertThat(label.text).isEqualTo("Waiting for build to finish...")
  }

  @Test
  fun rememberFacetForSecondCreate() {
    val windowManager = ToolWindowManager.getInstance(module.project)
    val toolWindow = windowManager.registerToolWindow("Resources Explorer", false, ToolWindowAnchor.LEFT)
    initFacet()
    val resourceExplorerToolFactory = ResourceExplorerToolFactory()
    runInEdtAndWait {
      (project.getProjectSystem() as TestProjectSystem).emulateSync(ProjectSystemSyncManager.SyncResult.SUCCESS)
      ClearResourceCacheAfterFirstBuild.getInstance(module.project).syncSucceeded()
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    }
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(ResourceExplorer::class.java)
    var resourceExplorer = toolWindow.contentManager.contents[0].component as ResourceExplorer
    assertThat(resourceExplorer.facet.name == "app")

    // Create another module and change the facet in the resourceExplorer
    val module2Name = "app2"
    runInEdtAndWait {
      addAndroidModule(module2Name, project, "com.example.app2") { resourceDir ->
        val drawableDir = resourceDir.resolve("drawable-hdpi").apply { mkdirs() }
        drawableDir.resolve("icon.xml").writeText("<drawable></drawable")
      }
    }
    val facet2 = ModuleManager.getInstance(project).modules.first { it.name == module2Name }.androidFacet!!
    runInEdtAndWait { resourceExplorer.facet = facet2 }

    // Create a new tool window, it should remember to use the new module as it was the last selected
    runInEdtAndWait {
      (project.getProjectSystem() as TestProjectSystem).emulateSync(ProjectSystemSyncManager.SyncResult.SUCCESS)
      ClearResourceCacheAfterFirstBuild.getInstance(module.project).syncSucceeded()
      resourceExplorerToolFactory.createToolWindowContent(module.project, toolWindow)
    }
    assertThat(toolWindow.contentManager.contents).isNotEmpty()
    assertThat(toolWindow.contentManager.contents[0].component).isInstanceOf(ResourceExplorer::class.java)
    val newResourceExplorer = toolWindow.contentManager.contents[0].component as ResourceExplorer
    assertThat(newResourceExplorer).isNotEqualTo(resourceExplorer)
    assertThat(newResourceExplorer.facet.name == "app2")
  }
}