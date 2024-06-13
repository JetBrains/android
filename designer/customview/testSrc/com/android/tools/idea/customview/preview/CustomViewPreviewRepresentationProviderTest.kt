/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import kotlinx.coroutines.runBlocking
import org.jetbrains.android.LightJavaCodeInsightFixtureAdtTestCase

class CustomViewPreviewRepresentationProviderTest : LightJavaCodeInsightFixtureAdtTestCase() {
  private lateinit var provider: CustomViewPreviewRepresentationProvider

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      "src/android/view/View.kt",
      """
      package android.view

      class View
    """
        .trimIndent(),
    )
    provider = CustomViewPreviewRepresentationProvider()
  }

  fun testDoNotAcceptKotlinWhenNoViews() = runBlocking {
    val file = myFixture.addFileToProject("src/com/example/SomeFile.kt", "")
    assertFalse(provider.accept(project, file))
  }

  fun testDoNotAcceptJavaWhenNoViews() = runBlocking {
    val file = myFixture.addFileToProject("src/com/example/SomeFile.java", "")
    assertFalse(provider.accept(project, file))
  }

  fun testDoNotAcceptRes() = runBlocking {
    val file = myFixture.addFileToProject("res/layout/some_layout.xml", "")
    assertFalse(provider.accept(project, file))
  }

  fun testQuickRejectKotlinWhenNoIncludes() = runBlocking {
    val file =
      myFixture.addFileToProject(
        "src/com/example/CustomView.kt",
        """
      package com.example

      import android.view.View

      class CustomView() : View()
    """
          .trimIndent(),
      )
    assertFalse(provider.accept(project, file))
  }

  fun testQuickRejectJavaWhenNoIncludes() = runBlocking {
    val file =
      myFixture.addFileToProject(
        "src/com/example/CustomView.java",
        """
      package com.example;

      import android.view.View;

      public class CustomView extends View {
        public CustomButton() {
          super();
        }
      }
    """
          .trimIndent(),
      )
    assertFalse(provider.accept(project, file))
  }

  fun testAcceptKotlinWhenHasViewsAndIncludes() = runBlocking {
    val file =
      myFixture.addFileToProject(
        "src/com/example/CustomView.kt",
        """
      package com.example

      import android.view.View
      import android.util.AttributeSet
      import android.content.Context

      class CustomView() : View()
    """
          .trimIndent(),
      )
    assertTrue(provider.accept(project, file))
  }

  fun testAcceptJavaWhenHasViewsAndDirectIncludes() = runBlocking {
    val file =
      myFixture.addFileToProject(
        "src/com/example/CustomView.java",
        """
      package com.example;

      import android.view.View;
      import android.util.AttributeSet;
      import android.content.Context;

      public class CustomView extends View {
        public CustomButton() {
          super();
        }
      }
    """
          .trimIndent(),
      )
    assertTrue(provider.accept(project, file))
  }

  fun testAcceptJavaWhenHasViewsAndInDirectIncludes() = runBlocking {
    val file =
      myFixture.addFileToProject(
        "src/com/example/CustomView.java",
        """
      package com.example;

      import android.view.View;
      import android.util.*;
      import android.content.*;

      public class CustomView extends View {
        public CustomButton(AttributeSet s, Context c) {
          super();
        }
      }
    """
          .trimIndent(),
      )
    assertTrue(provider.accept(project, file))
  }
}
