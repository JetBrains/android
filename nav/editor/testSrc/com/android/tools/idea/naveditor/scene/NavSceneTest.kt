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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.avdmanager.DeviceManagerConnection
import com.android.tools.idea.common.model.Coordinates.getSwingRectDip
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.GuiInputHandler
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.model.popUpTo
import com.android.tools.idea.naveditor.scene.draw.PreviewType
import com.android.tools.idea.naveditor.scene.draw.verifyDrawAction
import com.android.tools.idea.naveditor.scene.draw.verifyDrawActionHandle
import com.android.tools.idea.naveditor.scene.draw.verifyDrawActionHandleDrag
import com.android.tools.idea.naveditor.scene.draw.verifyDrawActivity
import com.android.tools.idea.naveditor.scene.draw.verifyDrawEmptyDesigner
import com.android.tools.idea.naveditor.scene.draw.verifyDrawFragment
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHeader
import com.android.tools.idea.naveditor.scene.draw.verifyDrawHorizontalAction
import com.android.tools.idea.naveditor.scene.draw.verifyDrawNestedGraph
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.model.createChild
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.mock
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

/**
 * Tests for the nav editor Scene.
 */
@RunsInEdt
class NavSceneTest {

  @get:Rule
  val edtRule = EdtRule()

  private val projectRule = AndroidProjectRule.withSdk()
  private val navEditorRule = NavEditorRule(projectRule)

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(navEditorRule)

