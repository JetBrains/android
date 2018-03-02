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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase

@Language("JAVA")
private const val MAIN_ACTIVITY = """
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
private const val OTHER_ACTIVITY = """
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

class OnClickEnumSupportTest: AndroidTestCase() {

  fun testWithNoActivities() {
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW, FRAME_LAYOUT)
    val support = OnClickEnumSupport(util.nlModel)
    assertThat(support.values).isEmpty()
  }

  fun testWithFullyQualifiedActivityName() {
    val support = findEnumSupportFor("p1.p2.MainActivity")
    assertThat(support.values.map { it.display }).containsExactly("onClick", "help")
    assertThat(support.values.map { it.value }).containsExactly("onClick", "help")
  }

  fun testWithDotInActivityName() {
    val support = findEnumSupportFor(".MainActivity")
    assertThat(support.values.map { it.display }).containsExactly("onClick", "help")
    assertThat(support.values.map { it.value }).containsExactly("onClick", "help")
  }

  fun testWithNoActivityName() {
    val support = findEnumSupportFor("")
    assertThat(support.values.map { it.display }).containsExactly("onClick", "startProcessing", "onClick", "help").inOrder()
    assertThat(support.values.map { it.value }).containsExactly("onClick", "startProcessing", "onClick", "help").inOrder()
    assertThat(support.values[0].header).isEqualTo("OtherActivity")
    assertThat(support.values[1].header).isEmpty()
    assertThat(support.values[2].header).isEqualTo("MainActivity")
    assertThat(support.values[3].header).isEmpty()
  }

  private fun findEnumSupportFor(activityName: String): OnClickEnumSupport {
    myFixture.addClass(MAIN_ACTIVITY.trimIndent())
    myFixture.addClass(OTHER_ACTIVITY.trimIndent())
    val util = SupportTestUtil(testRootDisposable, myFacet, myFixture, TEXT_VIEW, FRAME_LAYOUT, activityName)
    return OnClickEnumSupport(util.nlModel)
  }
}
