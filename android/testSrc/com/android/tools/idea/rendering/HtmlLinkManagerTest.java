/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicBoolean;

public class HtmlLinkManagerTest extends TestCase {
  public void testRunnable() {
    HtmlLinkManager manager = new HtmlLinkManager();
    final AtomicBoolean result1 = new AtomicBoolean(false);
    final AtomicBoolean result2= new AtomicBoolean(false);
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        result1.set(true);
      }
    };
    Runnable runnable2 = new Runnable() {
      @Override
      public void run() {
        result2.set(true);
      }
    };
    String url1 = manager.createRunnableLink(runnable1);
    String url2 = manager.createRunnableLink(runnable2);
    assertFalse(result1.get());
    manager.handleUrl(url1, null, null, null, null);
    assertTrue(result1.get());
    assertFalse(result2.get());
    result1.set(false);
    manager.handleUrl(url2, null, null, null, null);
    assertFalse(result1.get());
    assertTrue(result2.get());
  }
}
