/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.stacktrace;

import org.junit.Test;

import static com.android.tools.profilers.stacktrace.CodeLocation.INVALID_LINE_NUMBER;
import static org.junit.Assert.assertEquals;

public class StackFrameParserTest {
  @Test
  public void getLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    assertEquals(274, new StackFrameParser(line).getLineNumber());
  }

  @Test
  public void getNoLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)";
    assertEquals(INVALID_LINE_NUMBER, new StackFrameParser(line).getLineNumber());
  }

  @Test
  public void getInvalidLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)";
    assertEquals(INVALID_LINE_NUMBER, new StackFrameParser(line).getLineNumber());
  }

  @Test
  public void getClassName() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)";
    assertEquals("com.example.android.displayingbitmaps.util.ImageFetcher", new StackFrameParser(line).getClassName());
  }

  @Test
  public void getClassNameIfNested() {
    String line = "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)";
    assertEquals("com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask", new StackFrameParser(line).getClassName());
  }

  @Test
  public void getClassNameIfAnonymous() {
    String line = "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)";
    assertEquals("com.example.android.displayingbitmaps.util.AsyncTask$2", new StackFrameParser(line).getClassName());
  }
}
