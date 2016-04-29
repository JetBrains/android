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
package com.android.tools.idea.fd;

import com.google.common.collect.ImmutableSet;
import org.intellij.lang.annotations.Language;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstantRunManagerTest {
  @Test
  public void multiProcessCheck() {
    @Language("XML") String manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertFalse(InstantRunManager.manifestSpecifiesMultiProcess(manifest, ImmutableSet.<String>of()));

    manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process = \":foo\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertTrue(InstantRunManager.manifestSpecifiesMultiProcess(manifest, ImmutableSet.<String>of()));

    manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process=\":leakcanary\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertFalse(InstantRunManager.manifestSpecifiesMultiProcess(manifest, ImmutableSet.of(":leakcanary")));

    manifest =
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process=\":leakcanary\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n" +
      "        <activity\n" +
      "            android:name=\".MainActivity\"\n" +
      "            android:process =\":foo\"\n" +
      "            android:theme=\"@style/AppTheme.NoActionBar\">\n" +
      "        </activity>\n";
    assertTrue(InstantRunManager.manifestSpecifiesMultiProcess(manifest, ImmutableSet.of(":leakcanary")));
  }
}
