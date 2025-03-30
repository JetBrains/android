/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.stacktrace

import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.inspectors.common.api.stacktrace.StackFrameParser.parseFrame
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StackFrameParserTest {
  @Test
  fun parsesFullFrame() {
    parseFrame("com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)").apply {
      assertThat(this).isNotNull()
      assertThat(this!!.className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
      assertThat(methodName).isEqualTo("downloadUrlToStream")
      assertThat(fileName).isEqualTo("ImageFetcher.java")
      // The line number should be one less since it will be converted from 1-base to 0-base.
      assertThat(lineNumber).isEqualTo(273)
    }
  }

  @Test
  fun parsesFrameWithoutLineNumber() {
    parseFrame("com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)").apply {
      assertThat(this).isNotNull()
      assertThat(this!!.className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
      assertThat(methodName).isEqualTo("downloadUrlToStream")
      assertThat(fileName).isEqualTo("ImageFetcher.java")
      assertThat(lineNumber).isEqualTo(CodeLocation.INVALID_LINE_NUMBER)
    }
  }

  @Test
  fun parsesFrameWithInvalidLineNumber() {
    parseFrame("com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)").apply {
      assertThat(this).isNotNull()
      assertThat(this!!.className).isEqualTo("com.example.android.displayingbitmaps.util.ImageFetcher")
      assertThat(methodName).isEqualTo("downloadUrlToStream")
      assertThat(fileName).isEqualTo("ImageFetcher.java")
      assertThat(lineNumber).isEqualTo(CodeLocation.INVALID_LINE_NUMBER)
    }
  }

  @Test
  fun parsesFrameWithNestedClassName() {
    parseFrame("com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask.doInBackground(ImageWorker.java:312)").apply {
      assertThat(this).isNotNull()
      assertThat(this!!.className).isEqualTo("com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask")
      assertThat(methodName).isEqualTo("doInBackground")
    }
  }

  @Test
  fun parsesFrameWithAnonymousClassName() {
    parseFrame("com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)").apply {
      assertThat(this).isNotNull()
      assertThat(this!!.className).isEqualTo("com.example.android.displayingbitmaps.util.AsyncTask$2")
      assertThat(methodName).isEqualTo("call")
    }
  }

  @Test
  fun parsesStack() {
    val locations = StackFrameParser.parseStack(listOf(
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)",
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)",
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)",  // invalid line number, but valid code location
      "com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask.doInBackground(ImageWorker.java:312)",
      "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)",
    ).joinToString("\n"))

    assertThat(locations).containsExactly(
      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageFetcher").apply {
        setMethodName("downloadUrlToStream")
        setFileName("ImageFetcher.java")
        setLineNumber(273)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageFetcher").apply {
        setMethodName("downloadUrlToStream")
        setFileName("ImageFetcher.java")
        setLineNumber(CodeLocation.INVALID_LINE_NUMBER)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageFetcher").apply {
        setMethodName("downloadUrlToStream")
        setFileName("ImageFetcher.java")
        setLineNumber(CodeLocation.INVALID_LINE_NUMBER)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask").apply {
        setMethodName("doInBackground")
        setFileName("ImageWorker.java")
        setLineNumber(311)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.AsyncTask$2").apply {
        setMethodName("call")
        setFileName("AsyncTask.java")
        setLineNumber(312)
      }.build(),
    )
  }

  @Test
  fun parsesStackIgnoresInvalidLines() {
    val locations = StackFrameParser.parseStack(listOf(
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream()",  // invalid code location - should be ignored
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)",
      "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)",  // invalid line number, but valid code location
      "com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask.doInBackground(ImageWorker.java:312)",
      "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)",
    ).joinToString("\n"))

    assertThat(locations).containsExactly(
      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageFetcher").apply {
        setMethodName("downloadUrlToStream")
        setFileName("ImageFetcher.java")
        setLineNumber(CodeLocation.INVALID_LINE_NUMBER)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageFetcher").apply {
        setMethodName("downloadUrlToStream")
        setFileName("ImageFetcher.java")
        setLineNumber(CodeLocation.INVALID_LINE_NUMBER)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask").apply {
        setMethodName("doInBackground")
        setFileName("ImageWorker.java")
        setLineNumber(311)
      }.build(),

      CodeLocation.Builder("com.example.android.displayingbitmaps.util.AsyncTask$2").apply {
        setMethodName("call")
        setFileName("AsyncTask.java")
        setLineNumber(312)
      }.build(),
    )
  }
}
