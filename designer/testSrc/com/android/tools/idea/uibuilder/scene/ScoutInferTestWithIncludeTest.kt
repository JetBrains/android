/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.uibuilder.scout.Scout
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction

class ScoutInferTestWithIncludeTest: SceneTest() {
  override fun createModel(): ModelBuilder =
    model("constraint.xml",
          component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
            .id("@+id/content_main")
            .withBounds(0, 0, 2000, 2000)
            .width("match_parent")
            .height("match_parent")
            .children(
              component(SdkConstants.TEXT_VIEW)
                .id("@+id/textview")
                .withBounds(900, 980, 200, 40)
                .width("match_parent")
                .height("match_parent"),
              component(SdkConstants.TAG_INCLUDE)
                .id("@+id/the_include")
                .withAttribute("layout", "@layout/test_layout")
                .withBounds(900, 980, 200, 40)
            ))

  // Regression test for b/219886505
  fun testInferWithIncludeDoesNotThrow() {
    runWriteCommandAction(myFacet.module.project) { Scout.inferConstraintsAndCommit(myModel.components) }
    myScreen.get("@+id/content_main")
      .expectXml("""
        <android.support.constraint.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/content_main"
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:id="@+id/textview"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                app:layout_constraintBaseline_toBaselineOf="@+id/the_include"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent" />

            <include
                android:id="@+id/the_include"
                layout="@layout/test_layout"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintStart_toStartOf="@+id/textview"
                app:layout_constraintTop_toTopOf="parent" />
        </android.support.constraint.ConstraintLayout>
      """.trimIndent())
  }
}