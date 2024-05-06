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
package com.android.tools.idea.run

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidActivityRunLineMarkerContributorTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  @Before
  fun setUp() {
    // Simulate the existence of android.app.Activity
    projectRule.fixture.addFileToProject(
      "src/android/app/Activity.java",
      """
      package android.app

      class Activity {}
      """
        .trimIndent()
    )
  }

  @Test
  @RunsInEdt
  fun identifyKotlinActivity() {
    val activityFile =
      projectRule.fixture.addFileToProject(
        "src/com/example/myapplication/MyActivity.kt",
        """
      package com.example.myapplication

      import android.app.Activity

      /**
       */
      class MyActivity : Activity() {
      }
      """
          .trimIndent()
      )

    val contributor = AndroidActivityRunLineMarkerContributor()
    assertNotNull(contributor.getInfo(activityFile.findElementByText("class")))
    assertNull(
      contributor.getInfo(activityFile.findElementByText("package com.example.myapplication"))
    )
  }

  @Test
  @RunsInEdt
  fun identifyJavaActivity() {
    val activityFile =
      projectRule.fixture.addFileToProject(
        "src/com/example/myapplication/MyActivity.java",
        """
      package com.example.myapplication;

      import android.app.Activity;

      /**
       */
      class MyActivity extends Activity {
      }
      """
          .trimIndent()
      )

    val contributor = AndroidActivityRunLineMarkerContributor()
    assertNotNull(contributor.getInfo(activityFile.findElementByText("class")))
    assertNull(
      contributor.getInfo(activityFile.findElementByText("package com.example.myapplication;"))
    )
  }
}

fun PsiFile.findElementByText(text: String): PsiElement =
  findDescendantOfType { it.node.text == text }!!
