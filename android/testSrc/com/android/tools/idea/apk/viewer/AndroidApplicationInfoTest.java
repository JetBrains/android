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
package com.android.tools.idea.apk.viewer;

import org.junit.Test;

import static org.junit.Assert.*;

public class AndroidApplicationInfoTest {
  @Test
  public void parseManifest() throws Exception {
    AndroidApplicationInfo info = AndroidApplicationInfo.parse("N: android=http://schemas.android.com/apk/res/android\n" +
                                                                "  E: manifest (line=13)\n" +
                                                                "    A: android:versionCode(0x0101021b)=(type 0x10)0x101dfde9\n" +
                                                                "    A: android:versionName(0x0101021c)=\"51.0.2704.10\" (Raw: \"51.0.2704.10\")\n" +
                                                                "    A: package=\"com.android.chrome\" (Raw: \"com.android.chrome\")\n" +
                                                                "    A: platformBuildVersionCode=(type 0x10)0x17 (Raw: \"23\")\n" +
                                                                "    A: platformBuildVersionName=\"N\" (Raw: \"N\")\n");
    assertEquals("com.android.chrome", info.packageId);
    assertEquals("51.0.2704.10", info.versionName);
  }
}