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
import org.junit.Assert
import org.junit.Test

class StackFrameParserTest {
  @Test
  fun getLineNumber() {
    val line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)"
    val codeLocation = parseFrame(line)

    // The file is 1-based, but the code location is 0-indexed, so when we call getLineNumber(), it
    // should be one less than what the raw string contains.
    Assert.assertEquals(273, codeLocation.lineNumber.toLong())
  }

  @Test
  fun getNoLineNumber() {
    val line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)"
    val codeLocation = parseFrame(line)
    Assert.assertEquals(CodeLocation.INVALID_LINE_NUMBER.toLong(), codeLocation.lineNumber.toLong())
  }

  @Test
  fun getInvalidLineNumber() {
    val line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)"
    val codeLocation = parseFrame(line)
    Assert.assertEquals(CodeLocation.INVALID_LINE_NUMBER.toLong(), codeLocation.lineNumber.toLong())
  }

  @Test
  fun getClassName() {
    val line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)"
    val codeLocation = parseFrame(line)
    Assert.assertEquals("com.example.android.displayingbitmaps.util.ImageFetcher", codeLocation.className)
  }

  @Test
  fun getClassNameIfNested() {
    val line = "com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask.doInBackground(ImageWorker.java:312)"
    val codeLocation = parseFrame(line)
    Assert.assertEquals("com.example.android.displayingbitmaps.util.ImageWorker\$BitmapWorkerTask", codeLocation.className)
  }

  @Test
  fun getClassNameIfAnonymous() {
    val line = "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)"
    val codeLocation = parseFrame(line)
    Assert.assertEquals("com.example.android.displayingbitmaps.util.AsyncTask$2", codeLocation.className)
  }
}