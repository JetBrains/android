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
package com.android.tools.idea.npw;

import static com.android.tools.idea.npw.project.GradleAndroidModuleTemplate.createDefaultTemplateAt;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.junit.Test;

public final class GradleModuleTemplateTest {

  @Test
  public void testDefaultSourceSetAtCurrentDir() {
    NamedModuleTemplate moduleTemplate = createDefaultTemplateAt(new File("."));
    AndroidModulePaths paths = moduleTemplate.getPaths();

    assertEquals("main", moduleTemplate.getName());
    assertEquals(new File("."), paths.getModuleRoot());
    assertEquals(new File("./src/main/java/my/package"), paths.getSrcDirectory("my.package"));
    assertEquals(new File("./src/androidTest/java/my/package"), paths.getTestDirectory("my.package"));
    assertEquals(new File("./src/main/aidl/my/package"), paths.getAidlDirectory("my.package"));
    assertEquals(new File("./src/main"), paths.getManifestDirectory());
    assertEquals(ImmutableList.of(new File("./src/main/res")), paths.getResDirectories());
  }

  @Test
  public void testDefaultSourceSetAtInvalidDir() {
    // AndroidModuleTemplate are not expected to do validation of its file path, so no exception is expected
    // (This is not a requirement, but helps the UI to bind with a field that can temporarily hold an invalid value)
    for (String str : new String[] {":", "<", ">", "?", "\0"}) {
      assertEquals(createDefaultTemplateAt(new File(str)).getPaths().getModuleRoot(), new File(str));
    }
  }
}
