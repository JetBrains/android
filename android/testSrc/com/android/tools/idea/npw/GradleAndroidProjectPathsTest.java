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
package com.android.tools.idea.npw;

import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths.createDefaultSourceSetAt;
import static org.junit.Assert.assertEquals;

public final class GradleAndroidProjectPathsTest {

  @Test
  public void testDefaultSourceSetAtCurrentDir() {
    AndroidSourceSet sourceSet = createDefaultSourceSetAt(new File("."));
    AndroidProjectPaths paths = sourceSet.getPaths();

    assertEquals("main", sourceSet.getName());
    assertEquals(paths.getModuleRoot(), new File("."));
    assertEquals(paths.getSrcDirectory("my.package"), new File("./src/main/java/my/package"));
    assertEquals(paths.getTestDirectory("my.package"), new File("./src/androidTest/java/my/package"));
    assertEquals(paths.getAidlDirectory("my.package"), new File("./src/main/aidl/my/package"));
    assertEquals(paths.getManifestDirectory(), new File("./src/main"));
    assertEquals(paths.getResDirectory(), new File("./src/main/res"));
  }

  @Test
  public void testDefaultSourceSetAtInvalidDir() {
    // AndroidProjectPaths are not expected to do validation of its file path, so no exception is expected
    // (This is not a requirement, but helps the UI to bind with a field that can temporarily hold an invalid value)
    for (String str : new String[] {":", "<", ">", "?", "\0"}) {
      assertEquals(createDefaultSourceSetAt(new File(str)).getPaths().getModuleRoot(), new File(str));
    }
  }
}
