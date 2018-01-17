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
import com.android.tools.idea.common.editor.NlEditor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.common.surface.ZoomType
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.naveditor.surface.NavView
import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.`when`

/**
 * Tests for the nav editor Scene.
 */
class NavSceneTest : NavTestCase() {

  fun testDisplayList() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "subnav")
          action("action2", destination="activity")
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

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1050,928\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,491,401,74,126\n" +
        "DrawAction,NORMAL,490x400x76x128,580x400x70x19,NORMAL\n" +
        "DrawArrow,2,RIGHT,570x406x5x6,b2a7a7a7\n" +
        "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,390x461x5x6,b2a7a7a7\n" +
        "DrawIcon,490x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment1,498x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,580x400x70x19,fffafafa,6\n" +
        "DrawRectangle,579x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,580x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawAction,EXIT,580x400x70x19,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,390x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,subnav,580x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,400x400x76x128,fffafafa,6\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,6\n" +
        "DrawFilledRectangle,404x404x68x111,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,404x404x68x111,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,Activity,400x515x76x13,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,activity,400x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,960,928\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawAction,NORMAL,400x400x76x128,490x400x70x19,NORMAL\n" +
        "DrawArrow,2,RIGHT,480x406x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,490x400x70x19,fffafafa,6\n" +
        "DrawRectangle,489x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,navigation.xml,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,nav,490x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
    val algorithm = ManualLayoutAlgorithm(model.module)
    var component = scene.getSceneComponent("fragment1")!!
    component.setPosition(-100, -200)
    algorithm.save(component)
    component = scene.getSceneComponent("fragment2")!!
    component.setPosition(-300, 0)
    algorithm.save(component)
    component = scene.getSceneComponent("fragment3")!!
    component.setPosition(200, 200)
    algorithm.save(component)

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1126,1128\n" +
        "DrawRectangle,500x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,501,401,74,126\n" +
        "DrawIcon,500x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,400x500x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,501,74,126\n" +
        "DrawTruncatedText,3,fragment2,400x490x77x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,650x600x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,651,601,74,126\n" +
        "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
    val algorithm = ManualLayoutAlgorithm(model.module)
    var component = scene.getSceneComponent("fragment1")!!
    component.setPosition(1900, 1800)
    algorithm.save(component)
    component = scene.getSceneComponent("fragment2")!!
    component.setPosition(1700, 2000)
    algorithm.save(component)
    component = scene.getSceneComponent("fragment3")!!
    component.setPosition(2200, 2200)
    algorithm.save(component)

    val list = DisplayList()
    model.surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1126,1128\n" +
        "DrawRectangle,500x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,501,401,74,126\n" +
        "DrawIcon,500x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,400x500x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,501,74,126\n" +
        "DrawTruncatedText,3,fragment2,400x490x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,650x600x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,651,601,74,126\n" +
        "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
  }

  fun testAddComponent() {
    /*lateinit*/ var root: NavModelBuilderUtil.NavigationComponentDescriptor? = null

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

    root!!.fragment("fragment3")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1056,928\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,491,401,74,126\n" +
        "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,390x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,401,74,126\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,580x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,581x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,581x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment3,580x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
  }

  fun testRemoveComponent() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment2") {
        fragment("fragment1", layout = "activity_main") {
          action("action1", destination = "fragment2")
        }
        fragment("fragment2", layout="activity_main2")
      }
    }
    val editor = TestNlEditor(model.virtualFile, project)

    val scene = model.surface.scene!!

    val list = DisplayList()
    model.delete(listOf(model.find("fragment2")!!))

    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,876,928\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,401,74,126\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawLine,2,477x464,484x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,484x461x5x6,b2a7a7a7\n" +
        "\n" +
        "UNClip\n", list.serialize())

    val undoManager = UndoManager.getInstance(project)
    undoManager.undo(editor)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)
    model.surface.sceneManager!!.update()
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,966,928\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,491,401,74,126\n" +
        "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,390x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,401,74,126\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
  }

  private class TestNlEditor(private val myFile: VirtualFile, project: Project) : NlEditor(myFile, project), DocumentReferenceProvider {

    override fun getDocumentReferences(): Collection<DocumentReference> {
      return ImmutableList.of(DocumentReferenceManager.getInstance().create(myFile))
    }
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
    val surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    surface.zoom(ZoomType.ACTUAL)
    if (!SystemInfo.isMac || !UIUtil.isRetina()) {
      surface.zoomOut()
      surface.zoomOut()
      surface.zoomOut()
      surface.zoomOut()
    }
    val scene = surface.scene!!
    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    val view = NavView(surface, surface.sceneManager!!)
    scene.buildDisplayList(list, 0, view)
    assertEquals("Clip,0,0,56,-72\n" +
        "DrawRectangle,-10x-100x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-9x-99x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-9x-99x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawAction,NORMAL,-10x-100x76x128,80x-100x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,70x-39x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment1,-10x-110x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawRectangle,80x-100x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,81,-99,74,126\n" +
        "DrawIcon,80x-111x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment2,88x-110x68x5,ff656565,Default:0:9,false\n" +
        "DrawLine,2,157x-36,164x-36,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,164x-39x5x6,b2a7a7a7\n" +
        "\n" +
        "DrawFilledRectangle,-100x-70x70x19,fffafafa,6\n" +
        "DrawRectangle,-101x-71x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,-100x-70x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawAction,EXIT,-100x-70x70x19,-10x-100x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,-20x-39x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,subnav,-100x-80x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
    list.clear()
    surface.currentNavigation = model.find("subnav")!!
    scene.layout(0, SceneContext.get(view))
    scene.buildDisplayList(list, 0, view)
    assertEquals("Clip,0,0,-246,-254\n" +
        "DrawRectangle,-122x-140x15x25,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-121x-139x13x23,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-121x-139x13x23,ffa7a7a7,Default:0:2,true\n" +
        "DrawAction,NORMAL,-122x-140x15x25,-140x-140x15x25,NORMAL\n" +
        "DrawArrow,2,RIGHT,-142x-128x1x1,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment3,-122x-142x15x1,ff656565,Default:0:2,false\n" +
        "\n" +
        "DrawRectangle,-140x-140x15x25,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-139x-139x13x23,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-139x-139x13x23,ffa7a7a7,Default:0:2,true\n" +
        "DrawTruncatedText,3,fragment4,-140x-142x15x1,ff656565,Default:0:2,false\n" +
        "DrawLine,2,-125x-128,-124x-128,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-124x-128x1x1,b2a7a7a7\n" +
        "\n" +
        "UNClip\n", list.serialize())
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

    assertEquals("Clip,0,0,876,928\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
    object: WriteCommandAction<Any?>(project, "Add") {
      override fun run(result: Result<Any?>) {
        val tag = rootComponent.tag.createChildTag("fragment", null, null, true)
        val newComponent = surface.model!!.createComponent(tag, rootComponent, null)
        surface.selectionModel.setSelection(ImmutableList.of(newComponent))
        newComponent.assignId("myId")
      }
    }.execute()
    val manager = NavSceneManager(model, model.surface as NavDesignSurface)
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

    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals("Clip,0,0,960,928\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,401,74,126\n" +
        "DrawAction,SELF,400x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,UP,457x533x6x5,b2a7a7a7\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,490x400x70x19,fffafafa,6\n" +
        "DrawRectangle,489x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawAction,SELF,490x400x70x19,490x400x70x19,NORMAL\n" +
        "DrawArrow,2,UP,541x424x6x5,b2a7a7a7\n" +
        "DrawTruncatedText,3,nav1,490x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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

    assertEquals("Clip,0,0,876,928\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawNavScreen,401,401,74,126\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "DrawIcon,469x389x7x7,DEEPLINK\n" +
        "DrawTruncatedText,3,fragment1,408x390x60x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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

    var list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    val view = NavView(model.surface as NavDesignSurface, scene.sceneManager)
    scene.buildDisplayList(list, 0, view)
    val context = SceneContext.get(view)

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawLine,2,387x464,391x464,ff1886f7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,ff1886f7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(context))

    // now "subnav" is in the front
    val subnav = model.find("subnav")!!
    model.surface.selectionModel.setSelection(ImmutableList.of(subnav))
    list.clear()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, view)

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ff1886f7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ff1886f7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "DrawFilledCircle,6,591x409,fff5f5f5,0:3:54\n" +
        "DrawCircle,7,591x409,ff1886f7,2,0:2:54\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(context))

    // test multi select
    model.surface.selectionModel.setSelection(ImmutableList.of(model.find("fragment1")!!, subnav))

    list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawRectangle,398x398x80x132,ff1886f7,1,2\n" +
        "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
        "DrawIcon,400x389x7x7,START_DESTINATION\n" +
        "\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ff1886f7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ff1886f7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "DrawFilledCircle,6,591x409,fff5f5f5,3:0:54\n" +
        "DrawCircle,7,591x409,ff1886f7,2,2:0:54\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(context))
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

    val list = DisplayList()
    val view = model.surface.currentSceneView!!
    `when`(view.scale).thenReturn(1.0)
    val transform = SceneContext.get(view)
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.mouseHover(transform, 150, 30)
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawRectangle,398x398x80x132,ffa7a7a7,1,2\n" +
        "DrawAction,NORMAL,400x400x76x128,520x400x70x19,NORMAL\n" +
        "DrawArrow,2,RIGHT,510x406x5x6,b2a7a7a7\n" +
        "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawFilledCircle,6,478x464,fff5f5f5,0:3:54\n" +
        "DrawCircle,7,478x464,ffa7a7a7,2,0:2:54\n" +
        "\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(transform))

    scene.mouseHover(transform, 488, 436)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawAction,NORMAL,400x400x76x128,520x400x70x19,HOVER\n" +
        "DrawArrow,2,RIGHT,510x406x5x6,ffa7a7a7\n" +
        "DrawLine,2,387x464,391x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawFilledCircle,6,478x464,fff5f5f5,3:0:54\n" +
        "DrawCircle,7,478x464,ffa7a7a7,2,2:0:54\n" +
        "\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(transform))

    scene.mouseHover(transform, 120, 148)
    list.clear()
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))

    assertEquals("Clip,0,0,990,928\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawAction,NORMAL,400x400x76x128,520x400x70x19,NORMAL\n" +
        "DrawArrow,2,RIGHT,510x406x5x6,b2a7a7a7\n" +
        "DrawLine,2,387x464,391x464,ffa7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x461x5x6,ffa7a7a7\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "DrawFilledRectangle,520x400x70x19,fffafafa,6\n" +
        "DrawRectangle,519x399x72x21,ffa7a7a7,1,6\n" +
        "DrawTruncatedText,3,Nested Graph,520x400x70x19,ffa7a7a7,Default:1:9,true\n" +
        "DrawTruncatedText,3,subnav,520x390x70x5,ff656565,Default:0:9,false\n" +
        "\n" +
        "UNClip\n", list.generateSortedDisplayList(transform))
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
    assertEquals("Clip,0,0,977,1028\n" +
        "DrawRectangle,450x450x77x128,FRAMES,1,0\n" +
        "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
        "DrawScreenLabel,450,445,fragment1\n" +
        "\n" +
        "UNClip\n", list.serialize())

    list.clear()
    model.configuration
        .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,914,914\n" +
        "DrawRectangle,425x425x64x64,FRAMES,1,0\n" +
        "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
        "DrawTruncatedText,3,fragment1,425x415x64x5,SUBDUED_TEXT,0,false\n" +
        "\n" +
        "UNClip\n", list.serialize())

    list.clear()
    model.configuration.setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false)
    surface.sceneManager!!.update()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1028,972\n" +
        "DrawRectangle,450x450x128x72,FRAMES,1,0\n" +
        "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
        "DrawTruncatedText,3,fragment1,450x440x128x5,SUBDUED_TEXT,0,false\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
        }
        fragment("fragment3")
      }
    }

    val list = DisplayList()
    val surface = model.surface
    val scene = surface.scene!!
    scene.layout(0, SceneContext.get())
    scene.buildDisplayList(list, 0, NavView(model.surface as NavDesignSurface, scene.sceneManager))
    assertEquals("Clip,0,0,1086,928\n" +
        "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,491x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,491x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawLine,2,477x464,481x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,481x461x5x6,b2a7a7a7\n" +
        "\n" +
        "DrawRectangle,610x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,611x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,611x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawAction,NORMAL,610x400x76x128,400x400x76x128,NORMAL\n" +
        "DrawArrow,2,RIGHT,390x461x5x6,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment2,610x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawLine,2,597x455,601x455,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,601x452x5x6,b2a7a7a7\n" +
        "DrawLine,2,597x464,601x464,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,601x461x5x6,b2a7a7a7\n" +
        "\n" +
        "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
        "DrawTruncatedText,3,fragment3,400x390x76x5,ff656565,Default:0:9,false\n" +
        "DrawLine,2,387x446,391x446,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x443x5x6,b2a7a7a7\n" +
        "DrawLine,2,387x455,391x455,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x452x5x6,b2a7a7a7\n" +
        "DrawLine,2,387x473,391x473,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,391x470x5x6,b2a7a7a7\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
          navigation("nav2") {
            action("action8", destination = "root")
          }
        }
      }
    }

    val surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    surface.zoom(ZoomType.ACTUAL)

    if (!SystemInfo.isMac || !UIUtil.isRetina()) {
      surface.zoomOut()
      surface.zoomOut()
      surface.zoomOut()
      surface.zoomOut()
    }

    val scene = surface.scene!!
    val list = DisplayList()
    scene.layout(0, SceneContext.get(model.surface.currentSceneView))

    val view = NavView(surface, surface.sceneManager!!)
    surface.currentNavigation = model.find("nav1")!!
    scene.layout(0, SceneContext.get(view))
    scene.buildDisplayList(list, 0, view)


    assertEquals("Clip,0,0,-222,-246\n" +
        "DrawRectangle,-122x-140x15x25,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-121x-139x13x23,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-121x-139x13x23,ffa7a7a7,Default:0:2,true\n" +
        "DrawTruncatedText,3,fragment2,-122x-142x15x1,ff656565,Default:0:2,false\n" +
        "DrawLine,2,-107x-128,-106x-128,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-106x-128x1x1,b2a7a7a7\n" +
        "\n" +
        "DrawRectangle,-98x-140x15x25,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-97x-139x13x23,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-97x-139x13x23,ffa7a7a7,Default:0:2,true\n" +
        "DrawTruncatedText,3,fragment3,-98x-142x15x1,ff656565,Default:0:2,false\n" +
        "DrawLine,2,-83x-130,-82x-130,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-82x-130x1x1,b2a7a7a7\n" +
        "DrawLine,2,-83x-128,-82x-128,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-82x-128x1x1,b2a7a7a7\n" +
        "\n" +
        "DrawRectangle,-140x-134x15x25,ffa7a7a7,1,0\n" +
        "DrawFilledRectangle,-139x-133x13x23,fffafafa,0\n" +
        "DrawTruncatedText,3,Preview Unavailable,-139x-133x13x23,ffa7a7a7,Default:0:2,true\n" +
        "DrawAction,NORMAL,-140x-134x15x25,-122x-140x15x25,NORMAL\n" +
        "DrawArrow,2,RIGHT,-124x-128x1x1,b2a7a7a7\n" +
        "DrawTruncatedText,3,fragment4,-140x-137x15x1,ff656565,Default:0:2,false\n" +
        "DrawLine,2,-125x-126,-124x-126,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-124x-126x1x1,b2a7a7a7\n" +
        "DrawLine,2,-125x-124,-124x-124,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-124x-124x1x1,b2a7a7a7\n" +
        "DrawLine,2,-125x-120,-124x-120,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-124x-120x1x1,b2a7a7a7\n" +
        "\n" +
        "DrawFilledRectangle,-122x-110x14x3,fffafafa,1\n" +
        "DrawRectangle,-123x-111x16x5,ffa7a7a7,1,1\n" +
        "DrawTruncatedText,3,Nested Graph,-122x-110x14x3,ffa7a7a7,Default:1:2,true\n" +
        "DrawTruncatedText,3,nav2,-122x-113x14x1,ff656565,Default:0:2,false\n" +
        "DrawLine,2,-108x-109,-107x-109,b2a7a7a7,3:0:1\n" +
        "DrawArrow,2,RIGHT,-107x-109x1x1,b2a7a7a7\n" +
        "\n" +
        "UNClip\n", list.serialize())
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
}
