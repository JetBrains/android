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
package com.android.tools.idea.naveditor.surface

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.editor.DesignerEditorPanel
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.inlineDrawRect
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.GuiInputHandler
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.TestInteractable
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.analytics.NavLogEvent
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.NavCoordinate
import com.android.tools.idea.naveditor.scene.NavSceneManager
import com.android.tools.idea.naveditor.scene.updateHierarchy
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.indexing.UnindexedFilesScanner
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doCallRealMethod
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.assertNotEquals

/**
 * Tests for [NavDesignSurface]
 */
class NavDesignSurfaceTest : NavTestCase() {

  lateinit var testProjectSystem: TestProjectSystem

  fun testOpenFileMetrics() {
    val surface = NavDesignSurface(project, project)

    val model = model("nav2.xml") {
      navigation {
        fragment("f1")
        activity("a1")
      }
    }
    TestNavUsageTracker.create(model).use { tracker ->
      PlatformTestUtil.waitForFuture(surface.setModel(model))

      val expectedEvent = NavLogEvent(NavEditorEvent.NavEditorEventType.OPEN_FILE, tracker)
        .withNavigationContents()
        .getProtoForTest()
      assertEquals(1, expectedEvent.contents.fragments)
      verify(tracker).logEvent(expectedEvent)
    }
  }

  private fun <T> any(): T = ArgumentMatchers.any() as T

