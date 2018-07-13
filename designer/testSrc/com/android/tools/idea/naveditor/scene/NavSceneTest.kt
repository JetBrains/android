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
import com.android.tools.idea.common.api.InsertType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNlEditor
import com.android.tools.idea.naveditor.scene.targets.ScreenDragTarget
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.psi.PsiDocumentManager
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Tests for the nav editor Scene.
 */
class NavSceneTest : NavTestCase() {
  fun testDisplayList() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "subnav")
          action("action2", destination = "activity")
        }
        navigation("subnav") {
          fragment("fragment2", layout = "activity_main2") {
            action("action3", destination = "activity")
          }
        }
        activity("activity")
      }
    }
    val scene = model.surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("activity")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1050,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,580x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,612x423x6x5,b2a7a7a7\n" +
      "DrawAction,NORMAL,490x400x76x128,400x389x76x139,NORMAL\n" +
      "DrawArrow,2,UP,435x531x6x5,b2a7a7a7\n" +
      "DrawIcon,490x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,498x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,580x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,580x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,580x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,EXIT,580x400x70x19,400x389x76x139,NORMAL\n" +
      "DrawArrow,2,DOWN,435x380x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,subnav,580x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,400x400x76x128,fffafafa,6\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,6\n" +
      "DrawNavScreen,404x404x68x111\n" +
      "DrawRectangle,5,404x404x68x111,ffa7a7a7,1,0\n" +
      "DrawTruncatedText,3,Activity,400x515x76x13,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,activity,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawAction,NORMAL,400x400x76x128,490x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,522x423x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,navigation.xml,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1126,1128\n" +
      "DrawRectangle,1,500x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,501x401x74x126\n" +
      "DrawIcon,500x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x500x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x501x74x126\n" +
      "DrawTruncatedText,3,fragment2,400x490x77x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,650x600x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,651x601x74x126\n" +
      "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
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

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1126,1128\n" +
      "DrawRectangle,1,500x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,501x401x74x126\n" +
      "DrawIcon,500x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x500x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x501x74x126\n" +
      "DrawTruncatedText,3,fragment2,400x490x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,650x600x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,651x601x74x126\n" +
      "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
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

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    root.fragment("fragment3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 380, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,400x389x78x139,NORMAL\n" +
      "DrawArrow,2,UP,436x531x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,581x401x74x126\n" +
      "DrawTruncatedText,3,fragment3,580x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
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
    val editor = TestNlEditor(model.virtualFile, project)

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 20, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    model.delete(listOf(model.find("fragment2")!!))

    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477x464,484x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484x461x5x6,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    val undoManager = UndoManager.getInstance(project)
    undoManager.undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)
    model.surface.sceneManager!!.update()
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,400x389x78x139,NORMAL\n" +
      "DrawArrow,2,UP,436x531x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSubflow() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout = "activity_main2") {
          action("action2", destination = "fragment3")
        }
        navigation("subnav") {
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
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 20, 20)
    scene.sceneManager.layout(false)

    val surface = model.surface as NavDesignSurface

    val view = NavView(surface, scene.sceneManager)
    scene.layout(0, SceneContext.get(view))

    val list = DisplayList()
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,580x389x78x139,NORMAL\n" +
      "DrawArrow,2,RIGHT,571x455x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,581x401x74x126\n" +
      "DrawIcon,580x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment2,588x390x68x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,657x464,664x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664x461x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawFilledRectangle,1,400x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,400x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,400x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,EXIT,400x400x70x19,490x389x78x139,NORMAL\n" +
      "DrawArrow,2,RIGHT,481x455x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,subnav,400x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()

    `when`<NlComponent>(surface.currentNavigation).then { model.find("subnav")!! }
    scene.sceneManager.update()
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment4")!!, 20, 20)
    scene.sceneManager.layout(false)


    scene.layout(0, SceneContext.get(view))
    scene.buildDisplayList(list, 0, view)
    assertEquals(
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,400x389x78x139,NORMAL\n" +
      "DrawArrow,2,UP,436x531x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment3,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment4,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477x464,484x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484x461x5x6,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testNonexistentLayout() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1", layout = "activity_nonexistent")
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSelectedNlComponentSelectedInScene() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "subnav")
          action("action2", destination = "activity")
        }
      }
    }
    val surface = model.surface
    val rootComponent = model.components[0]
    object : WriteCommandAction<Any?>(project, "Add") {
      override fun run(result: Result<Any?>) {
        val tag = rootComponent.tag.createChildTag("fragment", null, null, true)
        val newComponent = surface.model!!.createComponent(surface, tag, rootComponent, null, InsertType.CREATE)
        surface.selectionModel.setSelection(ImmutableList.of(newComponent))
        newComponent.assignId("myId")
      }
    }.execute()
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
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawArrow,2,UP,459x531x6x5,b2a7a7a7\n" +
      "DrawSelfAction,476x464,462x535,b2a7a7a7\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawArrow,2,UP,541x423x6x5,b2a7a7a7\n" +
      "DrawSelfAction,560x409,544x427,b2a7a7a7\n" +
      "DrawTruncatedText,3,nav1,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testDeepLinks() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          deeplink("https://www.android.com/")
        }
      }
    }
    val scene = model.surface.scene!!

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawIcon,469x389x7x7,DEEPLINK\n" +
      "DrawTruncatedText,3,fragment1,408x390x60x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testSelectedComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        action("a1", destination = "fragment1")
        fragment("fragment1")
        navigation("subnav")
      }
    }
    val scene = model.surface.scene!!

    // Selecting global nav brings it to the front
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("a1")!!))

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)

    scene.sceneManager.layout(false)
    var list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    val view = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    scene.buildDisplayList(list, 0, view)
    val context = SceneContext.get(view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawLine,2,387x464,391x464,ff1886f7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,ff1886f7\n" +
      "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )

    // now "subnav" is in the front
    val subnav = model.find("subnav")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(subnav))
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ff1886f7,2,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ff1886f7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "DrawFilledCircle,6,560x409,fff5f5f5,0:3:54\n" +
      "DrawCircle,7,560x409,ff1886f7,2,0:2:54\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )

    // test multi select
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!, subnav))

    list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawRectangle,1,398x398x80x132,ff1886f7,2,2\n" +
      "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
      "DrawIcon,400x389x7x7,START_DESTINATION\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ff1886f7,2,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ff1886f7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "DrawFilledCircle,6,560x409,fff5f5f5,3:0:54\n" +
      "DrawCircle,7,560x409,ff1886f7,2,2:0:54\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(context)
    )
  }

  fun testHoveredComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "subnav")
        }
        navigation("subnav")
        action("a2", destination = "fragment1")
      }
    }

    val scene = model.surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val transform = SceneContext.get(model.surface.currentSceneView)
    scene.layout(0, transform)
    scene.mouseHover(transform, 150, 30)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawRectangle,1,398x398x80x132,ffa7a7a7,2,2\n" +
      "DrawAction,NORMAL,400x400x76x128,490x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,522x423x6x5,b2a7a7a7\n" +
      "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawFilledCircle,6,478x464,fff5f5f5,0:3:54\n" +
      "DrawCircle,7,478x464,ffa7a7a7,2,0:2:54\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )

    scene.mouseHover(transform, 552, 440)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawAction,NORMAL,400x400x76x128,490x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,522x423x6x5,b2a7a7a7\n" +
      "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawFilledCircle,6,478x464,fff5f5f5,3:0:54\n" +
      "DrawCircle,7,478x464,ffa7a7a7,2,2:0:54\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )

    scene.mouseHover(transform, 120, 148)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,960,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawAction,NORMAL,400x400x76x128,490x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,522x423x6x5,b2a7a7a7\n" +
      "DrawLine,2,387x464,391x464,ffa7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x461x5x6,ffa7a7a7\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "\n" +
      "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,490x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
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
    val transform = SceneContext.get(model.surface.currentSceneView)
    scene.layout(0, transform)

    // If rectangle extends from (20, 20) to (173, 276), then the handle should be at (173, 148)
    // Hover over a point to the right of that so that we're over the handle but not the rectangle
    scene.mouseHover(transform, 177, 148)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawRectangle,1,398x398x80x132,ffa7a7a7,2,2\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawFilledCircle,6,478x464,fff5f5f5,0:5:90\n" +
      "DrawCircle,7,478x464,ffa7a7a7,2,0:4:90\n" +
      "\n" +
      "UNClip\n", list.generateSortedDisplayList(transform)
    )
  }

  fun testHoverDuringDrag() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1") {
          action("a1", destination = "subnav")
        }
        navigation("subnav")
        action("a2", destination = "fragment1")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    moveComponentTo(scene.getSceneComponent("fragment1")!!, 140, 20)
    moveComponentTo(scene.getSceneComponent("subnav")!!, 320, 20)
    scene.sceneManager.layout(false)

    val list = DisplayList()
    val sceneContext = SceneContext.get(surface.currentSceneView)

    scene.layout(0, sceneContext)

    val interactionManager = mock(InteractionManager::class.java)
    `when`(interactionManager.isInteractionInProgress).thenReturn(true)
    `when`(surface.interactionManager).thenReturn(interactionManager)

    val drawRect1 = scene.getSceneComponent("fragment1")!!
    scene.mouseDown(sceneContext, drawRect1.drawX + drawRect1.drawWidth, drawRect1.centerY)

    val drawRect2 = scene.getSceneComponent("subnav")!!
    scene.mouseDrag(sceneContext, drawRect2.centerX, drawRect2.centerY)

    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
        "Clip,0,0,960,928\n" +
        "DrawFilledRectangle,1,490x400x70x19,fffafafa,6\n" +
        "DrawRectangle,1,490x400x70x19,ff1886f7,2,6\n" +
        "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawRectangle,1,398x398x80x132,ff1886f7,2,2\n" +
        "DrawAction,NORMAL,400x400x76x128,490x389x70x30,NORMAL\n" +
        "DrawArrow,2,UP,522x423x6x5,b2a7a7a7\n" +
        "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawNavScreen,401x401x74x126\n" +
        "DrawFilledCircle,6,478x464,fff5f5f5,0:3:54\n" +
        "DrawFilledCircle,7,478x464,ff1886f7,2:2:0\n" +
        "DrawActionHandleDrag,478,464\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(sceneContext)
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
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,977,1028\n" +
      "DrawRectangle,450x450x77x128,FRAMES,1,0\n" +
      "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
      "DrawScreenLabel,450,445,fragment1\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()
    model.configuration
      .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,914,914\n" +
      "DrawRectangle,425x425x64x64,FRAMES,1,0\n" +
      "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,425x415x64x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()
    model.configuration.setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1028,972\n" +
      "DrawRectangle,450x450x128x72,FRAMES,1,0\n" +
      "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
      "DrawTruncatedText,3,fragment1,450x440x128x5,SUBDUED_TEXT,0,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!

    moveComponentTo(scene.getSceneComponent("fragment1")!!, 200, 20)
    moveComponentTo(scene.getSceneComponent("fragment2")!!, 380, 20)
    moveComponentTo(scene.getSceneComponent("fragment3")!!, 20, 20)
    scene.sceneManager.layout(false)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477x464,481x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,481x461x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,580x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,581x401x74x126\n" +
      "DrawAction,NORMAL,580x400x76x128,400x389x78x139,NORMAL\n" +
      "DrawArrow,2,UP,436x531x6x5,b2a7a7a7\n" +
      "DrawArrow,2,UP,639x531x6x5,b2a7a7a7\n" +
      "DrawSelfAction,656x464,642x535,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment2,580x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567x455,571x455,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,571x452x5x6,b2a7a7a7\n" +
      "DrawLine,2,567x464,571x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,571x461x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment3,400x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,387x446,391x446,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x443x5x6,b2a7a7a7\n" +
      "DrawLine,2,387x455,391x455,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x452x5x6,b2a7a7a7\n" +
      "DrawLine,2,387x473,391x473,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,391x470x5x6,b2a7a7a7\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
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
      "Clip,0,0,966,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawAction,NORMAL,400x400x76x128,490x389x78x139,NORMAL\n" +
      "DrawArrow,2,RIGHT,481x455x5x6,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment2,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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
            action("action8", destination = "root")
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
    scene.layout(0, SceneContext.get(view))

    val list = DisplayList()
    scene.buildDisplayList(list, 0, view)

    assertEquals(
      "Clip,0,0,1056,1078\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawTruncatedText,3,fragment2,490x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567x464,574x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,574x461x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,580x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,581x401x74x126\n" +
      "DrawTruncatedText,3,fragment3,580x390x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,657x455,664x455,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664x452x5x6,b2a7a7a7\n" +
      "DrawLine,2,657x464,664x464,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,664x461x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,400x520x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x521x74x126\n" +
      "DrawAction,NORMAL,400x520x76x128,490x389x78x139,NORMAL\n" +
      "DrawArrow,2,UP,526x531x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment4,400x510x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,477x566,484x566,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484x563x5x6,b2a7a7a7\n" +
      "DrawLine,2,477x575,484x575,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484x572x5x6,b2a7a7a7\n" +
      "DrawLine,2,477x593,484x593,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,484x590x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawRectangle,1,490x550x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x551x74x126\n" +
      "DrawArrow,2,UP,549x681x6x5,b2a7a7a7\n" +
      "DrawSelfAction,566x614,552x685,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment5,490x540x76x5,ff656565,Default:0:9,false\n" +
      "DrawLine,2,567x605,574x605,b2a7a7a7,3:0:1\n" +
      "DrawArrow,2,RIGHT,574x602x5x6,b2a7a7a7\n" +
      "\n" +
      "DrawFilledRectangle,1,400x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,400x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,400x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav2,400x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
  }

  fun testHoverMarksComponent() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment1")
        fragment("fragment2")
      }
    }

    val scene = model.surface.scene!!
    val view = model.surface.currentSceneView!!
    `when`(view.scale).thenReturn(1.0)
    val transform = SceneContext.get(view)!!
    val fragment1 = scene.getSceneComponent("fragment1")!!
    fragment1.setPosition(100, 100)
    fragment1.setSize(100, 100, false)
    fragment1.layout(transform, 0)
    val fragment2 = scene.getSceneComponent("fragment2")!!
    fragment2.setPosition(1000, 1000)
    fragment2.setSize(100, 100, false)
    fragment2.layout(transform, 0)

    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    var version = scene.displayListVersion

    scene.mouseHover(transform, 150, 150)
    assertEquals(SceneComponent.DrawState.HOVER, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 1050, 1050)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.HOVER, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
    version = scene.displayListVersion

    scene.mouseHover(transform, 0, 0)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment1.drawState)
    assertEquals(SceneComponent.DrawState.NORMAL, fragment2.drawState)
    assertTrue(version < scene.displayListVersion)
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
      "Clip,0,0,1056,928\n" +
      "DrawRectangle,1,490x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,491x401x74x126\n" +
      "DrawAction,NORMAL,490x400x76x128,580x389x78x139,NORMAL\n" +
      "DrawArrow,2,RIGHT,571x455x5x6,b2a7a7a7\n" +
      "DrawAction,NORMAL,490x400x76x128,400x419x70x30,NORMAL\n" +
      "DrawArrow,2,UP,432x453x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawRectangle,1,580x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,581x401x74x126\n" +
      "DrawTruncatedText,3,fragment2,580x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,400x430x70x19,fffafafa,6\n" +
      "DrawRectangle,1,400x430x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,400x430x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawAction,NORMAL,400x430x70x19,490x389x78x139,NORMAL\n" +
      "DrawArrow,2,RIGHT,481x455x5x6,b2a7a7a7\n" +
      "DrawAction,NORMAL,400x430x70x19,400x389x70x30,NORMAL\n" +
      "DrawArrow,2,UP,432x423x6x5,b2a7a7a7\n" +
      "DrawTruncatedText,3,nav1,400x420x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "DrawFilledRectangle,1,400x400x70x19,fffafafa,6\n" +
      "DrawRectangle,1,400x400x70x19,ffa7a7a7,1,6\n" +
      "DrawTruncatedText,3,Nested Graph,400x400x70x19,ffa7a7a7,Default:1:9,true\n" +
      "DrawTruncatedText,3,nav2,400x390x70x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    val list = DisplayList()
    val sceneManager = scene.sceneManager as NavSceneManager
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130x258\n", list.serialize()
    )
    assertTrue(sceneManager.isEmpty)

    root?.fragment("fragment1")

    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )
    assertFalse(sceneManager.isEmpty)

    model.delete(listOf(model.find("fragment1")!!))
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(surface, sceneManager))

    assertEquals(
      "DrawEmptyDesigner,130x258\n", list.serialize()
    )
    assertTrue(sceneManager.isEmpty)
  }

  fun testZoomIn() {
    zoomTest(3.0, "Clip,0,0,5259,5568\n" +
                  "DrawRectangle,1,2400x2400x459x768,ffa7a7a7,1,0\n" +
                  "DrawNavScreen,2401x2401x457x766\n" +
                  "DrawTruncatedText,3,fragment1,2400x2340x459x30,ff656565,Default:0:36,false\n" +
                  "\n" +
                  "UNClip\n")
  }

  fun testZoomOut() {
    zoomTest(0.25, "Clip,0,0,438,464\n" +
                   "DrawRectangle,1,200x200x38x64,ffa7a7a7,1,0\n" +
                   "DrawNavScreen,201x201x36x62\n" +
                   "DrawTruncatedText,3,fragment1,200x195x38x2,ff656565,Default:0:5,false\n" +
                   "\n" +
                   "UNClip\n")
  }

  fun testZoomToFit() {
    zoomTest(1.0, "Clip,0,0,1753,1856\n" +
                  "DrawRectangle,1,800x800x153x256,ffa7a7a7,1,0\n" +
                  "DrawNavScreen,801x801x151x254\n" +
                  "DrawTruncatedText,3,fragment1,800x780x153x10,ff656565,Default:0:12,false\n" +
                  "\n" +
                  "UNClip\n")
  }

  private fun zoomTest(newScale: Double, serialized: String) {
    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
    )

    list.clear()

    `when`(surface.scale).thenReturn(newScale)

    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals(serialized, list.serialize())
  }

  fun testCustomDestination() {
    val relativePath = "src/mytest/navtest/MyTestNavigator.java"
    val fileText = "package myTest.navtest;\n" +
                   "\n" +
                   "import android.os.Bundle;\n" +
                   "import android.support.annotation.NonNull;\n" +
                   "import android.support.annotation.Nullable;\n" +
                   "\n" +
                   "import androidx.navigation.NavDestination;\n" +
                   "import androidx.navigation.NavOptions;\n" +
                   "import androidx.navigation.Navigator;\n" +
                   "\n" +
                   "@Navigator.Name(\"MyTestNavigator\")\n" +
                   "public class TestNavigator extends Navigator<TestNavigator.Destination> {\n" +
                   "    @NonNull\n" +
                   "    @Override\n" +
                   "    public TestNavigator.Destination createDestination() {\n" +
                   "        return null;\n" +
                   "    }\n" +
                   "\n" +
                   "    @Override\n" +
                   "    public boolean popBackStack() {\n" +
                   "        return false;\n" +
                   "    }\n" +
                   "\n" +
                   "    @Override\n" +
                   "    public void navigate(@NonNull TestNavigator.Destination destination, @Nullable Bundle bundle, @Nullable NavOptions navOptions) {\n" +
                   "\n" +
                   "    }\n" +
                   "\n" +
                   "    public static class Destination extends NavDestination {\n" +
                   "        Destination(@NonNull Navigator<? extends NavDestination> navigator) {\n" +
                   "            super(navigator);\n" +
                   "        }\n" +
                   "    }\n" +
                   "}\n"

    myFixture.addFileToProject(relativePath, fileText)

    val model = model("nav.xml") {
      navigation {
        fragment("fragment1")
      }
    }

    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())

    val list = DisplayList()
    scene.buildDisplayList(list, 0, NavView(surface as NavDesignSurface, scene.sceneManager))

    assertEquals(
      "Clip,0,0,876,928\n" +
      "DrawRectangle,1,400x400x76x128,ffa7a7a7,1,0\n" +
      "DrawNavScreen,401x401x74x126\n" +
      "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
      "\n" +
      "UNClip\n", list.serialize()
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
}