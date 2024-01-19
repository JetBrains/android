/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT
import com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START
import com.android.SdkConstants.ATTR_LAYOUT_BELOW
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.CHECK_BOX
import com.android.SdkConstants.RELATIVE_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.intellij.lang.annotations.Language
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class NlIdRenamingTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule val edtRule = EdtRule()

  private var componentStack: ComponentStack? = null

  @Before
  fun setUp() {
    componentStack = ComponentStack(projectRule.project)
  }

  @After
  fun tearDown() {
    componentStack!!.restore()
    componentStack = null
  }

  @Test
  fun testSetValueChangeReferences() {
    val util =
      SupportTestUtil(projectRule, createTestLayout()).selectById("textView").clearSnapshots()
    val property = util.makeIdProperty()

    // Perform renaming
    property.value = "label"

    // Verify renaming results
    assertThat(util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW))
      .isEqualTo("@id/label")
    assertThat(
        util.findSiblingById("checkBox1")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)
      )
      .isEqualTo("@id/label")
    assertThat(
        util.findSiblingById("checkBox2")!!.getAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF)
      )
      .isEqualTo("@id/label")
  }

  @Test
  fun testSetResourceLightFields() {
    addManifest(projectRule.fixture)
    val activityFile = projectRule.fixture.addFileToProject("src/MainActivity.java", testActivity)
    val util =
      SupportTestUtil(projectRule, createTestLayout()).selectById("checkBox1").clearSnapshots()
    val property = util.makeIdProperty()

    // Perform renaming
    property.value = "checkBox30"

    // Verify that the reference in the activity file was renamed
    assertThat(activityFile.text).contains("findViewById(R.id.checkBox30)")
  }

  private fun createTestLayout(): ComponentDescriptor =
    ComponentDescriptor(RELATIVE_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        ComponentDescriptor(TEXT_VIEW)
          .withBounds(0, 0, 100, 100)
          .id("@+id/textView")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_LEFT, "true")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_ALIGN_PARENT_START, "true"),
        ComponentDescriptor(CHECK_BOX)
          .withBounds(0, 0, 200, 200)
          .id("@+id/checkBox1")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@id/textView")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF, "@id/textView"),
        ComponentDescriptor(CHECK_BOX)
          .withBounds(0, 0, 200, 300)
          .id("@+id/checkBox2")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, "100dp")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_BELOW, "@id/button1")
          .withAttribute(ANDROID_URI, ATTR_LAYOUT_TO_RIGHT_OF, "@id/textView"),
      )

  @Language("JAVA")
  private val testActivity =
    """
    package com.example;

    import android.app.Activity;
    import android.os.Bundle;
    import android.widget.CheckBox;

    public class MainActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.a_layout);
            CheckBox toolbar = findViewById(R.id.checkBox1);
        }
    }
    """
      .trimIndent()
}
