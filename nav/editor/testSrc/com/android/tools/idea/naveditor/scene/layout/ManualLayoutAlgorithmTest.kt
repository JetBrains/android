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
package com.android.tools.idea.naveditor.scene.layout

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.editor.NavEditor
import com.android.tools.idea.naveditor.scene.flatten
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.DocumentsEditor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil

/**
 * Tests for [ManualLayoutAlgorithm]
 */
class ManualLayoutAlgorithmTest : NavTestCase() {

  fun testSimple() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    val positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    var newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(123, 456)
    positions.put("fragment1", newPositions)

    newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(456, 789)
    positions.put("fragment2", newPositions)

    val scene = model.surface.scene!!
    val algorithm = createAlgorithm(model, rootPositions)
    algorithm.layout(scene.root!!.flatten())

    assertEquals(123, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(456, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(789, scene.getSceneComponent("fragment2")!!.drawY)
  }

  fun testNewNestedGraph() {
    val model = model("nav.xml") {
      navigation {
        navigation("subnav") {
          fragment("fragment1")
          fragment("fragment2")
          fragment("fragment4")
        }
        fragment("fragment3")
      }
    }
    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    val positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    for (i in 1..4) {
      val newPositions = ManualLayoutAlgorithm.LayoutPositions()
      newPositions.myPosition = ManualLayoutAlgorithm.Point(i * 100, i * 100 + 50)
      positions.put("fragment$i", newPositions)
    }

    val scene = model.surface.scene!!
    val algorithm = createAlgorithm(model, rootPositions)
    algorithm.layout(scene.root!!.children)

    assertEquals(232, scene.getSceneComponent("subnav")!!.drawX)
    assertEquals(283, scene.getSceneComponent("subnav")!!.drawY)
    assertEquals(300, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(350, scene.getSceneComponent("fragment3")!!.drawY)
  }

  fun testDifferentFiles() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }
    val model2 = model("nav2.xml") {
      navigation {
        fragment("fragment1")
      }
    }
    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    var positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    var newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(123, 456)
    positions.put("fragment1", newPositions)

    positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav2.xml", positions)

    newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(456, 789)
    positions.put("fragment1", newPositions)

    val scene = model.surface.scene!!
    val algorithm = createAlgorithm(model, rootPositions)
    algorithm.layout(scene.root!!.flatten())

    val scene2 = model2.surface.scene!!
    algorithm.layout(scene2.root!!.flatten())

    assertEquals(123, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(456, scene2.getSceneComponent("fragment1")!!.drawX)
    assertEquals(789, scene2.getSceneComponent("fragment1")!!.drawY)
  }

  fun testSave() {
    var model = model("nav.xml") {
      navigation("nav") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    var surface = NavDesignSurface(project, myRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    var component = surface.scene!!.getSceneComponent("fragment1")!!
    component.setPosition(100, 200)
    var algorithm = createAlgorithm(model)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project, true)

    assertTrue(
      LocalFileSystem.getInstance().findFileByPath(project.basePath!! + "/.idea/navEditor.xml")!!
        .contentsToByteArray().toString(Charsets.UTF_8)
        .contains("fragment1"))

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml") {
      navigation("nav") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    surface = NavDesignSurface(project, myRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    component = surface.scene!!.getSceneComponent("fragment1")!!
    algorithm = createAlgorithm(model)
    algorithm.layout(listOf(component))
    assertEquals(100, component.drawX)
    assertEquals(200, component.drawY)
  }

  fun testSaveWithError() {
    var model = model("nav.xml") {
      navigation("nav") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    var algorithm = createAlgorithm(model)
    var surface = NavDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val nullIdComponent = surface.scene!!.getSceneComponent("fragment1")!!
    WriteCommandAction.runWriteCommandAction(project) { nullIdComponent.nlComponent.setAndroidAttribute(ATTR_ID, null) }
    nullIdComponent.setPosition(100, 200)
    algorithm.save(nullIdComponent)

    var component = surface.scene!!.getSceneComponent("fragment2")!!
    component.setPosition(400, 500)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project)

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml") {
      navigation("nav") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    surface = NavDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    component = surface.scene!!.getSceneComponent("fragment2")!!
    algorithm = createAlgorithm(model)
    algorithm.layout(listOf(component))
    assertEquals(400, component.drawX)
    assertEquals(500, component.drawY)

    // don't need to test the null id component; behavior there is undefined.
  }

  fun testChangeId() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    val positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    var newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(123, 456)
    positions.put("fragment1", newPositions)

    newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(456, 789)
    positions.put("fragment2", newPositions)

    val scene = model.surface.scene!!
    val algorithm = createAlgorithm(model, rootPositions)
    algorithm.layout(scene.root!!.flatten())

    WriteCommandAction.runWriteCommandAction(project) { model.find("fragment1")!!.setAttribute(ANDROID_URI, ATTR_ID, "@+id/renamed") }

    scene.root!!.flatten().forEach { it.setPosition(0, 0) }
    algorithm.layout(scene.root!!.flatten())


    assertEquals(123, scene.getSceneComponent("renamed")!!.drawX)
    assertEquals(456, scene.getSceneComponent("renamed")!!.drawY)
    assertEquals(456, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(789, scene.getSceneComponent("fragment2")!!.drawY)
  }

  fun testUndo() {
    val model = model("nav.xml") {
      navigation("nav") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    val editor = object : NavEditor(model.virtualFile, project), DocumentsEditor {
      override fun getDocuments() = arrayOf(FileDocumentManager.getInstance().getDocument(model.virtualFile)!!)
    }
    val surface = NavDesignSurface(project, myRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))
    val component = surface.scene!!.getSceneComponent("fragment1")!!
    component.setPosition(100, 200)
    val algorithm = createAlgorithm(model)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project)
    component.setPosition(300, 400)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project)

    assertEquals(300, component.drawX)
    assertEquals(400, component.drawY)

    UndoManager.getInstance(model.project).undo(editor)

    assertEquals(100, component.drawX)
    assertEquals(200, component.drawY)
  }

  fun testSkipPersisted() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2")
      }
    }
    model.find("fragment2")!!.putClientProperty(SKIP_PERSISTED_LAYOUT, true)
    val scene = model.surface.scene!!
    val fragment1 = scene.getSceneComponent("fragment1")!!
    fragment1.setPosition(10, 20)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(30, 40)

    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    val positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    var newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(123, 456)
    positions.put("fragment1", newPositions)

    newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(456, 789)
    positions.put("fragment2", newPositions)

    val algorithm = createAlgorithm(model, rootPositions)
    val notLaidOut = algorithm.layout(scene.root!!.children)
    assertSameElements(notLaidOut, fragment2)
    assertEquals(123, fragment1.drawX)
    assertEquals(456, fragment1.drawY)
    assertEquals(30, fragment2.drawX)
    assertEquals(40, fragment2.drawY)
  }

  private fun createAlgorithm(model: SyncNlModel, positions: ManualLayoutAlgorithm.LayoutPositions? = null): ManualLayoutAlgorithm {
    return if (positions == null) ManualLayoutAlgorithm(myModule)
    else ManualLayoutAlgorithm(positions, myModule)
  }
}
