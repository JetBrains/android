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
package com.google.gct.studio.samples

import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.appspot.gsamplesindex.samplesindex.SamplesIndex
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.replaceService
import com.sun.net.httpserver.HttpServer
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SampleImportActionTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk().onEdt()

  private val LOCALHOST = "127.0.0.1"

  private lateinit var server: HttpServer
  private lateinit var url: String
  private lateinit var localService: SamplesService

  @Before
  fun setUp() {
    // Set up server.
    server = HttpServer.create()
    with(server) {
      bind(InetSocketAddress(LOCALHOST, 0), 0)
      start()
      url = "http://$LOCALHOST:${address.port}"

      createContext(
        path = "/${SamplesService.DEFAULT_SERVICE_PATH}",
        content = SAMPLE_CONTENT,
        rCode = HttpURLConnection.HTTP_OK
      )
    }

    localService = LocalTestSamplesService(url)
    ApplicationManager.getApplication()
      .replaceService(SamplesService::class.java, localService, projectRule.testRootDisposable)

    enableHeadlessDialogs(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    server.stop(0)
  }

  @Test
  fun `can invoke SampleImportAction`() {
    val action = SampleImportAction()
    val event = createTestEvent()

    createModalDialogAndInteractWithIt({ action.actionPerformed(event) }) {
      it.close(DialogWrapper.CANCEL_EXIT_CODE)
    }
  }

  private fun HttpServer.createContext(path: String, content: String, rCode: Int, rLen: Long = 0) {
    synchronized(this) {
      createContext(path) { exchange ->
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(rCode, rLen)
        exchange.responseBody.write(content.toByteArray(StandardCharsets.UTF_8))
        exchange.close()
      }
    }
  }
}

private class LocalTestSamplesService(private val rootUrl: String) : SamplesService() {
  override fun getIndex(): SamplesIndex {
    return getIndex(rootUrl, DEFAULT_SERVICE_PATH)
  }
}

private val SAMPLE_CONTENT =
  """
  {
   "items": [
    {
     "id": "tensorflow/examples//master/lite/examples/image_classification/android_play_services",
     "title": "Tensor​Flow Lite in Play Services image classification Android example application",
     "status": "PUBLISHED",
     "level": "INTERMEDIATE",
     "technologies": [
      "android",
      "tensorflow",
      "google play services"
     ],
     "categories": [
      "artificial intelligence",
      "machine learning"
     ],
     "languages": [
      "java",
      "kotlin"
     ],
     "solutions": [
      "mobile"
     ],
     "cloneUrl": "https://github.com/tensorflow/examples/",
     "github": "tensorflow-examples",
     "branch": "master",
     "path": "lite/examples/image_classification/android_play_services/",
     "description": "This example performs real-time image classification on live camera frames.",
     "screenshots": [
      {
       "link": "https://storage.googleapis.com/download.tensorflow.org/tflite/examples/android_play_services_demo.gif",
       "primary": true
      },
      {
       "link": "https://raw.github.com/tensorflow/examples//master/lite/examples/image_classification/android_play_services//screenshots/screenshot-1.jpg",
       "primary": false
      }
     ],
     "icon": "screenshots/icon-web.png",
     "apiRefs": [
      {
       "namespace": "android",
       "name": "com.google.android.gms.tflite.java.TfLite",
       "link": "https://developer.android.com/reference/com/google/android/gms/tflite/java/TfLite.html"
      }
     ],
     "license": {
      "name": "apache2",
      "link": "http://www.apache.org/licenses/LICENSE-2.0.html"
     }
    }
   ]
  }
"""
    .trimIndent()
