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
package com.android.tools.idea.uibuilder.palette

import com.android.AndroidXConstants.CARD_VIEW
import com.android.AndroidXConstants.FLOATING_ACTION_BUTTON
import com.android.SdkConstants.FN_GRADLE_PROPERTIES
import com.android.AndroidXConstants.RECYCLER_VIEW
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.projectsystem.PLATFORM_SUPPORT_LIBS
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.util.ArrayDeque
import java.util.concurrent.Future

class DependencyManagerTest {
  private var panel: PalettePanel? = null
  private var palette: Palette? = null
  private var disposable: Disposable? = null
  private var dependencyManager: DependencyManager? = null
  private var dependencyUpdateCount = 0
  private val syncListeners = ArrayDeque<Future<*>>()
  private val dialogMessages = mutableListOf<String>()
  private val projectRule = AndroidProjectRule.onDisk()
  private val watcher = TestName()
  private lateinit var testProjectSystem: TestProjectSystem

  @get:Rule
  val rule = RuleChain.outerRule(projectRule).around(watcher)!!

  @Before
  fun setUp() {
    testProjectSystem = TestProjectSystem(projectRule.project, availableDependencies = PLATFORM_SUPPORT_LIBS)
    testProjectSystem.useAndroidX = true
    runInEdtAndWait { testProjectSystem.useInTests() }
    panel = mock(PalettePanel::class.java)
    palette = NlPaletteModel.get(AndroidFacet.getInstance(projectRule.module)!!).getPalette(LayoutFileType)
    disposable = Disposer.newDisposable()
    Disposer.register(projectRule.testRootDisposable, disposable!!)

    dependencyManager = DependencyManager(projectRule.project)
    dependencyManager?.setSyncTopicListener { syncListeners.add(it) }
    if (watcher.methodName != "testNoNotificationOnProjectSyncBeforeSetPalette") {
      dependencyManager!!.setPalette(palette!!, projectRule.module)
      waitAndDispatchAll()
    }
    Disposer.register(disposable!!, dependencyManager!!)
    dependencyManager!!.addDependencyChangeListener { dependencyUpdateCount++ }
    TestDialogManager.setTestDialog { message: String ->
      dialogMessages.add(message.trim()) // Remove line break in the end of the message.
      Messages.OK
    }
  }

  @After
  fun tearDown() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
    // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
    // tests through IJ)
    Disposer.dispose(disposable!!)
    palette = null
    panel = null
    dependencyManager = null
    disposable = null
  }

  @Test
  fun testNeedsLibraryLoad() {
    assertThat(dependencyManager!!.needsLibraryLoad(findItem(TEXT_VIEW))).isFalse()
    assertThat(dependencyManager!!.needsLibraryLoad(findItem(FLOATING_ACTION_BUTTON.defaultName()))).isTrue()
  }

  @Test
  fun testEnsureLibraryIsIncluded() {
    val (floatingActionButton, recyclerView, cardView) =
      listOf(FLOATING_ACTION_BUTTON.defaultName(), RECYCLER_VIEW.defaultName(), CARD_VIEW.defaultName()).map(this::findItem)

    assertThat(dependencyManager!!.needsLibraryLoad(floatingActionButton)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(cardView)).isTrue()

    runInEdtAndWait {
      dependencyManager!!.ensureLibraryIsIncluded(floatingActionButton)
      waitAndDispatchAll()
      dependencyManager!!.ensureLibraryIsIncluded(cardView)
      waitAndDispatchAll()
    }

    assertThat(dependencyManager!!.needsLibraryLoad(floatingActionButton)).isFalse()
    assertThat(dependencyManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(cardView)).isFalse()
  }

  @Test
  fun testRegisterDependencyUpdates() {
    simulateProjectSync()
    assertThat(dependencyUpdateCount).isEqualTo(0)

    runInEdtAndWait {
      dependencyManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
      waitAndDispatchAll()
    }
    assertThat(dependencyUpdateCount).isEqualTo(1)
  }

  @Test
  fun testDisposeStopsProjectSyncListening() {
    Disposer.dispose(disposable!!)

    runInEdtAndWait {
      dependencyManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
      waitAndDispatchAll()
    }

    assertThat(dependencyUpdateCount).isEqualTo(0)
  }

  @Test
  fun testAndroidxDependencies() {
    assertThat(dependencyManager!!.useAndroidXDependencies()).isTrue()

    testProjectSystem.useAndroidX = false
    simulateProjectSync()
    assertThat(dependencyManager!!.useAndroidXDependencies()).isFalse()
    assertThat(dependencyUpdateCount).isEqualTo(1)

    testProjectSystem.useAndroidX = true
    simulateProjectSync()
    assertThat(dependencyManager!!.useAndroidXDependencies()).isTrue()
    assertThat(dependencyUpdateCount).isEqualTo(2)
  }

  @Test
  fun testNoNotificationOnProjectSyncBeforeSetPalette() {
    dependencyManager!!.setNotifyAlways(true)
    simulateProjectSync()
    assertThat(dependencyUpdateCount).isEqualTo(0)
  }

  @Test
  fun testSetPaletteWithDisposedProject() {
    val foo = createTempDirectory("foo")
    val bar = createTempDirectory("bar")
    val tempProject = ProjectManagerEx.getInstanceEx().newProject(foo, OpenProjectTask(isNewProject = true))!!
    val localDependencyManager: DependencyManager

    try {
      val tempModule = WriteCommandAction.runWriteCommandAction(tempProject, Computable<Module> {
        ModuleManager.getInstance(tempProject).newModule(bar, StdModuleTypes.JAVA.id)
      })

      localDependencyManager = DependencyManager(tempProject)
      Disposer.register(tempProject, localDependencyManager)
      localDependencyManager.setPalette(palette!!, tempModule)
    }
    finally {
      WriteCommandAction.runWriteCommandAction(tempProject) {
        Disposer.dispose(tempProject)
      }
    }
  }

  private fun simulateProjectSync() {
    projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    waitAndDispatchAll()
  }

  private fun waitAndDispatchAll() {
    while (syncListeners.isNotEmpty()) {
      syncListeners.remove().get()
    }
    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
  }

  private fun findItem(tagName: String): Palette.Item {
    val found = Ref<Palette.Item>()
    palette!!.accept { item ->
      if (item.tagName == tagName) {
        found.set(item)
      }
    }
    if (found.isNull) {
      throw RuntimeException("The item: $tagName was not found on the palette.")
    }
    return found.get()
  }
}
