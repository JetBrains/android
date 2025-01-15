// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dcl.lang.wrapper;

import com.android.test.testutils.TestUtils;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.List;

public class GradleBuildFileTest extends LightPlatformTestCase {
  public void testTest() {
    String w = TestUtils.resolveWorkspacePath("tools/adt/idea/gradle-declarative-lang/testData/dcl/wrapper/").toString();
    GradleBuildFile g = new GradleBuildFile(w + "\\test.dcl", getProject());
    int version = g.getJavaVersion();
    assertEquals(21, version);

    String mainClass = g.getJavaMainClass();
    assertEquals("com.example.App", mainClass);

    List<String> dependencies = g.getJavaDependencies();
    assertEquals(2, dependencies.size());
    assertEquals("implementation(project(\":java-util\"))", dependencies.get(0));
    assertEquals("implementation(\"com.google.guava:guava:32.1.3-jre\")", dependencies.get(1));
  }

}
