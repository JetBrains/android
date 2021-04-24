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
package com.android.tools.idea.naveditor.structure

import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.project.DumbServiceImpl
import javax.swing.DefaultListModel

class HostPanelTest : NavTestCase() {

  fun testDumbMode() {
    // This has a navHostFragment referencing our nav file
    myFixture.addFileToProject("res/layout/file1.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                       "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                       "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                                                       "\n" +
                                                       "    <fragment\n" +
                                                       "        android:id=\"@+id/fragment3\"\n" +
                                                       "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                                                       "        app:defaultNavHost=\"true\"\n" +
                                                       "        app:navGraph=\"@navigation/nav\" />\n" +
                                                       "\n" +
                                                       "</LinearLayout>")
    val model = model("nav.xml") { navigation() }
    waitForResourceRepositoryUpdates()
    val panel = HostPanel(model.surface as NavDesignSurface)
    val listModel = panel.list.model as DefaultListModel
    waitFor("list was never populated") { !listModel.isEmpty }
    panel.list.model = listModel

    DumbServiceImpl.getInstance(project).isDumb = true
    try {
      model.activate(this)
      waitFor("list expected to be empty") { listModel.isEmpty }
      DumbServiceImpl.getInstance(project).isDumb = false
      waitFor("list was never populated") { !listModel.isEmpty }
    }
    finally {
      DumbServiceImpl.getInstance(project).isDumb = false
    }
  }

  fun testFindReferences() {
    // This has a navHostFragment referencing our nav file
    myFixture.addFileToProject("res/layout/file1.xml", """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .    xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .    <fragment
      .       android:id="@+id/fragment3"
      .       android:name="androidx.navigation.fragment.NavHostFragment"
      .       app:defaultNavHost="true"
      .       app:navGraph="@navigation/nav"/>
      .
      .</LinearLayout>
      """.trimMargin("."))

    // This has a navHostFragment referencing a different nav file
    myFixture.addFileToProject("res/layout/file2.xml", """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .   xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .   <fragment
      .     android:id="@+id/fragment3"
      .     android:name="androidx.navigation.fragment.NavHostFragment"
      .     app:defaultNavHost="true"
      .     app:navGraph="@navigation/navigation"/>
      .
      .</LinearLayout>
      """.trimMargin("."))

    // This has a fragment referencing this file, but it's not a navHostFragment
    myFixture.addFileToProject("res/layout/file3.xml", """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .   xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .   <fragment
      .       android:id="@+id/fragment3"
      .       android:name="com.example.MyFragment"
      .       app:defaultNavHost="true"
      .       app:navGraph="@navigation/navigation"/>
      .
      .</LinearLayout>
      """.trimMargin("."))

    val model = model("nav.xml") { navigation() }
    waitForResourceRepositoryUpdates()

    val references = findReferences(model.file, model.module)
    assertEquals(1, references.size)
    assertEquals("file1.xml", references[0].containingFile.name)
    assertEquals(174, references[0].textOffset)
  }

  fun testFindDerivedClassReference() {
    // This has a subclass of NavHostFragment referencing this file
    myFixture.addFileToProject("res/layout/file1.xml", """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .    xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .    <fragment
      .        android:id="@+id/fragment3"
      .        android:name="mytest.navtest.NavHostFragmentChild"
      .        app:defaultNavHost="true"
      .        app:navGraph="@navigation/nav" />
      .
      .</LinearLayout>
      """.trimMargin("."))

    myFixture.addFileToProject("src/mytest/navtest/NavHostFragmentChild.java", """
      .package mytest.navtest;
      .import androidx.navigation.fragment.NavHostFragment;
      .
      .public class NavHostFragmentChild extends NavHostFragment {
      .}
      """.trimMargin("."))

    val model = model("nav.xml") { navigation() }
    waitForResourceRepositoryUpdates()

    val references = findReferences(model.file, model.module)
    assertEquals(1, references.size)
    assertEquals("file1.xml", references[0].containingFile.name)
    assertEquals(174, references[0].textOffset)
  }

  private fun waitFor(error: String, condition: () -> Boolean) {
    repeat(1000) {
      if (condition()) {
        return
      }
      Thread.sleep(10)
    }
    fail(error)
  }
}