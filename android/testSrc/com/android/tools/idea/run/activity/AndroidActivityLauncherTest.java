/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import junit.framework.TestCase;

public class AndroidActivityLauncherTest extends TestCase {
  public void testActivityPath() {
    assertEquals("com.foo/.Debug", AndroidActivityLauncher.getLauncherActivityPath("com.foo", ".Debug"));
    assertEquals("com.foo/a.b\\$Launcher", AndroidActivityLauncher.getLauncherActivityPath("com.foo", "a.b$Launcher"));
  }

  public void testAmStartCommand() {
    assertEquals("am start -n \"com.foo/.Launch\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
                 AndroidActivityLauncher.getStartActivityCommand("com.foo/.Launch", ""));
    assertEquals("am start -n \"com.foo/.Launch\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D",
                 AndroidActivityLauncher.getStartActivityCommand("com.foo/.Launch", "-D"));
    assertEquals("am start -n \"com.foo/.Launch\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D --es aa 'bb'",
                 AndroidActivityLauncher.getStartActivityCommand("com.foo/.Launch", "-D --es aa 'bb'"));
  }
}