  fun testComponentActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_main", name = "mytest.navtest.MainActivity")
        fragment("fragment2", layout = "fragment_blank", name = "mytest.navtest.BlankFragment")
      }
    }
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.treeReader.find("fragment1")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("activity_main.xml", editorManager.openFiles[0].name)

      editorManager.closeFile(editorManager.openFiles[0])
      surface.notifyComponentActivate(model.treeReader.find("fragment2")!!)
      assertEquals("fragment_blank.xml", editorManager.openFiles[0].name)
      verify(tracker, times(2)).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_LAYOUT).build())
    }
  }

  fun testNoLayoutComponentActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", name = "mytest.navtest.MainActivity")
        fragment("fragment2", name = "mytest.navtest.BlankFragment")
      }
    }
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.treeReader.find("fragment1")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("MainActivity.java", editorManager.openFiles[0].name)
      editorManager.closeFile(editorManager.openFiles[0])
      surface.notifyComponentActivate(model.treeReader.find("fragment2")!!)
      assertEquals("BlankFragment.java", editorManager.openFiles[0].name)
      verify(tracker, times(2)).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_CLASS).build())
    }
  }

  fun testSubflowActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    TestNavUsageTracker.create(model).use { tracker ->
      assertEquals(model.treeReader.components[0], surface.currentNavigation)
      val subnav = model.treeReader.find("subnav")!!
      surface.notifyComponentActivate(subnav)
      assertEquals(subnav, surface.currentNavigation)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_NESTED).build())
    }
  }

  fun testIncludeActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        include("navigation")
      }
    }
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    TestNavUsageTracker.create(model).use { tracker ->
      surface.notifyComponentActivate(model.treeReader.find("nav")!!)
      val editorManager = FileEditorManager.getInstance(project)
      assertEquals("navigation.xml", editorManager.openFiles[0].name)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(NavEditorEvent.NavEditorEventType.ACTIVATE_INCLUDE).build())
    }
  }

  fun testRootActivated() {
    val surface = NavDesignSurface(project, myRootDisposable)
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val modelListener = mock(ModelListener::class.java)
    val surfaceListener = mock(DesignSurfaceListener::class.java)
    model.addListener(modelListener)
    surface.addListener(surfaceListener)
    assertEquals(model.treeReader.components[0], surface.currentNavigation)
    val root = model.treeReader.find("root")!!
    surface.notifyComponentActivate(root)
    assertEquals(root, surface.currentNavigation)
    verifyNoMoreInteractions(modelListener)
    verifyNoMoreInteractions(surfaceListener)
  }

  fun testDoubleClickFragment() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main2")
      }
    }

    val surface = model.surface as NavDesignSurface
    whenever(surface.layeredPane).thenReturn(mock(JComponent::class.java))
    val guiInputHandler = surface.guiInputHandler
    guiInputHandler.startListening()

    val view = NavView(surface, surface.sceneManager!!)

    surface.scene!!.layout(0, view.context)
    val fragment = surface.scene!!.getSceneComponent("fragment1")!!
    val x = Coordinates.getSwingX(view, fragment.drawX) + 5
    val y = Coordinates.getSwingY(view, fragment.drawY) + 5
    LayoutTestUtilities.clickMouse(guiInputHandler, MouseEvent.BUTTON1, 2, x, y, 0)

    verify(surface).notifyComponentActivate(eq(fragment.nlComponent), anyInt(), anyInt())
  }

  fun testScrollToCenter() {
    val model = NavModelBuilderUtil.model(name = "nav.xml", facet = myFacet, fixture = myFixture,
                                          extentSize = Dimension(1000, 1000),
                                          f = {
                                            navigation("root") {
                                              fragment("fragment1")
                                              fragment("fragment2")
                                              fragment("fragment3")
                                            }
                                          }).build()
    val surface = model.surface as NavDesignSurface
    val view = NavView(surface, surface.sceneManager!!)
    whenever(surface.focusedSceneView).thenReturn(view)
    whenever(surface.scrollDurationMs).thenReturn(1)
    val scheduleRef = AtomicReference<Future<*>>()
    whenever(surface.scheduleRef).thenReturn(scheduleRef)
    doCallRealMethod().whenever(surface).scrollToCenter(any())
    val scrollPosition = Point()
    doAnswer { invocation ->
      scrollPosition.setLocation(invocation.getArgument(0), invocation.getArgument<Int>(1))
      null
    }.whenever(surface).setScrollPosition(anyInt(), anyInt())

    val f1 = model.treeReader.find("fragment1")!!
    val f2 = model.treeReader.find("fragment2")!!
    val f3 = model.treeReader.find("fragment3")!!

    surface.scene!!.getSceneComponent(f1)!!.setPosition(0, 0)
    surface.scene!!.getSceneComponent(f2)!!.setPosition(100, 100)
    surface.scene!!.getSceneComponent(f3)!!.setPosition(200, 200)
    (surface.sceneManager as NavSceneManager).layout(false)
    surface.zoomController.zoomToFit()

    // Scroll pane is centered at 500, 500 so the values below are the absolute positions of the new locations
    verifyScroll(ImmutableList.of(f2), surface, scheduleRef, scrollPosition, 488, 514)
    verifyScroll(ImmutableList.of(f1, f2), surface, scheduleRef, scrollPosition, 463, 489)
    verifyScroll(ImmutableList.of(f1, f3), surface, scheduleRef, scrollPosition, 488, 514)
    verifyScroll(ImmutableList.of(f3), surface, scheduleRef, scrollPosition, 538, 564)
  }

  private fun verifyScroll(
    components: List<NlComponent>,
    surface: NavDesignSurface,
    scheduleRef: AtomicReference<Future<*>>,
    scrollPosition: Point,
    expectedX: Int,
    expectedY: Int
  ) {
    surface.scrollToCenter(components)

    while (scheduleRef.get() != null && !scheduleRef.get().isCancelled) {
      UIUtil.dispatchAllInvocationEvents()
    }
    assertEquals(Point(expectedX, expectedY), scrollPosition)
  }

  fun testDragSelect() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())
    val sceneView = NavView(surface, surface.sceneManager!!)
    whenever(surface.focusedSceneView).thenReturn(sceneView)

    model.surface.selectionModel.setSelection(ImmutableList.of(model.treeReader.find("fragment1")!!))
    val manager = GuiInputHandler(
      surface, TestInteractable(surface, JPanel(), JPanel()), NavInteractionHandler(surface)
    )
    manager.startListening()

    val fragment1 = scene.getSceneComponent("fragment1")!!
    val fragment2 = scene.getSceneComponent("fragment2")!!

    val rect1 = fragment1.fillDrawRect(0, null)
    rect1.grow(5, 5)
    dragSelect(manager, sceneView, rect1)
    assertTrue(fragment1.isSelected)
    assertFalse(fragment2.isSelected)
    dragRelease(manager, sceneView, rect1)
    assertTrue(fragment1.isSelected)
    assertFalse(fragment2.isSelected)

    val rect2 = fragment2.fillDrawRect(0, null)
    rect2.grow(5, 5)
    dragSelect(manager, sceneView, rect2)
    assertFalse(fragment1.isSelected)
    assertTrue(fragment2.isSelected)
    dragRelease(manager, sceneView, rect2)
    assertFalse(fragment1.isSelected)
    assertTrue(fragment2.isSelected)

    val rect3 = Rectangle()
    rect3.add(rect1)
    rect3.add(rect2)
    rect3.grow(5, 5)
    dragSelect(manager, sceneView, rect3)
    assertTrue(fragment1.isSelected)
    assertTrue(fragment2.isSelected)
    dragRelease(manager, sceneView, rect3)
    assertTrue(fragment1.isSelected)
    assertTrue(fragment2.isSelected)

    val rect4 = Rectangle(rect3.x + rect3.width + 10, rect3.y + rect3.height + 10, 100, 100)
    dragSelect(manager, sceneView, rect4)
    assertFalse(fragment1.isSelected)
    assertFalse(fragment2.isSelected)
    dragRelease(manager, sceneView, rect4)
    assertFalse(fragment1.isSelected)
    assertFalse(fragment2.isSelected)

    manager.stopListening()
  }

  fun testRefreshRoot() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        navigation("subnav") {
          navigation("duplicate")
        }
        navigation("othersubnav") {
          navigation("duplicate")
        }
        fragment("oldfragment")
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val root = model.treeReader.components[0]
    assertEquals(root, surface.currentNavigation)
    surface.refreshRoot()
    assertEquals(root, surface.currentNavigation)

    val subnav = model.treeReader.find("subnav")!!
    surface.currentNavigation = subnav
    surface.refreshRoot()
    assertEquals(subnav, surface.currentNavigation)

    val orig = model.treeReader.find("othersubnav")!!.getChild(0)!!
    surface.currentNavigation = orig
    val model2 = model("nav.xml") {
      navigation("foo") {
        fragment("fragment1")
        navigation("subnav") {
          navigation("duplicate")
        }
        navigation("othersubnav") {
          navigation("duplicate")
        }
        activity("newactivity")
      }
    }

    updateHierarchy(model, model2)
    val newVersion = model.treeReader.find("othersubnav")!!.getChild(0)!!
    assertNotEquals(orig, newVersion)
    surface.refreshRoot()
    assertEquals(newVersion, surface.currentNavigation)
  }

  // TODO: Add a similar test that manipulates the NlModel directly instead of changing the XML
  fun testUpdateXml() {
    val model = model("nav.xml") {
      navigation {
        navigation("navigation1") {
          fragment("fragment1")
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    var root = model.treeReader.components[0]
    assertEquals(root, surface.currentNavigation)

    var navigation1 = model.treeReader.find("navigation1")!!
    surface.currentNavigation = navigation1
    assertEquals(navigation1, surface.currentNavigation)

    // Paste in the same xml and verify that the current navigation is unchanged
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(model.file)!!
      document.setText("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                       "    <navigation android:id=\"@+id/navigation1\" app:startDestination=\"@id/fragment1\">\n" +
                       "        <fragment android:id=\"@+id/fragment1\"/>\n" +
                       "    </navigation>\n" +
                       "</navigation>")
      manager.commitAllDocuments()
    }

    navigation1 = model.treeReader.find("navigation1")!!
    assertEquals(navigation1, surface.currentNavigation)

    // Paste in xml that invalidates the current navigation and verify that the current navigation gets reset to the root
    WriteCommandAction.runWriteCommandAction(project) {
      val manager = PsiDocumentManager.getInstance(project)
      val document = manager.getDocument(model.file)!!
      document.setText("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "            xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                       "    <xnavigation android:id=\"@+id/navigation1\" app:startDestination=\"@id/fragment1\">\n" +
                       "        <fragment android:id=\"@+id/fragment1\"/>\n" +
                       "    </xnavigation>\n" +
                       "</navigation>")
      manager.commitAllDocuments()
    }

    updateHierarchy(model, model)

    root = model.treeReader.components[0]
    val component = surface.currentNavigation
    assertEquals(root, component)
  }

  fun testActivateWithSchemaChange() {
    NavigationSchema.createIfNecessary(myModule)
    val editor = mock(DesignerEditorPanel::class.java)
    val surface = NavDesignSurface(project, editor, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model("nav.xml") { navigation() }))
    @Suppress("UNCHECKED_CAST")
    val workbench = mock(WorkBench::class.java) as WorkBench<DesignSurface<*>>
    whenever(editor.workBench).thenReturn(workbench)
    val lock = Semaphore(1)
    lock.acquire()
    // This should indicate that the relevant logic is complete
    whenever(workbench.hideLoading()).then { lock.release() }

    val navigator = addClass("import androidx.navigation.*;\n" +
                             "@Navigator.Name(\"activity_sub\")\n" +
                             "public class TestListeners extends ActivityNavigator {}\n")
    NavigationSchema.get(myModule).rebuildSchema().get()
    val initialSchema = NavigationSchema.get(myModule)

    updateContent(navigator, "import androidx.navigation.*;\n" +
                             "@Navigator.Name(\"activity_sub2\")\n" +
                             "public class TestListeners extends ActivityNavigator {}\n")

    surface.activate()
    // wait for the relevant logic to complete
    var completed = false
    for (i in 0..5) {
      UIUtil.dispatchAllInvocationEvents()
      if (lock.tryAcquire(1, TimeUnit.SECONDS)) {
        completed = true
        break
      }
    }
    assertTrue("hideLoading never executed", completed)
    assertNotEquals(initialSchema, NavigationSchema.get(myModule))
    verify(workbench).showLoading("Refreshing Navigators...")
    verify(workbench).hideLoading()
  }

  fun testRightClick() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())
    val sceneView = NavView(surface, surface.sceneManager!!)
    whenever(surface.focusedSceneView).thenReturn(sceneView)

    model.surface.selectionModel.setSelection(ImmutableList.of(model.treeReader.find("fragment1")!!))

    val manager = GuiInputHandler(
      surface, TestInteractable(surface, JPanel(), JPanel()), NavInteractionHandler(surface)
    )
    manager.startListening()

    try {
      val fragment1 = scene.getSceneComponent("fragment1")!!

      val rect = fragment1.inlineDrawRect(sceneView)
      val x1 = rect.center.x
      val y = rect.center.y.toInt()
      LayoutTestUtilities.pressMouse(manager, MouseEvent.BUTTON3, x1.toInt(), y, 0)

      val x2 = x1 + rect.width * 3
      LayoutTestUtilities.moveMouse(manager, x1.toInt(), y, x2.toInt(), y)
      LayoutTestUtilities.releaseMouse(manager, MouseEvent.BUTTON3, x2.toInt(), y, 0)

      val x3 = x2 - rect.width
      LayoutTestUtilities.moveMouse(manager, x2.toInt(), y, x3.toInt(), y)

      // confirm that right clicking did not end up dragging the fragment
      val finalRect = fragment1.inlineDrawRect(sceneView)
      assertEquals(finalRect, rect)
    }
    finally {
      manager.stopListening()
    }
  }

  private fun addClass(@Language("JAVA") content: String): PsiClass {
    val result = WriteCommandAction.runWriteCommandAction(project, Computable<PsiClass> {
      myFixture.addClass(content)
    })
    WriteAction.runAndWait<RuntimeException> { PsiDocumentManager.getInstance(myModule.project).commitAllDocuments() }
    val dumbService = DumbService.getInstance(project)
    UnindexedFilesScanner(project).queue()
    dumbService.completeJustSubmittedTasks()
    return result
  }

  private fun updateContent(psiClass: PsiClass, @Language("JAVA") newContent: String) {
    WriteCommandAction.runWriteCommandAction(
      project) {
      try {
        psiClass.containingFile.virtualFile.setBinaryContent(newContent.toByteArray())
      }
      catch (e: Exception) {
        fail(e.message)
      }
    }
    WriteAction.runAndWait<RuntimeException> { PsiDocumentManager.getInstance(myModule.project).commitAllDocuments() }
    val dumbService = DumbService.getInstance(project)
    UnindexedFilesScanner(project).queue()
    dumbService.completeJustSubmittedTasks()
  }

  fun testActivateAddNavigator() {
    NavigationSchema.createIfNecessary(myModule)
    val surface = NavDesignSurface(project, mock(DesignerEditorPanel::class.java), project)
    PlatformTestUtil.waitForFuture(surface.setModel(model("nav.xml") { navigation() }))

    addClass("import androidx.navigation.*;\n" +
             "@Navigator.Name(\"activity_sub\")\n" +
             "public class TestListeners extends ActivityNavigator {}\n")
    val initialSchema = NavigationSchema.get(myModule)

    surface.activate()
    initialSchema.rebuildTask?.get()
    assertNotEquals(initialSchema, NavigationSchema.get(myModule))
  }

  fun testGetDependencies() {
    testDependencies(false, "android.arch.navigation")
    testDependencies(true, "androidx.navigation")
  }

  private fun testDependencies(androidX: Boolean, groupId: String) {
    if (androidX != testProjectSystem.useAndroidX) {
      testProjectSystem.useAndroidX = androidX
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
      UIUtil.dispatchAllInvocationEvents()
    }

    val dependencies = NavDesignSurface.getDependencies(myModule)
    val artifactIds = arrayOf("navigation-fragment", "navigation-ui")
    assertEquals(dependencies.count(), artifactIds.count())

    for (i in 0 until dependencies.count()) {
      assertEquals(groupId, dependencies[i].mavenGroupId)
      assertEquals(artifactIds[i], dependencies[i].mavenArtifactId)
    }
  }

  fun testSelection() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        navigation("nested1") {
          fragment("fragment2")
          navigation("nested2") {
            fragment("fragment3")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val root = model.treeReader.find("root")!!
    val fragment1 = model.treeReader.find("fragment1")!!
    val nested1 = model.treeReader.find("nested1")!!
    val fragment2 = model.treeReader.find("fragment2")!!
    val nested2 = model.treeReader.find("nested2")!!
    val fragment3 = model.treeReader.find("fragment3")!!

    testCurrentNavigation(surface, root, root)
    testCurrentNavigation(surface, root, fragment1)
    testCurrentNavigation(surface, root, nested1)
    testCurrentNavigation(surface, nested1, fragment2)
    testCurrentNavigation(surface, nested1, nested1)
    testCurrentNavigation(surface, nested1, nested2)
    testCurrentNavigation(surface, nested2, fragment3)
    testCurrentNavigation(surface, root, root)

    testCurrentNavigation(surface, root, fragment1, nested1)
    testCurrentNavigation(surface, nested1, fragment2, nested2)
    testCurrentNavigation(surface, nested1, fragment2, nested2, fragment1)
  }

  private fun testCurrentNavigation(surface: NavDesignSurface, expected: NlComponent, vararg select: NlComponent) {
    surface.selectionModel.setSelection(select.toList())
    assertEquals(expected.id, surface.currentNavigation.id)
  }

  private fun dragSelect(manager: GuiInputHandler, sceneView: SceneView, @NavCoordinate rect: Rectangle) {
    @SwingCoordinate val x1 = Coordinates.getSwingX(sceneView, rect.x)
    @SwingCoordinate val y1 = Coordinates.getSwingY(sceneView, rect.y)
    @SwingCoordinate val x2 = Coordinates.getSwingX(sceneView, rect.x + rect.width)
    @SwingCoordinate val y2 = Coordinates.getSwingY(sceneView, rect.y + rect.height)

    LayoutTestUtilities.pressMouse(manager, MouseEvent.BUTTON1, x1, y1, 0)
    LayoutTestUtilities.dragMouse(manager, x1, y1, x2, y2, 0)
  }

  private fun dragRelease(manager: GuiInputHandler, sceneView: SceneView, @NavCoordinate rect: Rectangle) {
    @SwingCoordinate val x2 = Coordinates.getSwingX(sceneView, rect.x + rect.width)
    @SwingCoordinate val y2 = Coordinates.getSwingY(sceneView, rect.y + rect.height)

    LayoutTestUtilities.releaseMouse(manager, MouseEvent.BUTTON1, x2, y2, 0)
  }

  override fun setUp() {
    super.setUp()
    testProjectSystem = TestProjectSystem(project)
    testProjectSystem.useAndroidX = true
    testProjectSystem.useInTests()
  }
}
