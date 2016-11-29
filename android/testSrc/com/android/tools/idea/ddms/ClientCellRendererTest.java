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

package com.android.tools.idea.ddms;

import com.android.utils.Pair;
import junit.framework.TestCase;

public class ClientCellRendererTest extends TestCase {

  private static void verifyAppNameRendering(String name, String first, String second) {
    Pair<String, String> p = ClientCellRenderer.splitApplicationName(name);
    assertEquals(first, p.getFirst());
    assertEquals(second, p.getSecond());
  }

  public void testAppNameSplit() {
    verifyAppNameRendering("com.android.settings", "com.android.", "settings");
    verifyAppNameRendering("com.android.", "com.android.", "");
    verifyAppNameRendering("system_process", "", "system_process");
    verifyAppNameRendering("com.google.chrome:sandbox", "com.google.", "chrome:sandbox");
  }
}
