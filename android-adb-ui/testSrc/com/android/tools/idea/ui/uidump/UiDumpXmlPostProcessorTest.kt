/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui.uidump

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UiDumpXmlPostProcessorTest {
  
  @Test
  fun basic() {
    val input =
    // language=XML
      """
      <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
      <hierarchy rotation="0">
        <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
          <node index="0" text="" resource-id="" class="android.widget.LinearLayout" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
            <node index="0" text="" resource-id="android:id/content" class="android.widget.FrameLayout" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
              <node index="0" text="" resource-id="" class="androidx.compose.ui.platform.ComposeView" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][170,126]">
                <node index="0" text="" resource-id="" class="android.view.View" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][170,126]">
                  <node index="0" text="" resource-id="" class="android.view.View" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,1][170,126]">
                    <node index="0" text="Hi &#129313;" resource-id="" class="android.widget.TextView" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[63,32][107,95]" />
                    <node index="1" text="" resource-id="" class="android.widget.Button" package="com.example.myapplication" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,11][170,116]" />
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
      </hierarchy>
      """.trimIndent()

    val expected =
      // language=XML
      """
      <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
      <hierarchy rotation="0">
        <node bounds="[0,0][1080,2400]" class="android.widget.FrameLayout" index="0" package="com.example.myapplication">
          <node bounds="[0,0][1080,2400]" class="android.widget.LinearLayout" index="0" package="com.example.myapplication">
            <node bounds="[0,0][1080,2400]" class="android.widget.FrameLayout" index="0" package="com.example.myapplication" resource-id="android:id/content">
              <node bounds="[0,0][170,126]" class="androidx.compose.ui.platform.ComposeView" index="0" package="com.example.myapplication">
                <node bounds="[0,0][170,126]" class="android.view.View" index="0" package="com.example.myapplication">
                  <node bounds="[0,1][170,126]" class="android.view.View" clickable="true" focusable="true" index="0" package="com.example.myapplication">
                    <node bounds="[63,32][107,95]" class="android.widget.TextView" index="0" package="com.example.myapplication" text="Hi 🤡"/>
                    <node bounds="[0,11][170,116]" class="android.widget.Button" index="1" package="com.example.myapplication"/>
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
      </hierarchy>
      """.trimIndent()

    assertPostProcessing(input, expected)
  }

  @Test
  fun llmInstruction() {
    val instruction = createLlmInstruction()
    val expected =
      """
      For all the attributes listed below, if you don't see it in the input, assume it is the value provided below.
        checkable="false"
        checked="false"
        clickable="false"
        content-desc=""
        enabled="true"
        focusable="false"
        focused="false"
        long-clickable="false"
        password="false"
        resource-id=""
        scrollable="false"
        selected="false"
        text=""
      """.trimIndent() + "\n"
    assertThat(instruction).isEqualTo(expected)
  }

  fun assertPostProcessing(input: String, expected: String) {
    val output = postProcess(input)
    assertThat(output).isEqualTo(expected)
  }
}