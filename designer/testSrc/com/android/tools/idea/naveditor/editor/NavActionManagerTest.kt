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
import com.android.tools.idea.naveditor.NavModelBuilderUtil.*
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
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

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    model = model("nav.xml",
        rootComponent("navigation").unboundedChildren(
            fragmentComponent("fragment1"),
            navigationComponent("subnav")
                .unboundedChildren(fragmentComponent("fragment2"))))
        .build()
    surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
  }

  fun testGetDestinations() {
    val actionManager = NavActionManager(surface)

    val virtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity_main2.xml")
    val xmlFile = PsiManager.getInstance(project).findFile(virtualFile!!) as XmlFile

    val parent = model.components[0]
    val expected1 = Destination.RegularDestination(parent, "fragment", null, "BlankFragment", "mytest.navtest.BlankFragment")
    val expected2 = Destination.RegularDestination(parent, "activity", null, "MainActivity", "mytest.navtest.MainActivity",
        layoutFile = xmlFile)
    val expected3 = Destination.IncludeDestination("navigation.xml", parent)
    assertSameElements(actionManager.destinations, ImmutableList.of(expected1, expected2, expected3))
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
    val model = model("nav.xml",
        rootComponent("root").unboundedChildren(
            navigationComponent("subflow").unboundedChildren(fragmentComponent("fragment2")),
            fragmentComponent("fragment1"))).build()

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
