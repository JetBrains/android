/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import junit.framework.TestCase;

public class WelcomeUIUtilsTest extends TestCase {

  public void testGetMessageWithDetails() throws Exception {
    assertEquals("message.", WelcomeUIUtils.getMessageWithDetails("message", null));
    assertEquals("message.", WelcomeUIUtils.getMessageWithDetails("message", ""));
    assertEquals("message.", WelcomeUIUtils.getMessageWithDetails("message", " "));
    assertEquals("message.", WelcomeUIUtils.getMessageWithDetails("message", "\n"));
    assertEquals("message: details.", WelcomeUIUtils.getMessageWithDetails("message", "details"));
    assertEquals("message: details.", WelcomeUIUtils.getMessageWithDetails("message", "details."));
  }
}