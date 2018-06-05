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
package com.android.tools.idea.res

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import org.jetbrains.android.AndroidTestCase

class ProjectLightResourceClassServiceTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.IN_MEMORY_R_CLASSES.override(true)
    // No need to copy R.java into gen!

    myFixture.addFileToProject(
      "/res/values/values.xml",
      // language=xml
      """
        <resources>
          <string name="appString">Hello from app</string>
        </resources>
      """.trimIndent()
    )
  }

  /**
   * Sets the language level to avoid the module system visibility rules.
   */
  override fun getLanguageLevel() = LanguageLevel.JDK_1_8

  override fun tearDown() {
    try {
      StudioFlags.IN_MEMORY_R_CLASSES.clearOverride()
    } finally {
      super.tearDown()
    }
  }

  fun testFindClass() {
    val javaPsiFacade = JavaPsiFacade.getInstance(project)
    assertThat(javaPsiFacade.findClass("p1.p2.R", myModule.moduleScope)).named("module R class").isNotNull()
    assertThat(javaPsiFacade.findClass("p1.p2.R.string", myModule.moduleScope)).named("existing subclass").isNotNull()
    assertThat(javaPsiFacade.findClass("p1.p2.R.color", myModule.moduleScope)).named("non-existing subclass").isNull()
  }

  fun testInnerClassesCompletion() {
    myFixture.configureByText(
      "/src/p1/p2/MainActivity.java",
      // language=java
      """
        package p1.p2;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(R.${caret});
            }
        }
      """.trimIndent()
    )

    myFixture.completeBasic()

    // TODO(b/80425291): remove "sample"
    assertThat(myFixture.lookupElementStrings).containsExactly("class", "string", "sample")
  }

  fun testTopLevelClassCompletion() {
    val activity = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      """
        package p1.p2;

        import android.app.Activity;
        import android.os.Bundle;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                getResources().getString(p1.p2.${caret});
            }
        }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
  }
}
