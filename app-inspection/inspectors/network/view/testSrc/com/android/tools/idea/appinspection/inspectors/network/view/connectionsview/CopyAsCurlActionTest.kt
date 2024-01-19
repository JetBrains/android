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
package com.android.tools.idea.appinspection.inspectors.network.view.connectionsview

import com.android.tools.idea.appinspection.inspectors.network.model.connections.HttpData
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.FakeClipboard
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TestActionEvent
import java.util.Base64
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.Header

/** Tests for [CopyAsCurlAction] */
class CopyAsCurlActionTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val fakeClipboard = FakeClipboard()

  private val actionEvent by lazy(NONE) { TestActionEvent.createTestEvent() }

  @Test
  fun update_missingUrl() {
    copyAsCurlAction(url = "", method = "GET").update(actionEvent)

    assertThat(actionEvent.presentation.isEnabled).isFalse()
  }

  @Test
  fun update_missingMethod() {
    val action = copyAsCurlAction(url = "http://google.com", method = "")

    action.update(actionEvent)

    assertThat(actionEvent.presentation.isEnabled).isFalse()
  }

  @Test
  fun update() {
    val action = copyAsCurlAction(url = "http://google.com", method = "GET")

    action.update(actionEvent)

    assertThat(actionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionPerformed() {
    val action = copyAsCurlAction(url = "http://google.com", method = "GET")

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getTextContents())
      .isEqualTo(
        """
          curl 'http://google.com' \
            --compressed
        """
          .trimIndent()
      )
  }

  @Test
  fun actionPerformed_withNonDefaultMethod() {
    val action = copyAsCurlAction(url = "http://google.com", method = "PUT")

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getTextContents())
      .isEqualTo(
        """
          curl 'http://google.com' \
            -X 'PUT' \
            --compressed
        """
          .trimIndent()
      )
  }

  @Test
  fun actionPerformed_withPayload() {
    val action =
      copyAsCurlAction(url = "http://google.com", method = "PUT", payload = "payload".toByteArray())

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getTextContents())
      .isEqualTo(
        """
          curl 'http://google.com' \
            -X 'PUT' \
            --data-raw 'payload' \
            --compressed
        """
          .trimIndent()
      )
  }

  @Test
  fun actionPerformed_withBinaryPayload() {
    val payload = Random(0).nextBytes(10)
    val payloadBase64 = Base64.getEncoder().encodeToString(payload)
    val action = copyAsCurlAction(url = "http://google.com", method = "PUT", payload = payload)

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getTextContents())
      .isEqualTo(
        """
          echo -n $payloadBase64 | base64 -d | curl 'http://google.com' \
            -X 'PUT' \
            --data-binary @- \
            --compressed
        """
          .trimIndent()
      )
  }

  @Test
  fun actionPerformed_withHeaders() {
    val action =
      copyAsCurlAction(
        url = "http://google.com",
        method = "PUT",
        payload = "payload".toByteArray(),
        header("header1", "value1"),
        header("header2", "value2.1", "value2.2"),
      )

    action.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(fakeClipboard.getTextContents())
      .isEqualTo(
        """
          curl 'http://google.com' \
            -X 'PUT' \
            -H 'header1: value1' \
            -H 'header2: value2.1, value2.2' \
            --data-raw 'payload' \
            --compressed
        """
          .trimIndent()
      )
  }

  private fun copyAsCurlAction(
    url: String,
    method: String,
    payload: ByteArray = ByteArray(0),
    vararg headers: Header,
  ) =
    CopyAsCurlAction(
      HttpData.createHttpData(
        0,
        url = url,
        method = method,
        requestPayload = ByteString.copyFrom(payload),
        requestHeaders = headers.asList(),
      )
    ) {
      fakeClipboard
    }
}

private fun header(name: String, vararg values: String) =
  Header.newBuilder().setKey(name).addAllValues(values.asIterable()).build()
