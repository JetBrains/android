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
package com.android.tools.idea.gradle.util;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.List;

/**
 * Tests for {@link GradleBuilds}.
 */
public class GradleBuildsTest extends TestCase {
  public void testFindAndAddBuildTaskWithSourceGenBuildMode() {
    List<String> tasks = Lists.newArrayList();
    JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();
    properties.SOURCE_GEN_TASK_NAME = "sourceGen";
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.SOURCE_GEN, ":basic", properties, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":basic:sourceGen", tasks.get(0));
  }

  public void testFindAndAddBuildTaskWithRootModel() {
    List<String> tasks = Lists.newArrayList();
    JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();
    properties.ASSEMBLE_TASK_NAME = "";
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.MAKE, ":", properties, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":assemble", tasks.get(0));
  }

  public void testFindAndAddBuildTaskWithSourceGenBuildModeAndNullAndroidFacet() {
    List<String> tasks = Lists.newArrayList();
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.SOURCE_GEN, ":basic", null, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(0, tasks.size());
  }

  public void testFindAndAddBuildTaskWithCompileJavaBuildMode() {
    List<String> tasks = Lists.newArrayList();
    JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();
    properties.COMPILE_JAVA_TASK_NAME = "compileJava";
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.COMPILE_JAVA, ":basic", properties, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":basic:compileJava", tasks.get(0));
  }

  public void testFindAndAddBuildTaskWithMakeBuildMode() {
    List<String> tasks = Lists.newArrayList();
    JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();
    properties.ASSEMBLE_TASK_NAME = "assembleDebug";
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.MAKE, ":basic", properties, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":basic:assembleDebug", tasks.get(0));
  }

  public void testFindAndAddBuildTaskWithRebuildBuildMode() {
    List<String> tasks = Lists.newArrayList();
    JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();
    properties.ASSEMBLE_TASK_NAME = "assembleDebug";
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.REBUILD, ":basic", properties, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":basic:assembleDebug", tasks.get(0));
  }

  public void testFindAndAddBuildTaskWithMakeBuildModeAndNullAndroidFacet() {
    List<String> tasks = Lists.newArrayList();
    GradleBuilds.findAndAddBuildTask("basic", BuildMode.MAKE, ":basic", null, tasks, GradleBuilds.TestCompileType.NONE);

    assertEquals(1, tasks.size());
    assertEquals(":basic:assemble", tasks.get(0));
  }
}
