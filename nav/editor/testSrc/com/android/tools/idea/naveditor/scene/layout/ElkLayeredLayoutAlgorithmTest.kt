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
package com.android.tools.idea.naveditor.scene.layout

import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase

class ElkLayeredLayoutAlgorithmTest : NavTestCase() {
  fun testLayout() {
    val model = model("nav.xml") {
      navigation(startDestination = "title") {
        fragment("title") {
          action("a1", destination = "register")
          action("a2", destination = "match")
          action("a3", destination = "leaderboard")
        }
        fragment("game") {
          action("a4", destination = "game_over")
          action("a5", destination = "winner")
        }
        fragment("register") {
          action("a6", destination = "match")
        }
        fragment("game_over") {
          action("a7", destination = "match")
        }
        fragment("winner") {
          action("a8", popUpTo = "match")
          action("a9", destination = "leaderboard")
        }
        fragment("match") {
          action("a10", destination = "game")
        }
        fragment("leaderboard") {
          action("a11", destination = "profile")
        }
        fragment("profile")
      }
    }
    val sceneManager = model.surface.getSceneManager(model)!!
    sceneManager.update()
    val scene = sceneManager.scene
    val root = scene.root!!
    val algorithm = ElkLayeredLayoutAlgorithm()
    root.children.forEach { it.setPosition(0, 0) }
    algorithm.layout(root.children)

    assertPosition(scene, "title", 12, 190)
    assertPosition(scene, "game", 265, 372)
    assertPosition(scene, "register", 518, 12)
    assertPosition(scene, "game_over", 518, 372)
    assertPosition(scene, "winner", 518, 728)
    assertPosition(scene, "match", 771, 97)
    assertPosition(scene, "leaderboard", 771, 813)
    assertPosition(scene, "profile", 1024, 813)
  }

  fun testLayoutStartHasIncoming() {
    val model = model("nav.xml") {
      navigation(startDestination = "f1") {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "f3")
        }
        fragment("f2")
        fragment("f3") {
          action("a3", destination = "f1")
          action("a4", destination = "f2")
        }
      }
    }
    val sceneManager = model.surface.getSceneManager(model)!!
    sceneManager.update()
    val scene = sceneManager.scene
    val root = scene.root!!
    val algorithm = ElkLayeredLayoutAlgorithm()
    root.children.forEach { it.setPosition(0, 0) }
    algorithm.layout(root.children)

    assertPosition(scene, "f1", 265, 63)
    assertPosition(scene, "f2", 518, 20)
    assertPosition(scene, "f3", 12, 20)
  }

  fun testDisconnectedDestinations() {
    val model = model("nav.xml") {
      navigation(startDestination = "f1") {
        fragment("f1") {
          action("a1", destination = "f2")
          action("a2", destination = "f3")
        }
        fragment("f2")
        fragment("f3")
        fragment("f4")
      }
    }
    val sceneManager = model.surface.getSceneManager(model)!!
    sceneManager.update()
    val scene = sceneManager.scene
    val root = scene.root!!
    val algorithm = ElkLayeredLayoutAlgorithm()
    root.children.forEach { it.setPosition(0, 0) }
    algorithm.layout(root.children)

    assertPosition(scene, "f1", 265, 54)
    assertPosition(scene, "f2", 518, 12)
    assertPosition(scene, "f3", 518, 368)
    assertPosition(scene, "f4", 12, 12)
  }

  private fun assertPosition(scene: Scene, id: String, x: Int, y: Int) {
    val sceneComponent = scene.getSceneComponent(id)!!
    assertEquals(x, sceneComponent.drawX)
    assertEquals(y, sceneComponent.drawY)
  }
}