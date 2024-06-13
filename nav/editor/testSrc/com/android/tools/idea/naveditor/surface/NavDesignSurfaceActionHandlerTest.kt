/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.model.startDestination
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import org.mockito.Mockito.mock

class NavDesignSurfaceActionHandlerTest : NavTestCase() {

  override fun setUp() {
    super.setUp()
    HeadlessDataManager.fallbackToProductionDataManager(testRootDisposable)
  }

  fun testDelete() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
        }
        fragment("fragment2") {
          action("a2", destination = "fragment3")
        }
        fragment("fragment3") {
          action("a3", destination = "fragment1")
        }
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val handler = NavDesignSurfaceActionHandler(surface)
    val context = DataManager.getInstance().getDataContext(model.surface)
    assertFalse(handler.canDeleteElement(context))

    surface.selectionModel.setSelection(listOf(model.find("fragment2")!!))
    assertTrue(handler.canDeleteElement(context))

    handler.deleteElement(context)

    assertSameElements(model.flattenComponents().toArray(),
                       model.components[0], model.find("fragment1"), model.find("fragment3"), model.find("a3"))
    assertEquals(surface.selectionModel.selection, listOf(model.find("fragment3")))
    val root = model.find("root")!!
    assertNull(root.startDestination)

    surface.selectionModel.setSelection(listOf(model.find("a3")!!))
    handler.deleteElement(context)
    assertEquals(surface.selectionModel.selection, model.components)
  }

  fun testUndoRedoDelete() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment")
        fragment("fragment2")
      }
    }

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val handler = NavDesignSurfaceActionHandler(surface)
    val context = DataManager.getInstance().getDataContext(model.surface)
    var nlComponent = model.find("fragment")!!
    val scene = surface.scene!!
    var sceneComponent = scene.getSceneComponent(nlComponent)!!
    sceneComponent.setPosition(123, 456)
    val sceneManager = surface.sceneManager!!
    sceneManager.save(listOf(sceneComponent))
    val editor = TestNavEditor(model.virtualFile, project)

    surface.selectionModel.setSelection(listOf(nlComponent))
    handler.deleteElement(context)

    assertNull(model.find("fragment"))
    assertNull(scene.getSceneComponent("fragment"))

    // move something so ManualLayoutAlgorithm doesn't have stale info
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(987, 654)
    sceneManager.save(listOf(fragment2))

    model.notifyModified(NlModel.ChangeType.EDIT)
    sceneManager.update()

    // undo the move
    UndoManager.getInstance(project).undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertNull(model.find("fragment"))
    assertNull(scene.getSceneComponent("fragment"))

    // undo the delete
    UndoManager.getInstance(project).undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    nlComponent = model.find("fragment")!!
    sceneComponent = scene.getSceneComponent(nlComponent)!!

    assertNotNull(nlComponent)
    assertEquals(123, sceneComponent.drawX)
    assertEquals(456, sceneComponent.drawY)

    // redo the delete
    UndoManager.getInstance(project).redo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertNull(model.find("fragment"))
    assertNull(scene.getSceneComponent("fragment"))

    // undo again
    UndoManager.getInstance(project).undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    nlComponent = model.find("fragment")!!
    sceneComponent = scene.getSceneComponent(nlComponent)!!

    assertNotNull(nlComponent)
    assertEquals(123, sceneComponent.drawX)
    assertEquals(456, sceneComponent.drawY)
  }

  fun testGetPasteTarget() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
        }
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3")
        }
      }
    }
    val surface = model.surface as NavDesignSurface
    val handler = NavDesignSurfaceActionHandler(surface)

    val root = model.components[0]
    val subnav = model.find("subnav")!!

    assertEquals(root, handler.pasteTarget)
    surface.selectionModel.setSelection(listOf())
    assertEquals(root, handler.pasteTarget)
    val a1 = model.find("a1")!!
    surface.selectionModel.setSelection(listOf(a1))
    assertEquals(a1, handler.pasteTarget)
    surface.selectionModel.setSelection(listOf(subnav))
    assertEquals(subnav, handler.pasteTarget)

    whenever(surface.currentNavigation).thenReturn(subnav)
    surface.selectionModel.setSelection(listOf())
    assertEquals(subnav, handler.pasteTarget)
    surface.selectionModel.setSelection(listOf(subnav))
    assertEquals(subnav, handler.pasteTarget)
    val fragment3 = model.find("fragment3")!!
    surface.selectionModel.setSelection(listOf(fragment3))
    assertEquals(fragment3, handler.pasteTarget)
  }

  fun testCanHandleChildren() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
          action("a2", destination = "subnav")
        }
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3")
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    val handler = NavDesignSurfaceActionHandler(surface)

    val root = model.components[0]
    val subnav = model.find("subnav")!!
    val fragment1 = model.find("fragment1")!!
    val action1 = model.find("a1")!!
    val action2 = model.find("a2")!!

    assertTrue(handler.canHandleChildren(root, listOf(fragment1)))
    assertTrue(handler.canHandleChildren(root, listOf(action1, fragment1)))
    assertTrue(handler.canHandleChildren(fragment1, listOf(action1)))
    assertFalse(handler.canHandleChildren(action1, listOf(fragment1)))
    assertFalse(handler.canHandleChildren(action1, listOf(action2)))
    assertTrue(handler.canHandleChildren(subnav, listOf(action1)))
    assertFalse(handler.canHandleChildren(subnav, listOf(fragment1)))

    whenever(surface.currentNavigation).thenReturn(subnav)
    assertTrue(handler.canHandleChildren(subnav, listOf(fragment1)))
  }

  fun testCutPasteToSubnav() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
          action("a2", destination = "subnav")
        }
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3")
        }
      }
    }

    val root = model.components[0]
    val subnav = model.find("subnav")!!
    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val handler = NavDesignSurfaceActionHandler(surface)

    surface.selectionModel.setSelection(listOf(fragment1))
    handler.performCut(mock(DataContext::class.java))
    surface.currentNavigation = subnav
    surface.selectionModel.clear()
    handler.performPaste(mock(DataContext::class.java))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertSameElements(root.children, fragment2, subnav)
    assertSameElements(subnav.children.map { it.id }, "fragment1", "fragment3")
  }

  fun testCutPasteAction() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
          action("a2", destination = "subnav")
        }
        fragment("fragment2")
        navigation("subnav") {
          fragment("fragment3")
        }
      }
    }

    val subnav = model.find("subnav")!!
    val fragment1 = model.find("fragment1")!!
    val fragment2 = model.find("fragment2")!!
    val action1 = model.find("a1")!!
    val action2 = model.find("a2")!!

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val handler = NavDesignSurfaceActionHandler(surface)

    surface.selectionModel.setSelection(listOf(action1))
    handler.performCut(mock(DataContext::class.java))
    surface.currentNavigation = subnav
    surface.selectionModel.setSelection(listOf(fragment2))
    handler.performPaste(mock(DataContext::class.java))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertSameElements(fragment1.children, action2)
    assertSameElements(fragment2.children.map { it.id }, "a1")
  }

  fun testCopyPasteToSelf() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val root = model.find("root")!!
    val fragment1 = model.find("fragment1")!!

    val surface = NavDesignSurface(project, project)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val handler = NavDesignSurfaceActionHandler(surface)

    surface.selectionModel.setSelection(listOf(fragment1))
    handler.performCopy(mock(DataContext::class.java))
    handler.performPaste(mock(DataContext::class.java))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val fragment = model.find("fragment")
    assertNotNull(fragment)
    assertSameElements(root.children, fragment, fragment1)
  }
}