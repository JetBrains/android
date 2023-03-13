/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.naveditor.inspections

import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.DisposableRule
import org.jetbrains.android.dom.inspections.NavFileInspection
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NavFileInspectionTest {
  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.withSdk()
  private val navRule = NavEditorRule(projectRule)
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(navRule).around(disposableRule)!!

  @Before
  fun setUp() {
    runReadAction {
      NavigationSchema.createIfNecessary(projectRule.module)
    }
    projectRule.fixture.enableInspections(NavFileInspection::class.java)
  }

  @Test
  fun testEmptyNames() {
    val psiFile = projectRule.fixture.addFileToProject(
      "res/navigation/abstract_classes_check_nav.xml",
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/abstract_classes_check_nav"
            app:startDestination="@id/fragment1">

        <fragment
            android:id="@+id/fragment1"
            android:name=<error descr="'' is not a valid destination for tag 'fragment'">""</error>
            android:label="lbl1" />
        <activity
            android:id="@+id/mainActivity"
            android:name=<error descr="'' is not a valid destination for tag 'activity'">""</error>
            android:label="activity_main"
            tools:layout="@layout/activity_main" />
        </navigation>
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting()
  }

  @Test
  fun testAbstractClassesNames() {
    val psiFile = projectRule.fixture.addFileToProject(
      "res/navigation/abstract_classes_check_nav.xml",
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/abstract_classes_check_nav"
            app:startDestination="@id/fragment1">

        <fragment
            android:id="@+id/fragment1"
            android:name=<error descr="'mytest.navtest.AbstractFragment' is not a valid destination for tag 'fragment'">"mytest.navtest.AbstractFragment"</error>
            android:label="lbl1" />
        <activity
            android:id="@+id/mainActivity"
            android:name=<error descr="'mytest.navtest.AbstractActivity' is not a valid destination for tag 'activity'">"mytest.navtest.AbstractActivity"</error>
            android:label="activity_main"
            tools:layout="@layout/activity_main" />
        </navigation>
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting()
  }

  @Test
  fun testIncompatibleClassesNames() {
    val psiFile = projectRule.fixture.addFileToProject(
      "res/navigation/abstract_classes_check_nav.xml",
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/abstract_classes_check_nav"
            app:startDestination="@id/fragment1">

        <fragment
            android:id="@+id/fragment1"
            android:name=<error descr="'mytest.navtest.MainActivity' is not a valid destination for tag 'fragment'">"mytest.navtest.MainActivity"</error>
            android:label="lbl1" />
        <activity
            android:id="@+id/mainActivity"
            android:name=<error descr="'mytest.navtest.BlankFragment' is not a valid destination for tag 'activity'">"mytest.navtest.BlankFragment"</error>
            android:label="activity_main"
            tools:layout="@layout/activity_main" />
        </navigation>
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting()
  }

  @Test
  fun testCompatibleClassesNames() {
    val psiFile = projectRule.fixture.addFileToProject(
      "res/navigation/abstract_classes_check_nav.xml",
      // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/abstract_classes_check_nav"
            app:startDestination="@id/fragment1">

        <fragment
            android:id="@+id/fragment1"
            android:name="mytest.navtest.BlankFragment"
            android:label="lbl1" />
        <activity
            android:id="@+id/mainActivity"
            android:name="mytest.navtest.MainActivity"
            android:label="activity_main"
            tools:layout="@layout/activity_main" />
        </navigation>
      """.trimIndent()
    )

    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    projectRule.fixture.checkHighlighting()
  }
}