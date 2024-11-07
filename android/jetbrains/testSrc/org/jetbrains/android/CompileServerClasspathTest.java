/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android;

import static org.junit.Assert.assertFalse;

import com.intellij.compiler.server.impl.BuildProcessClasspathManager;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class CompileServerClasspathTest extends BareTestFixtureTestCase {
  @org.junit.Ignore("b/214269581")
  @Test
  public void testCompileServerClasspath() {
    Set<String> libs = getBuildProcessClasspath();
    assertFalse(libs.isEmpty());
  }

  @NotNull
  private Set<String> getBuildProcessClasspath() {
    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    @NotNull List<String> pluginsCp = new BuildProcessClasspathManager(getTestRootDisposable()).getBuildProcessClasspath(project);
    return pluginsCp.stream()
      .map(it -> LightJavaModule.moduleName(new File(it).getName()))
      .collect(Collectors.toSet());
  }
}
