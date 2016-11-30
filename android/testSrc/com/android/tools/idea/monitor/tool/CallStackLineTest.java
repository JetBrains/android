/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.monitor.tool;

import com.intellij.openapi.project.Project;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class CallStackLineTest {
  private CallStackLine myStackLine;

  @Test
  public void getLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:274)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals(273, myStackLine.getLineNumber());
  }

  @Test
  public void getNoLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals(-1, myStackLine.getLineNumber());
  }

  @Test
  public void getInvalidLineNumber() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:init)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals(-1, myStackLine.getLineNumber());
  }

  @Test
  public void getClassName() {
    String line = "com.example.android.displayingbitmaps.util.ImageFetcher.downloadUrlToStream(ImageFetcher.java:27)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals("com.example.android.displayingbitmaps.util.ImageFetcher", myStackLine.getClassName());
  }

  @Test
  public void getClassNameIfNested() {
    String line = "com.example.android.displayingbitmaps.util.ImageWorker$BitmapWorkerTask.doInBackground(ImageWorker.java:312)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals("com.example.android.displayingbitmaps.util.ImageWorker", myStackLine.getClassName());
  }

  @Test
  public void getClassNameIfAnonymous() {
    String line = "com.example.android.displayingbitmaps.util.AsyncTask$2.call(AsyncTask.java:313)";
    myStackLine = new CallStackLine(Mockito.mock(Project.class), line);
    assertEquals("com.example.android.displayingbitmaps.util.AsyncTask", myStackLine.getClassName());
  }
}