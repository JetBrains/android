/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette2

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlLayoutType
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.projectsystem.*
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.uibuilder.palette.NlPaletteModel
import com.android.tools.idea.uibuilder.palette.Palette
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.registerExtension
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.mock
import java.io.File
import javax.xml.ws.Holder

class DependencyManagerTest : AndroidTestCase() {
  private var panel: PalettePanel? = null
  private var palette: Palette? = null
  private var disposable: Disposable? = null
  private var dependencyManager: DependencyManager? = null
  private var dependencyUpdateCount = 0

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    val testProjectSystem = TestProjectSystem(project, availableDependencies = PLATFORM_SUPPORT_LIBS)
    project.registerExtension<AndroidProjectSystemProvider>(EP_NAME, testProjectSystem, testRootDisposable)
    panel = mock(PalettePanel::class.java)
    palette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT)
    disposable = Disposer.newDisposable()
    Disposer.register(testRootDisposable, disposable!!)

    dependencyManager = DependencyManager(project)
    if (getTestName(true) != "noNotificationOnProjectSyncBeforeSetPalette") {
      dependencyManager!!.setPalette(palette!!, myModule)
    }
    Disposer.register(disposable!!, dependencyManager!!)
    dependencyManager!!.addDependencyChangeListener { dependencyUpdateCount++ }
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      AndroidModuleInfo.setInstanceForTest(myFacet, null)
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      Disposer.dispose(disposable!!)
      palette = null
      panel = null
      dependencyManager = null
      disposable = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testNeedsLibraryLoad() {
    assertThat(dependencyManager!!.needsLibraryLoad(findItem(TEXT_VIEW))).isFalse()
    assertThat(dependencyManager!!.needsLibraryLoad(findItem(FLOATING_ACTION_BUTTON.defaultName()))).isTrue()
  }

  fun testEnsureLibraryIsIncluded() {
    val (floatingActionButton, recyclerView, cardView) =
        listOf(FLOATING_ACTION_BUTTON.defaultName(), RECYCLER_VIEW.defaultName(), CARD_VIEW.defaultName()).map(this::findItem)

    assertThat(dependencyManager!!.needsLibraryLoad(floatingActionButton)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(cardView)).isTrue()

    dependencyManager!!.ensureLibraryIsIncluded(floatingActionButton)
    dependencyManager!!.ensureLibraryIsIncluded(cardView)
    simulateProjectSync()

    assertThat(dependencyManager!!.needsLibraryLoad(floatingActionButton)).isFalse()
    assertThat(dependencyManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(dependencyManager!!.needsLibraryLoad(cardView)).isFalse()
  }

  fun testRegisterDependencyUpdates() {
    simulateProjectSync()
    assertEquals(0, dependencyUpdateCount)

    dependencyManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
    simulateProjectSync()
    assertEquals(1, dependencyUpdateCount)
  }

  fun testDisposeStopsProjectSyncListening() {
    Disposer.dispose(disposable!!)

    dependencyManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
    simulateProjectSync()
    assertEquals(0, dependencyUpdateCount)
  }

  fun testAndroidxDependencies() {
    // The project has no dependencies and NELE_USE_ANDROIDX_DEFAULT is set to true
    assertTrue(dependencyManager!!.useAndroidXDependencies())

    val gradlePropertiesFile = ApplicationManager.getApplication().runWriteAction(Computable<VirtualFile> {
      val projectDir = VfsUtil.findFileByIoFile(File(project.basePath), true)!!
      projectDir.createChildData(null, FN_GRADLE_PROPERTIES)
    })

    val propertiesPsi = PsiManager.getInstance(project).findFile(gradlePropertiesFile)!!
    val propertiesDoc = PsiDocumentManager.getInstance(project).getDocument(propertiesPsi)!!

    // Check explicitly setting the variable
    ApplicationManager.getApplication().runWriteAction {
      propertiesDoc.setText("android.useAndroidX=false")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    simulateProjectSync()
    assertFalse(dependencyManager!!.useAndroidXDependencies())
    assertEquals(1, dependencyUpdateCount)

    ApplicationManager.getApplication().runWriteAction {
      propertiesDoc.setText("android.useAndroidX=true")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    simulateProjectSync()
    assertTrue(dependencyManager!!.useAndroidXDependencies())
    assertEquals(2, dependencyUpdateCount)
  }

  fun testNoNotificationOnProjectSyncBeforeSetPalette() {
    dependencyManager!!.setNotifyAlways(true)
    simulateProjectSync()
    assertEquals(0, dependencyUpdateCount)
  }

  private fun simulateProjectSync() {
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
  }

  private fun findItem(tagName: String): Palette.Item {
    val found = Holder<Palette.Item>()
    palette!!.accept { item ->
      if (item.tagName == tagName) {
        found.value = item
      }
    }
    if (found.value == null) {
      throw RuntimeException("The item: $tagName was not found on the palette.")
    }
    return found.value
  }
}
