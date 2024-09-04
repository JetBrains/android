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
package com.android.tools.idea.uibuilder.property.support

import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property.testutils.SupportTestUtil
import com.android.tools.property.panel.api.HeaderEnumValue
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.Callable
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@Language("JAVA")
private const val MAIN_ACTIVITY =
  """
  package p1.p2;

  import android.app.Activity;
  import android.os.Bundle;
  import android.view.View;

  public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {
    }

    public void help(View view) {
    }
  }
"""

@Language("JAVA")
private const val OTHER_ACTIVITY =
  """
  package p1.p2;

  import android.app.Activity;
  import android.os.Bundle;
  import android.view.View;

  public class OtherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);
    }

    public void onClick(View view) {
    }

    public void startProcessing(View view) {
    }
  }
"""

@Language("JAVA")
private const val THIRD_ACTIVITY =
  """
  package p1.p2;

  import android.app.Activity;
  import android.os.Bundle;
  import android.view.View;

  public class ThirdActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);
    }
  }
"""

class OnClickEnumSupportTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun before() {
    val testProjectSystem = TestProjectSystem(projectRule.project)
    testProjectSystem.useInTests()
    testProjectSystem.namespace = "p1.p2"
    FakeBuildSystemFilePreviewServices().register(projectRule.testRootDisposable)
  }

  @Test
  fun testWithNoActivities() {
    val util = SupportTestUtil(projectRule, TEXT_VIEW, parentTag = FRAME_LAYOUT)
    val support = OnClickEnumSupport(util.nlModel)
    val app = ApplicationManager.getApplication()
    val values = app.executeOnPooledThread(Callable { support.values }).get()
    assertThat(values).isEmpty()
  }

  @Test
  fun testWithFullyQualifiedActivityName() {
    val support = findEnumSupportFor("p1.p2.MainActivity")
    val app = ApplicationManager.getApplication()
    val allValues = app.executeOnPooledThread(Callable { support.values }).get()
    assertThat((allValues[0] as HeaderEnumValue).header).isEqualTo("MainActivity")
    val values = allValues.subList(1, allValues.size)
    assertThat(values.map { it.display }).containsExactly("help", "onClick").inOrder()
    assertThat(values.map { it.value }).containsExactly("help", "onClick").inOrder()
  }

  @Test
  fun testWithDotInActivityName() {
    val support = findEnumSupportFor(".MainActivity")
    val app = ApplicationManager.getApplication()
    val allValues = app.executeOnPooledThread(Callable { support.values }).get()
    assertThat((allValues[0] as HeaderEnumValue).header).isEqualTo("MainActivity")
    val values = allValues.subList(1, allValues.size)
    assertThat(values.map { it.display }).containsExactly("help", "onClick").inOrder()
    assertThat(values.map { it.value }).containsExactly("help", "onClick").inOrder()
  }

  @Test
  fun testWithNoActivityName() {
    val support = findEnumSupportFor("")
    val app = ApplicationManager.getApplication()
    val allValues = app.executeOnPooledThread(Callable { support.values }).get()
    assertThat((allValues[0] as HeaderEnumValue).header).isEqualTo("MainActivity")
    val mainValues = allValues.subList(1, 3)
    assertThat((allValues[3] as HeaderEnumValue).header).isEqualTo("OtherActivity")
    val otherValues = allValues.subList(4, 6)
    assertThat(mainValues.map { it.display }).containsExactly("help", "onClick").inOrder()
    assertThat(mainValues.map { it.value }).containsExactly("help", "onClick").inOrder()
    assertThat(otherValues.map { it.display })
      .containsExactly("onClick", "startProcessing")
      .inOrder()
    assertThat(otherValues.map { it.value }).containsExactly("onClick", "startProcessing").inOrder()
    assertThat(allValues.size).isEqualTo(6)
  }

  private fun findEnumSupportFor(activityName: String): OnClickEnumSupport {
    val fixture = projectRule.fixture
    fixture.addClass(MAIN_ACTIVITY.trimIndent())
    fixture.addClass(OTHER_ACTIVITY.trimIndent())
    fixture.addClass(THIRD_ACTIVITY.trimIndent())
    val util =
      SupportTestUtil(projectRule, TEXT_VIEW, parentTag = FRAME_LAYOUT, activityName = activityName)
    return OnClickEnumSupport(util.nlModel)
  }
}
