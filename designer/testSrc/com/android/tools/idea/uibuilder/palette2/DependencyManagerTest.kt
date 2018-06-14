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

import com.android.tools.idea.common.model.NlLayoutType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.uibuilder.palette.NlPaletteModel
import com.android.tools.idea.uibuilder.palette.Palette
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.AndroidTestCase

import javax.xml.ws.Holder

import com.android.SdkConstants.*
import com.android.tools.idea.projectsystem.*
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.*
import java.io.File

class DependencyManagerTest : AndroidTestCase() {
  private var myPanel: PalettePanel? = null
  private var myDisposable: Disposable? = null
  private var myPalette: Palette? = null
  private var myManager: DependencyManager? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    val testProjectSystem = TestProjectSystem(project, availableDependencies = PLATFORM_SUPPORT_LIBS)
    PlatformTestUtil.registerExtension<AndroidProjectSystemProvider>(Extensions.getArea(project), EP_NAME, testProjectSystem,
                                                                     testRootDisposable)

    myPanel = mock(PalettePanel::class.java)
    myDisposable = mock(Disposable::class.java)
    myPalette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT)

    myManager = DependencyManager(project)
    myManager!!.registerDependencyUpdates(myPanel!!, myDisposable!!)
    myManager!!.setPalette(myPalette!!, myModule)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      Disposer.dispose(myDisposable!!)
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myPalette = null
      myPanel = null
      myManager = null
      myDisposable = null
    }
    finally {
      super.tearDown()
    }
  }

  fun testNeedsLibraryLoad() {
    assertThat(myManager!!.needsLibraryLoad(findItem(TEXT_VIEW))).isFalse()
    assertThat(myManager!!.needsLibraryLoad(findItem(FLOATING_ACTION_BUTTON.defaultName()))).isTrue()
  }

  fun testEnsureLibraryIsIncluded() {
    val (floatingActionButton, recyclerView, cardView) =
        listOf(FLOATING_ACTION_BUTTON.defaultName(), RECYCLER_VIEW.defaultName(), CARD_VIEW.defaultName()).map(this::findItem)

    assertThat(myManager!!.needsLibraryLoad(floatingActionButton)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(cardView)).isTrue()

    myManager!!.ensureLibraryIsIncluded(floatingActionButton)
    myManager!!.ensureLibraryIsIncluded(cardView)
    simulateProjectSync()

    assertThat(myManager!!.needsLibraryLoad(floatingActionButton)).isFalse()
    assertThat(myManager!!.needsLibraryLoad(recyclerView)).isTrue()
    assertThat(myManager!!.needsLibraryLoad(cardView)).isFalse()
  }

  fun testRegisterDependencyUpdates() {
    simulateProjectSync()
    verify(myPanel, never())!!.onDependenciesUpdated()

    myManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
    simulateProjectSync()
    verify(myPanel)!!.onDependenciesUpdated()
  }

  fun testDisposeStopsProjectSyncListening() {
    Disposer.dispose(myDisposable!!)

    myManager!!.ensureLibraryIsIncluded(findItem(FLOATING_ACTION_BUTTON.defaultName()))
    simulateProjectSync()
    verify(myPanel, never())!!.onDependenciesUpdated()
  }

  fun testAndroidxDependencies() {
    // The project has no dependencies and NELE_USE_ANDROIDX_DEFAULT is set to true
    assertTrue(myManager!!.useAndroidxDependencies())

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
    assertFalse(myManager!!.useAndroidxDependencies())
    ApplicationManager.getApplication().runWriteAction {
      propertiesDoc.setText("android.useAndroidX=true")
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    assertTrue(myManager!!.useAndroidxDependencies())
  }

  private fun simulateProjectSync() {
    project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
  }

  private fun findItem(tagName: String): Palette.Item {
    val found = Holder<Palette.Item>()
    myPalette!!.accept { item ->
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
