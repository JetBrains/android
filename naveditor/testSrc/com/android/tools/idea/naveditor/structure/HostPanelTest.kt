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

class HostPanelTest : NavTestCase() {

  fun testFindReferences() {
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
    // This has a navHostFragment referencing a different nav file
    myFixture.addFileToProject("res/layout/file2.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                       "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                       "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                                                       "\n" +
                                                       "    <fragment\n" +
                                                       "        android:id=\"@+id/fragment3\"\n" +
                                                       "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                                                       "        app:defaultNavHost=\"true\"\n" +
                                                       "        app:navGraph=\"@navigation/navigation\" />\n" +
                                                       "\n" +
                                                       "</LinearLayout>")
    // This has a fragment referencing this file, but it's not a navHostFragment
    myFixture.addFileToProject("res/layout/file3.xml", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                       "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                       "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
                                                       "\n" +
                                                       "    <fragment\n" +
                                                       "        android:id=\"@+id/fragment3\"\n" +
                                                       "        android:name=\"com.example.MyFragment\"\n" +
                                                       "        app:defaultNavHost=\"true\"\n" +
                                                       "        app:navGraph=\"@navigation/navigation\" />\n" +
                                                       "\n" +
                                                       "</LinearLayout>")

    val model = model("nav.xml") { navigation() }
    val panel = HostPanel(model.surface as NavDesignSurface)

    val references = findReferences(model.file)
    assertEquals(1, references.size)
    assertEquals("file1.xml", references[0].containingFile.name)
    assertEquals(174, references[0].textOffset)
  }
}