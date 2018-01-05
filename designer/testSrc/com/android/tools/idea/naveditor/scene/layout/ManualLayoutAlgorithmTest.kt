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

import com.android.SdkConstants.ATTR_ID
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.fragmentComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.rootComponent
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.mockito.Mockito.*

/**
 * Tests for [ManualLayoutAlgorithm]
 */
class ManualLayoutAlgorithmTest : NavTestCase() {

  fun testSimple() {
    val model = model("nav.xml",
        rootComponent(null)
            .unboundedChildren(
                fragmentComponent("fragment1"),
                fragmentComponent("fragment2"))).build()
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
    val fallback = mock(NavSceneLayoutAlgorithm::class.java)
    val algorithm = ManualLayoutAlgorithm(fallback, NavigationSchema.get(myFacet), rootPositions)
    scene.root!!.flatten().forEach { algorithm.layout(it) }
    verifyZeroInteractions(fallback)

    assertEquals(123, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(456, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(789, scene.getSceneComponent("fragment2")!!.drawY)
  }

  fun testDifferentFiles() {
    val model = model("nav.xml",
        rootComponent(null)
            .unboundedChildren(
                fragmentComponent("fragment1"))).build()

    val model2 = model("nav2.xml",
        rootComponent(null)
            .unboundedChildren(
                fragmentComponent("fragment1"))).build()

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
    val fallback = mock(NavSceneLayoutAlgorithm::class.java)
    val algorithm = ManualLayoutAlgorithm(fallback, NavigationSchema.get(myFacet), rootPositions)
    scene.root!!.flatten().forEach { algorithm.layout(it) }

    val scene2 = model2.surface.scene!!
    scene2.root!!.flatten().forEach { algorithm.layout(it) }

    assertEquals(123, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(456, scene2.getSceneComponent("fragment1")!!.drawX)
    assertEquals(789, scene2.getSceneComponent("fragment1")!!.drawY)
  }

  fun testFallback() {
    val model = model("nav.xml",
        rootComponent("root")
            .unboundedChildren(
                fragmentComponent("fragment1"),
                fragmentComponent("fragment2"),
                fragmentComponent("fragment3"))).build()

    val rootPositions = ManualLayoutAlgorithm.LayoutPositions()
    val positions = ManualLayoutAlgorithm.LayoutPositions()
    rootPositions.put("nav.xml", positions)

    var newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(60, 60)
    positions.put("fragment1", newPositions)

    newPositions = ManualLayoutAlgorithm.LayoutPositions()
    newPositions.myPosition = ManualLayoutAlgorithm.Point(200, 200)
    positions.put("fragment3", newPositions)


    val scene = model.surface.scene!!
    val fallback = mock(NavSceneLayoutAlgorithm::class.java)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    doAnswer { invocation ->
      (invocation.getArgument<Any>(0) as SceneComponent).setPosition(123, 456)
      null
    }.`when`(fallback).layout(fragment2)
    val algorithm = ManualLayoutAlgorithm(fallback, NavigationSchema.get(myFacet), rootPositions)
    scene.root!!.flatten().forEach { algorithm.layout(it) }
    verify(fallback).layout(fragment2)
    verifyNoMoreInteractions(fallback)

    assertEquals(60, scene.getSceneComponent("fragment1")!!.drawX)
    assertEquals(60, scene.getSceneComponent("fragment1")!!.drawY)
    assertEquals(123, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment2")!!.drawY)
    assertEquals(200, scene.getSceneComponent("fragment3")!!.drawX)
    assertEquals(200, scene.getSceneComponent("fragment3")!!.drawY)

    algorithm.layout(fragment2)
    verifyNoMoreInteractions(fallback)
    assertEquals(123, scene.getSceneComponent("fragment2")!!.drawX)
    assertEquals(456, scene.getSceneComponent("fragment2")!!.drawY)
  }

  @Throws(Exception::class)
  fun testSave() {
    var model = model("nav.xml",
        rootComponent("nav").unboundedChildren(
            fragmentComponent("fragment1"),
            fragmentComponent("fragment2"))).build()
    var surface = NavDesignSurface(project, myRootDisposable)
    surface.model = model
    var component = surface.scene!!.getSceneComponent("fragment1")!!
    component.setPosition(100, 200)
    var algorithm = ManualLayoutAlgorithm(model.module)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project)

    // Tests always use file-based storage, not directory-based
    assertTrue(FileUtil.loadFile(VfsUtilCore.virtualToIoFile(project.projectFile!!)).contains("fragment1"))

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml", rootComponent("nav").unboundedChildren(
        fragmentComponent("fragment1"),
        fragmentComponent("fragment2"))).build()
    surface = NavDesignSurface(project, myRootDisposable)
    surface.model = model
    component = surface.scene!!.getSceneComponent("fragment1")!!
    algorithm = ManualLayoutAlgorithm(model.module)
    algorithm.layout(component)
    assertEquals(100, component.drawX)
    assertEquals(200, component.drawY)
  }

  fun testSaveWithError() {
    var algorithm = ManualLayoutAlgorithm(myModule)
    var model = model("nav.xml",
        rootComponent("nav").unboundedChildren(
            fragmentComponent("fragment1"),
            fragmentComponent("fragment2"))).build()
    var surface = NavDesignSurface(project, testRootDisposable)
    surface.model = model
    val nullIdComponent = surface.scene!!.getSceneComponent("fragment1")!!
    WriteCommandAction.runWriteCommandAction(project) { nullIdComponent.nlComponent.setAndroidAttribute(ATTR_ID, null) }
    nullIdComponent.setPosition(100, 200)
    algorithm.save(nullIdComponent)

    var component = surface.scene!!.getSceneComponent("fragment2")!!
    component.setPosition(400, 500)
    algorithm.save(component)
    PlatformTestUtil.saveProject(project)

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml", rootComponent("nav").unboundedChildren(
        fragmentComponent("fragment1"),
        fragmentComponent("fragment2"))).build()
    surface = NavDesignSurface(project, testRootDisposable)
    surface.model = model
    component = surface.scene!!.getSceneComponent("fragment2")!!
    algorithm = ManualLayoutAlgorithm(model.module)
    algorithm.layout(component)
    assertEquals(400, component.drawX)
    assertEquals(500, component.drawY)

    // don't need to test the null id component; behavior there is undefined.
  }
}
