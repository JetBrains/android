// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import static org.junit.Assert.assertFalse;

import com.google.common.collect.Streams;
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
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.junit.Test;

public class CompileServerClasspathTest extends BareTestFixtureTestCase {
  @Test
  public void testCompileServerClasspath() {
    Set<String> libs = getBuildProcessClasspath();
    assertFalse(libs.isEmpty());
  }

  @NotNull
  private Set<String> getBuildProcessClasspath() {
    List<String> baseCp = ClasspathBootstrap.getBuildProcessApplicationClasspath();
    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    @NotNull List<String> pluginsCp = new BuildProcessClasspathManager(getTestRootDisposable()).getBuildProcessPluginsClasspath(project);
    return Streams.concat(baseCp.stream(), pluginsCp.stream())
      .map(it -> LightJavaModule.moduleName(new File(it).getName()))
      .collect(Collectors.toSet());
  }
}
