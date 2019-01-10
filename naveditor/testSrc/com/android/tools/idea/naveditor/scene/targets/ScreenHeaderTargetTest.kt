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
package com.android.tools.idea.naveditor.scene.targets

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.scene.SceneComponent
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.draw.DisplayList
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.scene.draw.DrawHeader
import com.android.tools.idea.naveditor.scene.draw.DrawIcon

class ScreenHeaderTargetTest : NavTestCase() {
  fun testScreenHeaderTarget() {
    val model = model("nav.xml") {
      navigation("root", startDestination = "fragment1") {
        fragment("fragment1") {
          deeplink("https://www.android.com/")
        }
        fragment("fragment2") {
          deeplink("https://www.android.com/")
        }
        fragment("fragment3")
      }
    }
    val context = SceneContext.get(model.surface.currentSceneView)

    testScreenHeaderTarget(context, model, "fragment1", true, true)
    testScreenHeaderTarget(context, model, "fragment2", false, true)
    testScreenHeaderTarget(context, model, "fragment3", false, false)
  }

  fun testNoStartDestination() {
    val model = model("nav.xml") {
      navigation("root") {
        fragment("fragment")
        include("somegraph1")
        include("somegraph2")
      }
    }

    val root = model.find("root")!!
    val context = SceneContext.get(model.surface.currentSceneView)
    root.children.forEach {
      val sceneComponent = model.surface.scene!!.getSceneComponent(it)!!
      testScreenHeaderTarget(context, sceneComponent, false, false)
    }
  }

  private fun testScreenHeaderTarget(context: SceneContext, model: SyncNlModel, id: String, expectHome: Boolean, expectDeepLink: Boolean) {
    val nlComponent = model.find(id)!!
    val sceneComponent = model.surface.scene!!.getSceneComponent(nlComponent)!!

    testScreenHeaderTarget(context, sceneComponent, expectHome, expectDeepLink)
  }

  private fun testScreenHeaderTarget(context: SceneContext, component: SceneComponent, expectHome: Boolean, expectDeepLink: Boolean) {
    val target = ScreenHeaderTarget(component)
    val displayList = DisplayList()

    target.render(displayList, context)

    checkDrawIcon(displayList, DrawIcon.IconType.START_DESTINATION, expectHome)
    checkDrawIcon(displayList, DrawIcon.IconType.DEEPLINK, expectDeepLink)
  }

  private fun checkDrawIcon(displayList: DisplayList, iconType: DrawIcon.IconType, expected: Boolean) {
    val drawHeader = displayList.commands[0] as DrawHeader
    assertEquals(if (expected) 1 else 0, drawHeader.commands.filterIsInstance<DrawIcon>().count { it.iconType == iconType })
  }
}
