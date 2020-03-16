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
package com.android.tools.idea.naveditor.scene

import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.common.model.Coordinates.getSwingRectDip
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.geom.Rectangle2D

/**
 * Tests for the nav editor Scene.
 */
class NavSceneTest : NavTestCase() {
  fun testDisplayList() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "nested")
          action("action2", destination = "activity")
        }
        navigation("nested") {
          fragment("fragment2", layout = "activity_main2") {
            action("action3", destination = "activity")
          }
        }
        activity("activity")
      }
    }
    val scene = model.surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("nested")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("activity")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1050,928\n" +
      "DrawAction,580.0x400.0x70.0x19.0,400.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,490.0x400.0x76.5x128.0,580.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,490.0x400.0x76.5x128.0,400.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,490.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,580.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,580.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,activity,false,false\n" +
      "DrawActivity,400.0x400.0x76.5x128.0,404.0x404.0x68.5x111.0,0.5,ffa7a7a7,1.0,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testInclude() {
    val model = model("nav2.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("action1", destination = "nav")
        }
        include("navigation")
      }
    }
    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nav,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,navigation.xml,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testNegativePositions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main")
        fragment("fragment3", layout = "activity_main")
      }
    }

    val scene = model.surface.scene!!
    val sceneManager = scene.sceneManager as NavSceneManager
    val component1 = scene.getSceneComponent("fragment1")!!
    component1.setPosition(-100, -200)
    val component2 = scene.getSceneComponent("fragment2")!!
    component2.setPosition(-300, 0)
    val component3 = scene.getSceneComponent("fragment3")!!
    component3.setPosition(200, 200)
    sceneManager.save(listOf(component1, component2, component3))

    model.surface.sceneManager!!.update()
    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component1, 500f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 400f, 500f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component3, 650f, 600f, 76.5f, 128f)
  }

  fun testVeryPositivePositions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main")
        fragment("fragment2", layout = "activity_main")
        fragment("fragment3", layout = "activity_main")
      }
    }

    val scene = model.surface.scene!!
    val sceneManager: NavSceneManager = scene.sceneManager as NavSceneManager
    val component1 = scene.getSceneComponent("fragment1")!!
    component1.setPosition(1900, 1800)
    val component2 = scene.getSceneComponent("fragment2")!!
    component2.setPosition(1700, 2000)
    val component3 = scene.getSceneComponent("fragment3")!!
    component3.setPosition(2200, 2200)
    sceneManager.save(listOf(component1, component2, component3))

    model.surface.sceneManager!!.update()
    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component1, 500f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 400f, 500f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component3, 650f, 600f, 76.5f, 128f)
  }

  fun testAddComponent() {
    lateinit var root: NavModelBuilderUtil.NavigationComponentDescriptor

    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }.also { root = it }
    }
    val model = modelBuilder.build()

    val scene = model.surface.scene!!
    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    root.fragment("fragment3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    val component1 = scene.getSceneComponent("fragment1")!!
    moveComponentTo(component1, 200, 20)
    val component2 = scene.getSceneComponent("fragment2")!!
    moveComponentTo(component2, 20, 20)
    val component3 = scene.getSceneComponent("fragment3")!!
    moveComponentTo(component3, 380, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component1, 490f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 400f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component3, 580f, 400f, 76.5f, 128f)
  }

  fun testRemoveComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }
    }
    val editor = TestNavEditor(model.virtualFile, project)

    val scene = model.surface.scene!!
    val component1 = scene.getSceneComponent("fragment1")!!
    moveComponentTo(component1, 200, 20)
    var component2 = scene.getSceneComponent("fragment2")!!
    moveComponentTo(component2, 20, 20)
    scene.sceneManager.layout(false)

    model.delete(listOf(model.find("fragment2")!!))

    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component1, 400f, 400f, 76.5f, 128f)
    assertThat(scene.getSceneComponent("fragment2")).isNull()

    val undoManager = UndoManager.getInstance(project)
    undoManager.undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)
    model.surface.sceneManager!!.update()
    scene.layout(0, sceneView.context)

    component2 = scene.getSceneComponent("fragment2")!!
    assertDrawRectEquals(sceneView, component1, 490f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 400f, 400f, 76.5f, 128f)
  }

  fun testNestedGraph() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2") {
          action("action2", destination = "fragment3")
        }
        navigation("nested") {
          fragment("fragment3") {
            action("action3", destination = "fragment4")
          }
          fragment("fragment4") {
            action("action4", destination = "fragment1")
          }
        }
      }
    }

    val scene = model.surface.scene!!
    val component1 = scene.getSceneComponent("fragment1")!!
    moveComponentTo(component1, 200, 20)
    val component2 = scene.getSceneComponent("fragment2")!!
    moveComponentTo(component2, 380, 20)
    val nested = scene.getSceneComponent("nested")!!
    moveComponentTo(nested, 20, 20)
    scene.sceneManager.layout(false)

    val surface = model.surface as NavDesignSurface
    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component1, 490f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 580f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, nested, 400f, 400f, 70f, 19f)

    `when`<NlComponent>(surface.currentNavigation).then { model.find("nested")!! }
    scene.sceneManager.update()
    val component3 = scene.getSceneComponent("fragment3")!!
    moveComponentTo(component3, 200, 20)
    val component4 = scene.getSceneComponent("fragment4")!!
    moveComponentTo(component4, 20, 20)

    scene.sceneManager.layout(false)
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, component3, 490f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component4, 400f, 400f, 76.5f, 128f)
  }

  fun testNonexistentLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_nonexistent")
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,877,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testSelectedNlComponentSelectedInScene() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "nested")
          action("action2", destination = "activity")
        }
      }
    }
    val surface = model.surface
    val rootComponent = model.components[0]
    WriteCommandAction.runWriteCommandAction(project) {
      surface.model!!
      val newComponent = rootComponent.createChild("fragment", true, null, null, surface)!!
      surface.selectionModel.setSelection(ImmutableList.of(newComponent))
      newComponent.assignId("myId")
    }
    val manager = NavSceneManager(model, model.surface as NavDesignSurface)
    manager.update()
    val scene = manager.scene

    assertTrue(scene.getSceneComponent("myId")!!.isSelected)
  }

  fun testSelfAction() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment1")
        }
        navigation("nav1") {
          action("action2", destination = "nav1")
        }
      }
    }
    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nav1")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawSelfAction,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawSelfAction,400.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nav1,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testDeepLinks() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          deeplink("deepLink", "https://www.android.com/")
        }
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,877,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,true,true\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testSelectedComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        action("a1", destination = "fragment1")
        fragment("fragment1")
        navigation("nested")
      }
    }
    val scene = model.surface.scene!!

    // Selecting global nav brings it to the front
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("a1")!!))

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nested")!!, 320, 20)

    scene.sceneManager.layout(false)
    var list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    val view = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,ff1886f7,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    // now "nested" is in the front
    val nested = model.find("nested")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(nested))
    list.clear()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ff1886f7,2.0,Nested Graph,ff1886f7\n" +
      "DrawActionHandle,560.0x409.5,0.0,3.5,0.0,2.5,127,ff1886f7,fff5f5f5\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    // test multi select
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!, nested))

    list = DisplayList()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,true,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,ff1886f7\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ff1886f7,2.0,Nested Graph,ff1886f7\n" +
      "DrawActionHandle,560.0x409.5,3.5,0.0,2.5,0.0,127,ff1886f7,fff5f5f5\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testHoveredComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "nested")
        }
        navigation("nested")
        action("a2", destination = "fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nested")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val transform = scene.sceneManager.sceneViews.first().context
    scene.layout(0, transform)
    scene.mouseHover(transform, 150, 30, 0)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,ffa7a7a7\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawActionHandle,478.5x464.0,0.0,3.5,0.0,2.5,127,ffa7a7a7,fff5f5f5\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    scene.mouseHover(transform, 552, 440, 0)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawActionHandle,478.5x464.0,3.5,0.0,2.5,0.0,127,ffa7a7a7,fff5f5f5\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    scene.mouseHover(transform, 120, 148, 0)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,ffa7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testHoveredHandle() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val transform = scene.sceneManager.sceneViews.first().context
    scene.layout(0, transform)

    // If rectangle extends from (20, 20) to (173, 276), then the handle should be at (173, 148)
    // Hover over a point to the right of that so that we're over the handle but not the rectangle
    scene.mouseHover(transform, 177, 148, 0)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,877,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,ffa7a7a7\n" +
      "DrawActionHandle,478.5x464.0,0.0,5.5,0.0,4.0,200,ffa7a7a7,fff5f5f5\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testHoverDuringDrag() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "nested")
        }
        navigation("nested")
        action("a2", destination = "fragment1")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("nested")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val sceneContext = scene.sceneManager.sceneViews.first().context

    scene.layout(0, sceneContext)

    val interactionManager = mock(InteractionManager::class.java)
    `when`(interactionManager.isInteractionInProgress).thenReturn(true)
    `when`(surface.interactionManager).thenReturn(interactionManager)

    val drawRect1 = scene.getSceneComponent("fragment1")!!
    scene.mouseDown(sceneContext, drawRect1.drawX + drawRect1.drawWidth, drawRect1.centerY, 0)

    val drawRect2 = scene.getSceneComponent("nested")!!
    scene.mouseDrag(sceneContext, drawRect2.centerX, drawRect2.centerY, 0)

    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x70.0x11.0,0.5,nested,false,false\n" +
      "DrawNestedGraph,490.0x400.0x70.0x19.0,0.5,ff1886f7,2.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,ff1886f7\n" +
      "DrawHorizontalAction,384.0x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawActionHandleDrag,478.5x464.0,0.0,3.5,2.5,127\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  // TODO: this should test the different "Simulated Layouts", once that's implemented.
  fun disabledTestDevices() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }
    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,977,1028\n" +
      "DrawRoundRectangle,450x450x77x128,FRAMES,1,0\n" +
      "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
      "DrawScreenLabel,450,445,fragment1\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    list.clear()
    model.configuration
      .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,914,914\n" +
      "DrawRoundRectangle,425x425x64x64,FRAMES,1,0\n" +
      "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,425x415x64x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    list.clear()
    model.configuration.setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1028,972\n" +
      "DrawRoundRectangle,450x450x128x72,FRAMES,1,0\n" +
      "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,450x440x128x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testGlobalActions() {
    val model = model("nav.xml") {
      navigation("root") {
        action("action1", destination = "fragment1")
        action("action2", destination = "fragment2")
        action("action3", destination = "fragment2")
        action("action4", destination = "fragment3")
        action("action5", destination = "fragment3")
        action("action6", destination = "fragment3")
        action("action7", destination = "invalid")
        fragment("fragment1")
        fragment("fragment2") {
          action("action8", destination = "fragment3")
          action("action9", destination = "fragment2")
        }
        fragment("fragment3")
      }
    }

    val scene = model.surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 20, 20)
    scene.sceneManager.layout(false)

    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, scene.getSceneComponent("action1")!!, 474f, 461f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action2")!!, 564f, 452f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action3")!!, 564f, 461f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action4")!!, 384f, 443f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action5")!!, 384f, 452f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action6")!!, 384f, 470f, 12f, 6f)
    assertThat(scene.getSceneComponent("action7")).isNull()
  }

  fun testPopToDestination() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2") {
          action("a", popUpTo = "fragment1")
        }
      }
    }
    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,967,928\n" +
      "DrawAction,400.0x400.0x76.5x128.0,490.0x400.0x76.5x128.0,0.5,b2a7a7a7,true\n" +
      "\n" +
      "DrawHeader,490.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,490.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment2,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testExitActions() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1")
        navigation("nav1") {
          fragment("fragment2") {
            action("action1", destination = "fragment1")
          }
          fragment("fragment3") {
            action("action2", destination = "fragment1")
            action("action3", destination = "fragment1")
          }
          fragment("fragment4") {
            action("action4", destination = "fragment1")
            action("action5", destination = "fragment1")
            action("action6", destination = "fragment1")
            action("action7", destination = "fragment2")
          }
          fragment("fragment5") {
            action("action8", destination = "fragment1")
            action("action9", destination = "fragment5")
          }
          navigation("nav2") {
            action("action9", destination = "root")
          }
        }
      }
    }

    val surface = model.surface as NavDesignSurface
    `when`<NlComponent>(surface.currentNavigation).then { model.find("nav1")!! }

    val scene = surface.scene!!
    scene.sceneManager.update()

    moveComponentTo(scene.getSceneComponent("fragment2")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment4")!!, 20, 260)
    moveComponentTo(scene.getSceneComponent("fragment5")!!, 200, 320)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 20, 20)
    scene.sceneManager.layout(false)

    val view = NavView(surface, surface.sceneManager!!)
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    val list = DisplayList()
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,1057,1078\n" +
      "DrawAction,400.0x520.0x76.5x128.0,490.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x76.5x11.0,0.5,fragment2,false,false\n" +
      "DrawFragment,490.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,570.5x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,580.0x389.0x76.5x11.0,0.5,fragment3,false,false\n" +
      "DrawFragment,580.0x400.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,660.5x452.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawHorizontalAction,660.5x461.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x509.0x76.5x11.0,0.5,fragment4,false,false\n" +
      "DrawFragment,400.0x520.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,480.5x563.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawHorizontalAction,480.5x572.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "DrawHorizontalAction,480.5x590.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawSelfAction,490.0x550.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "DrawHeader,490.0x539.0x76.5x11.0,0.5,fragment5,false,false\n" +
      "DrawFragment,490.0x550.0x76.5x128.0,0.5,null\n" +
      "DrawHorizontalAction,570.5x602.0x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,400.0x389.0x70.0x11.0,0.5,nav2,false,false\n" +
      "DrawNestedGraph,400.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "DrawHorizontalAction,474.0x406.5x12.0x6.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )

    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, sceneView.context)

    assertDrawRectEquals(sceneView, scene.getSceneComponent("action1")!!, 570.5f, 461f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action2")!!, 660.5f, 452f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action3")!!, 660.5f, 461f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action4")!!, 480.5f, 563f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action5")!!, 480.5f, 572f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action6")!!, 480.5f, 590f, 12f, 6f)
    assertDrawRectEquals(sceneView, scene.getSceneComponent("action8")!!, 570.5f, 602f, 12f, 6f)
  }

  fun testHoverMarksComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.focusedSceneView!!
    `when`(view.scale).thenReturn(1.0)
    val transform = view.context
    val fragment1 = scene.getSceneComponent("fragment1")!!
    fragment1.setPosition(100, 100)
    fragment1.setSize(100, 100)
    fragment1.layout(transform, 0)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(1000, 1000)
    fragment2.setSize(100, 100)
    fragment2.layout(transform, 0)

    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    var version = scene.displayListVersion

    scene.mouseHover(transform, 150, 150, 0)
    assertEquals(SceneComponent.DrawState.HOVER, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 1050, 1050, 0)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.HOVER, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 0, 0, 0)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
  }

  fun testHoverGlobalAction() {
    val model = model("nav.xml") {
      navigation("root") {
        action("a1", destination = "fragment1")
        fragment("fragment1")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.focusedSceneView!!
    `when`(view.scale).thenReturn(1.0)
    val transform = view.context
    val action1 = scene.getSceneComponent("a1")!!

    scene.mouseHover(transform, -8, 125, 0)
    assertEquals(SceneComponent.DrawState.NORMAL, action1.drawState)

    WriteCommandAction.runWriteCommandAction(project) {
      action1.nlComponent.popUpTo = "fragment1"
    }

    scene.mouseHover(transform, -8, 125, 0)
    assertEquals(SceneComponent.DrawState.HOVER, action1.drawState)
  }

  fun testRegularActions() {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1") {
          action("a1", destination = "fragment2")
          action("a2", destination = "nav1")
        }
        fragment("fragment2")
        navigation("nav1") {
          action("a3", destination = "fragment1")
          action("a4", destination = "nav2")
        }
        navigation("nav2")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("nav1")!!, 20, 80)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1057,928\n" +
      "DrawAction,400.0x430.0x70.0x19.0,490.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,400.0x430.0x70.0x19.0,400.0x400.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,490.0x400.0x76.5x128.0,580.0x400.0x76.5x128.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawAction,490.0x400.0x76.5x128.0,400.0x430.0x70.0x19.0,0.5,b2a7a7a7,false\n" +
      "\n" +
      "DrawHeader,490.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,490.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,580.0x389.0x76.5x11.0,0.5,fragment2,false,false\n" +
      "DrawFragment,580.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "DrawHeader,400.0x419.0x70.0x11.0,0.5,nav1,false,false\n" +
      "DrawNestedGraph,400.0x430.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "DrawHeader,400.0x389.0x70.0x11.0,0.5,nav2,false,false\n" +
      "DrawNestedGraph,400.0x400.0x70.0x19.0,0.5,ffa7a7a7,1.0,Nested Graph,ffa7a7a7\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  fun testEmptyDesigner() {
    var root: NavModelBuilderUtil.NavigationComponentDescriptor? = null

    val modelBuilder = modelBuilder("nav.xml") {
      navigation("root") {
        action("action1", destination = "root")
      }.also { root = it }
    }

    val model = modelBuilder.build()

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    val list = DisplayList()
    val sceneManager = scene.sceneManager as NavSceneManager
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130.0x251.0\n", list.generateSortedDisplayList()
    )
    assertTrue(sceneManager.isEmpty)

    root?.fragment("fragment1")

    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "Clip,0,0,877,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,fragment1,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
    assertFalse(sceneManager.isEmpty)

    model.delete(listOf(model.find("fragment1")!!))
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130.0x251.0\n", list.generateSortedDisplayList()
    )
    assertTrue(sceneManager.isEmpty)
  }

  fun testZoomIn() {
    zoomTest(3.0, 2400f, 2400f, 459f, 768f)
  }

  fun testZoomOut() {
    zoomTest(0.25, 200f, 200f, 38.25f, 64f)
  }

  fun testZoomToFit() {
    zoomTest(1.0, 800f, 800f, 153f, 256f)
  }

  private fun zoomTest(newScale: Double, x: Float, y: Float, width: Float, height: Float) {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    val sceneView = scene.sceneManager.sceneViews.first()
    scene.layout(0, SceneContext.get())

    var component1 = scene.getSceneComponent("fragment1")!!
    assertDrawRectEquals(sceneView, component1, 400f, 400f, 76.5f, 128f)

    `when`(surface.scale).thenReturn(newScale)
    scene.layout(0, SceneContext.get())

    component1 = scene.getSceneComponent("fragment1")!!
    assertDrawRectEquals(sceneView, component1, x, y, width, height)
  }

  fun testCustomDestination() {
    val relativePath = "src/mytest/navtest/MyTestNavigator.java"
    val fileText = """
      package myTest.navtest;
      import androidx.navigation.NavDestination;
      import androidx.navigation.Navigator;
      @Navigator.Name("customComponent")
      public class TestNavigator extends Navigator<TestNavigator.Destination> {
          public static class Destination extends NavDestination {}
      }
      """

    myFixture.addFileToProject(relativePath, fileText)

    val model = model("nav.xml") {
      navigation {
        custom("customComponent")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())

    val list = DisplayList()
    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,877,928\n" +
      "DrawHeader,400.0x389.0x76.5x11.0,0.5,customComponent,false,false\n" +
      "DrawFragment,400.0x400.0x76.5x128.0,0.5,null\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList()
    )
  }

  /**
   * Reposition a component. If we just set the position directly children't aren't updated.
   */
  private fun moveComponentTo(component: SceneComponent, x: Int, y: Int) {
    val dragTarget = component.targets.filterIsInstance(ScreenDragTarget::class.java).first()
    dragTarget.mouseDown(component.drawX, component.drawY)
    dragTarget.mouseDrag(x, y, listOf())
    // the release position isn't used
    dragTarget.mouseRelease(x, y, listOf())
  }

  private fun assertDrawRectEquals(sceneView: SceneView, component: SceneComponent, x: Float, y: Float, width: Float, height: Float) {
    val drawRect = getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    assertThat(drawRect).isEqualTo(Rectangle2D.Float(x, y, width, height))
  }
}