  @Test
  fun testDisplayList() {
    val model = navEditorRule.model("nav.xml") {
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

    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(490f, 400f, 76.5f, 128f), 0.5, previewType = PreviewType.IMAGE)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(580f, 389f, 76.5f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(580f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "activity")
      verifyDrawActivity(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f),
                         Rectangle2D.Float(404.0f, 404.0f, 68.5f, 111.0f),
                         0.5, FRAME_COLOR, 1f, FRAME_COLOR)
    }
  }

  @Test
  fun testInclude() {
    val model = navEditorRule.model("nav2.xml") {
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

    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nav")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "navigation.xml", FRAME_COLOR)
    }
  }

  @Test
  fun testNegativePositions() {
    val model = navEditorRule.model("nav.xml") {
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

  @Test
  fun testVeryPositivePositions() {
    val model = navEditorRule.model("nav.xml") {
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

  @Test
  fun testAddComponent() {
    lateinit var root: NavModelBuilderUtil.NavigationComponentDescriptor

    val modelBuilder = navEditorRule.modelBuilder("nav.xml") {
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

  @Test
  fun testRemoveComponent() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2")
      }
    }
    val editor = TestNavEditor(model.virtualFile, projectRule.project)

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

    val undoManager = UndoManager.getInstance(projectRule.project)
    undoManager.undo(editor)
    PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)
    model.surface.sceneManager!!.update()
    scene.layout(0, sceneView.context)

    component2 = scene.getSceneComponent("fragment2")!!
    assertDrawRectEquals(sceneView, component1, 490f, 400f, 76.5f, 128f)
    assertDrawRectEquals(sceneView, component2, 400f, 400f, 76.5f, 128f)
  }

  @Test
  fun testNestedGraph() {
    val model = navEditorRule.model("nav.xml") {
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

    whenever<NlComponent>(surface.currentNavigation).then { model.find("nested")!! }
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

  @Test
  fun testNonexistentLayout() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_nonexistent")
      }
    }
    val scene = model.surface.scene!!

    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, previewType = PreviewType.UNAVAILABLE)
    }
  }

  @Test
  fun testSelectedNlComponentSelectedInScene() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "nested")
          action("action2", destination = "activity")
        }
      }
    }
    val surface = model.surface
    val rootComponent = model.components[0]
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      surface.model!!
      val newComponent = rootComponent.createChild("fragment", true, null, null)!!
      surface.selectionModel.setSelection(ImmutableList.of(newComponent))
      newComponent.assignId("myId")
    }
    val manager = NavSceneManager(model, model.surface as NavDesignSurface)
    manager.update()
    val scene = manager.scene

    assertThat(scene.getSceneComponent("myId")!!.isSelected).isTrue()
  }

  @Test
  fun testSelfAction() {
    val model = navEditorRule.model("nav.xml") {
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

    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, previewType = PreviewType.IMAGE)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nav1")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)
    }
  }

  @Test
  fun testDeepLinks() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          deeplink("deepLink", "https://www.android.com/")
        }
      }
    }
    val scene = model.surface.scene!!
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5,
                       "fragment1", isStart = true, hasDeepLink = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, previewType = PreviewType.IMAGE)
    }
  }

  @Test
  fun testSelectedComponent() {
    val model = navEditorRule.model("nav.xml") {
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
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, SELECTED_COLOR)
    }

    // now "nested" is in the front
    val nested = model.find("nested")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(nested))
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            SELECTED_COLOR, 2f, "Nested Graph", SELECTED_COLOR)
      verifyDrawActionHandle(inOrder, g, Point2D.Float(560f, 409.5f), 0f, 0f, SELECTED_COLOR, HANDLE_COLOR)
    }

    // test multi select
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!, nested))
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1", isStart = true)
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, SELECTED_COLOR)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            SELECTED_COLOR, 2f, "Nested Graph", SELECTED_COLOR)
      verifyDrawActionHandle(inOrder, g, Point2D.Float(560f, 409.5f), 3.5f, 2.5f, SELECTED_COLOR, HANDLE_COLOR)
    }
  }

  @Test
  fun testHoveredComponent() {
    val model = navEditorRule.model("nav.xml") {
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

    val transform = scene.sceneManager.sceneViews.first().context
    scene.layout(0, transform)
    scene.mouseHover(transform, 150, 30, 0)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, FRAME_COLOR)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, ACTION_COLOR)
      verifyDrawActionHandle(inOrder, g, Point2D.Float(478.5f, 464f), 0f, 0f, FRAME_COLOR, HANDLE_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)
    }

    scene.mouseHover(transform, 552, 440, 0)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, ACTION_COLOR)
      verifyDrawActionHandle(inOrder, g, Point2D.Float(478.5f, 464f), 3.5f, 2.5f, FRAME_COLOR, HANDLE_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)
    }

    scene.mouseHover(transform, 120, 148, 0)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, FRAME_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 70f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)
    }
  }

  @Test
  fun testHoveredHandle() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 20, 20)
    scene.sceneManager.layout(false)

    val transform = scene.sceneManager.sceneViews.first().context
    scene.layout(0, transform)

    // If rectangle extends from (20, 20) to (173, 276), then the handle should be at (173, 148)
    // Hover over a point to the right of that so that we're over the handle but not the rectangle
    scene.mouseHover(transform, 177, 148, 0)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, FRAME_COLOR)
      verifyDrawActionHandle(inOrder, g, Point2D.Float(478.5f, 464f), 0f, 0f, FRAME_COLOR, HANDLE_COLOR)
    }
  }

  @Test
  fun testHoverDuringDrag() {
    val model = navEditorRule.model("nav.xml") {
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

    val sceneContext = scene.sceneManager.sceneViews.first().context

    scene.layout(0, sceneContext)

    val guiInputHandler = mock(GuiInputHandler::class.java)
    whenever(guiInputHandler.isInteractionInProgress).thenReturn(true)
    whenever(surface.guiInputHandler).thenReturn(guiInputHandler)

    val drawRect1 = scene.getSceneComponent("fragment1")!!
    scene.mouseDown(sceneContext, drawRect1.drawX + drawRect1.drawWidth, drawRect1.centerY, 0)

    val drawRect2 = scene.getSceneComponent("nested")!!
    scene.mouseDrag(sceneContext, drawRect2.centerX, drawRect2.centerY, 0)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 76.5f, 11f), 0.5, "nested")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(490f, 400f, 70f, 19f), 0.5,
                            SELECTED_COLOR, 2f, "Nested Graph", FRAME_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5, SELECTED_COLOR)
      verifyDrawHorizontalAction(inOrder, g, Rectangle2D.Float(384f, 461f, 12f, 6f), 0.5, ACTION_COLOR)
      verifyDrawActionHandleDrag(inOrder, g, Point2D.Float(478.5f, 464f), 0f, 2.5f, -1, -1)
    }
  }

  // TODO: this should test the different "Simulated Layouts", once that's implemented.
  fun disabledTestDevices() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
      }
    }
    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    list.clear()
    model.configuration
      .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    list.clear()
    model.configuration.setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, scene.sceneManager.sceneViews.first().context)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
  }

  @Test
  fun testGlobalActions() {
    val model = navEditorRule.model("nav.xml") {
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

  @Test
  fun testPopToDestination() {
    val model = navEditorRule.model("nav.xml") {
      navigation {
        fragment("fragment1")
        fragment("fragment2") {
          action("a", popUpTo = "fragment1")
        }
      }
    }
    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR, isPopAction = true)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(490f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(490f, 400f, 76.5f, 128f), 0.5)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment2")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
    }
  }

  @Test
  fun testExitActions() {
    val model = navEditorRule.model("nav.xml") {
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
    whenever<NlComponent>(surface.currentNavigation).then { model.find("nav1")!! }

    val scene = surface.scene!!
    scene.sceneManager.update()

    moveComponentTo(scene.getSceneComponent("fragment2")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment4")!!, 20, 260)
    moveComponentTo(scene.getSceneComponent("fragment5")!!, 200, 320)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 20, 20)
    scene.sceneManager.layout(false)

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

  @Test
  fun testHoverMarksComponent() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.focusedSceneView!!
    whenever(view.scale).thenReturn(1.0)
    val transform = view.context
    val fragment1 = scene.getSceneComponent("fragment1")!!
    fragment1.setPosition(100, 100)
    fragment1.setSize(100, 100)
    fragment1.layout(transform, 0)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(1000, 1000)
    fragment2.setSize(100, 100)
    fragment2.layout(transform, 0)

    assertThat(fragment1.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    assertThat(fragment2.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    var version = scene.displayListVersion

    scene.mouseHover(transform, 150, 150, 0)
    assertThat(fragment1.drawState).isEqualTo(SceneComponent.DrawState.HOVER)
    assertThat(fragment2.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    assertThat(version).isLessThan(scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 1050, 1050, 0)
    assertThat(fragment1.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    assertThat(fragment2.drawState).isEqualTo(SceneComponent.DrawState.HOVER)
    assertThat(version).isLessThan(scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 0, 0, 0)
    assertThat(fragment1.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    assertThat(fragment2.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)
    assertThat(version).isLessThan(scene.displayListVersion)
  }

  @Test
  fun testHoverGlobalAction() {
    val model = navEditorRule.model("nav.xml") {
      navigation("root") {
        action("a1", destination = "fragment1")
        fragment("fragment1")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.focusedSceneView!!
    whenever(view.scale).thenReturn(1.0)
    val transform = view.context
    val action1 = scene.getSceneComponent("a1")!!

    scene.mouseHover(transform, -8, 125, 0)
    assertThat(action1.drawState).isEqualTo(SceneComponent.DrawState.NORMAL)

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      action1.nlComponent.popUpTo = "fragment1"
    }

    scene.mouseHover(transform, -8, 125, 0)
    assertThat(action1.drawState).isEqualTo(SceneComponent.DrawState.HOVER)
  }

  @Test
  fun testRegularActions() {
    val model = navEditorRule.model("nav.xml") {
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

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 500, 20)
    moveComponentTo(scene.getSceneComponent("nav1")!!, 200, 500)
    moveComponentTo(scene.getSceneComponent("nav2")!!, 500, 500)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)
      verifyDrawAction(inOrder, g, ACTION_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(550f, 389f, 76.5f, 11f), 0.5, "fragment2")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(550f, 400f, 76.5f, 128f), 0.5)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 629f, 76.5f, 11f), 0.5, "nav1")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(400f, 640f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)

      verifyDrawHeader(inOrder, g, Rectangle2D.Float(550f, 629f, 76.5f, 11f), 0.5, "nav2")
      verifyDrawNestedGraph(inOrder, g, Rectangle2D.Float(550f, 640f, 70f, 19f), 0.5,
                            FRAME_COLOR, 1f, "Nested Graph", FRAME_COLOR)
    }
  }

  @Test
  fun testEmptyDesigner() {
    var root: NavModelBuilderUtil.NavigationComponentDescriptor? = null

    val modelBuilder = navEditorRule.modelBuilder("nav.xml") {
      navigation("root") {
        action("action1", destination = "root")
      }.also { root = it }
    }

    val model = modelBuilder.build()

    val surface = model.surface as NavDesignSurface
    val scene = surface.scene!!
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    val sceneManager = scene.sceneManager as NavSceneManager

    val expectedEmptyNavSceneClick = Point2D.Float(130f, 258f)
    assertThat(sceneManager.isEmpty).isTrue()
    verifyScene(model.surface) { inOrder, g ->
      verifyDrawEmptyDesigner(inOrder, g, expectedEmptyNavSceneClick)
    }

    root?.fragment("fragment1")

    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "fragment1")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
    }
    assertThat(sceneManager.isEmpty).isFalse()

    model.delete(listOf(model.find("fragment1")!!))
    scene.layout(0, scene.sceneManager.sceneViews.first().context)

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawEmptyDesigner(inOrder, g, expectedEmptyNavSceneClick)
    }
    assertThat(sceneManager.isEmpty).isTrue()
  }

  @Test
  fun testZoomIn() {
    zoomTest(3.0, 2400f, 2400f, 459f, 768f)
  }

  @Test
  fun testZoomOut() {
    zoomTest(0.25, 200f, 200f, 38.25f, 64f)
  }

  @Test
  fun testZoomToFit() {
    zoomTest(1.0, 800f, 800f, 153f, 256f)
  }

  private fun zoomTest(newScale: Double, x: Float, y: Float, width: Float, height: Float) {
    val model = navEditorRule.model("nav.xml") {
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

    whenever(surface.scale).thenReturn(newScale)
    scene.layout(0, SceneContext.get())

    component1 = scene.getSceneComponent("fragment1")!!
    assertDrawRectEquals(sceneView, component1, x, y, width, height)
  }

  @Test
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

    projectRule.fixture.addFileToProject(relativePath, fileText)

    val model = navEditorRule.model("nav.xml") {
      navigation {
        custom("customComponent")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())

    verifyScene(model.surface) { inOrder, g ->
      verifyDrawHeader(inOrder, g, Rectangle2D.Float(400f, 389f, 76.5f, 11f), 0.5, "customComponent")
      verifyDrawFragment(inOrder, g, Rectangle2D.Float(400f, 400f, 76.5f, 128f), 0.5)
    }
  }

  /**
   * Reposition a component. If we just set the position directly children't aren't updated.
   */
  private fun moveComponentTo(component: SceneComponent, x: Int, y: Int) {
    val dragTarget = component.targets.filterIsInstance(ScreenDragTarget::class.java).first()
    dragTarget.mouseDown(component.drawX, component.drawY)
    dragTarget.mouseDrag(x, y, listOf(), SceneContext.get())
    // the release position isn't used
    dragTarget.mouseRelease(x, y, listOf())
  }

  private fun assertDrawRectEquals(sceneView: SceneView, component: SceneComponent, x: Float, y: Float, width: Float, height: Float) {
    val drawRect = getSwingRectDip(sceneView, component.fillDrawRect2D(0, null))
    assertThat(drawRect).isEqualTo(Rectangle2D.Float(x, y, width, height))
  }
}