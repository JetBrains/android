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
package com.android.tools.inspectors.common.api.stacktrace;

import static com.android.tools.idea.codenavigation.CodeLocation.INVALID_LINE_NUMBER;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.codenavigation.CodeLocation;
import org.junit.Test;

public class StackFrameParserTest {
  @Test
  public void getLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);

    // The file is 1-based, but the code location is 0-indexed, so when we call getLineNumber(), it
    // should be one less than what the raw string contains.
    assertEquals(273, codeLocation.getLineNumber());
  }

  @Test
  public void getNoLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);
    assertEquals(INVALID_LINE_NUMBER, codeLocation.getLineNumber());
  }

  @Test
  public void getInvalidLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);
    assertEquals(INVALID_LINE_NUMBER, codeLocation.getLineNumber());
  }

  @Test
  public void getClassName() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);
    assertEquals("com.example.android.displayingbitmaps.util.ImageFetcher", codeLocation.getClassName());
  }

  @Test
  public void getClassNameIfNested() {
    String line = "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);
    assertEquals("com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask", codeLocation.getClassName());
  }

  @Test
  public void getClassNameIfAnonymous() {
    String line = "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)";
    CodeLocation codeLocation = StackFrameParser.parseFrame(line);
    assertEquals("com.example.android.displayingbitmaps.util.AsyncTask$2", codeLocation.getClassName());
  }
}
