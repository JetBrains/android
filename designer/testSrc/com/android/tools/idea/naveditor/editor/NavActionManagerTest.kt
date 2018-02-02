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
package com.android.tools.idea.naveditor.editor

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.mockito.Mockito.`when`

/**
 * Tests for [NavActionManager]
 */
class NavActionManagerTest : NavTestCase() {

  private lateinit var model: SyncNlModel
  private lateinit var surface: NavDesignSurface

  override fun setUp() {
    super.setUp()
    model = model("nav.xml") {
      navigation("navigation") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }
    surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
  }

  fun testAddElement() {
    val layout = LocalResourceManager.getInstance(myFacet.module)!!.findResourceFiles(
        ResourceFolderType.LAYOUT).stream().filter { file -> file.name == "activity_main.xml" }.findFirst().get() as XmlFile
    Destination.RegularDestination(surface.currentNavigation, "activity", null, "MainActivity", "mytest.navtest.MainActivity",
        "myId", layout)
        .addToGraph()
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<fragment>, instance=1}\n" +
        "    NlComponent{tag=<navigation>, instance=2}\n" +
        "        NlComponent{tag=<fragment>, instance=3}\n" +
        "    NlComponent{tag=<activity>, instance=4}",
        NlTreeDumper().toTree(model.components))
    val newChild = model.find("myId")!!
    assertEquals(SdkConstants.LAYOUT_RESOURCE_PREFIX + "activity_main", newChild.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
    assertEquals("mytest.navtest.MainActivity", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))
    assertEquals("@+id/myId", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID))
  }

  fun testAddElementInSubflow() {
    val model = model("nav.xml") {
      navigation("root") {
        navigation("subflow") {
          fragment("fragment2")
        }
        fragment("fragment1")
      }
    }

    val surface = model.surface as NavDesignSurface
    `when`(surface.currentNavigation).thenReturn(model.find("subflow"))

    val psiClass = JavaPsiFacade.getInstance(project).findClass("mytest.navtest.MainActivity", GlobalSearchScope.allScope(project))
    Destination.RegularDestination(surface.currentNavigation, "activity", null, psiClass!!.name, psiClass.qualifiedName).addToGraph()

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
        "    NlComponent{tag=<navigation>, instance=1}\n" +
        "        NlComponent{tag=<fragment>, instance=2}\n" +
        "        NlComponent{tag=<activity>, instance=3}\n" +
        "    NlComponent{tag=<fragment>, instance=4}", NlTreeDumper().toTree(model.components))
  }
}
