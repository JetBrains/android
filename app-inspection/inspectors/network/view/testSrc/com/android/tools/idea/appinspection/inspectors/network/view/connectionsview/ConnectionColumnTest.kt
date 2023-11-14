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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

class ConnectionColumnTest {
  @Test
  fun getValueFrom_name_withQuery() {
    assertThat(
        ConnectionColumn.NAME.getValueFrom(
          httpData("www.google.com/l1/l2/test?query=1&other_query=2")
        )
      )
      .isEqualTo("test?query=1&other_query=2")
  }

  @Test
  fun getValueFrom_name_withEndingSlash() {
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/l1/l2/test/")))
      .isEqualTo("test")
  }

  @Test
  fun getValueFrom_name_withEmptyPath() {
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com")))
      .isEqualTo("www.google.com")
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/")))
      .isEqualTo("www.google.com")
  }

  @Test
  fun getValueFrom_name_pathWithSpaces() {
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/test test")))
      .isEqualTo("test test")
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/test%20test")))
      .isEqualTo("test test")
    assertThat(
        ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/test%252520test"))
      )
      .isEqualTo("test test")
  }

  @Test
  fun getValueFrom_name_queryWithSpaces() {
    val url =
      "https://www.google.com/test?query1%25253DHello%252520World%252526query2%25253D%252523Goodbye%252523"
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData(url)))
      .isEqualTo("test?query1=Hello World&query2=#Goodbye#")
  }

  @Test
  fun getValueFrom_name_queryWithSpaces_invalidUrlsReturnsTextAfterLastSlash() {
    // "%25-2" doesn't decode correctly
    // 1. test%25-2test -> test%-2test
    // 2. test%-2test -> can't decode -2 so throws an exception
    assertThat(
        ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/a/b/c/test%25-2test"))
      )
      .isEqualTo("test%-2test")
    assertThat(
        ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/a/b/c/test%25-2test/"))
      )
      .isEqualTo("test%-2test")
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData(("this.is.an.invalid.url/test"))))
      .isEqualTo("test")
  }

  @Test
  fun getValueFrom_name_queryWithSpaces_invalidUrlsReturnedInFullUrl() {
    assertThat(ConnectionColumn.NAME.getValueFrom(httpData("this.is.an.invalid.url")))
      .isEqualTo("this.is.an.invalid.url")
  }

  // If it wasn't handled properly, the | character would cause a URI syntax exception
  @Test
  fun getValueFrom_name_urlNameCanHandlePipeCharacter() {
    assertThat(
        ConnectionColumn.NAME.getValueFrom(httpData("https://www.google.com/q?prop=hello|world"))
      )
      .isEqualTo("q?prop=hello|world")
  }

  @Test
  fun getValueFrom_transport() {
    assertThat(ConnectionColumn.TRANSPORT.getValueFrom(httpData("", HttpTransport.JAVA_NET)))
      .isEqualTo("Java Native")
    assertThat(ConnectionColumn.TRANSPORT.getValueFrom(httpData("", HttpTransport.OKHTTP2)))
      .isEqualTo("OkHttp 2")
    assertThat(ConnectionColumn.TRANSPORT.getValueFrom(httpData("", HttpTransport.OKHTTP3)))
      .isEqualTo("OkHttp 3")
    assertThat(ConnectionColumn.TRANSPORT.getValueFrom(httpData("", HttpTransport.UNRECOGNIZED)))
      .isEqualTo("Unknown")
    assertThat(ConnectionColumn.TRANSPORT.getValueFrom(httpData("", HttpTransport.UNDEFINED)))
      .isEqualTo("Unknown")
  }
}

private fun httpData(url: String, transport: HttpTransport = HttpTransport.UNDEFINED) =
  HttpData.createHttpData(id = 1, url = url, transport = transport)
