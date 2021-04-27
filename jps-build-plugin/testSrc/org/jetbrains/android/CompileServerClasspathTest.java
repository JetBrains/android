// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import static org.junit.Assert.assertFalse;

import com.intellij.compiler.server.CompileServerPlugin;
import com.intellij.compiler.server.impl.BuildProcessClasspathManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.testFramework.ApplicationRule;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CompileServerClasspathTest {

  @Rule
  public ApplicationRule app = new ApplicationRule();

  private Disposable myDisposable;

  @Before
  public void setup() {
    myDisposable = Disposer.newDisposable();
  }

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
  }

  @Test
  public void testCompileServerClasspath() {
    // only keep extensions from this plugin (and not from others like kotlin)
    CompileServerPlugin.EP_NAME.getPoint().unregisterExtensions((classname, adapter) -> {
      return "org.jetbrains.android.jpsBuild".equals(adapter.getPluginDescriptor().getPluginId().getIdString());
    }, false);

    assertFalse(CompileServerPlugin.EP_NAME.getExtensionList().isEmpty());

    Set<String> libs = getBuildProcessClasspath();
    assertFalse(libs.isEmpty());
  }

  @NotNull
  private Set<String> getBuildProcessClasspath() {
    Project project = DefaultProjectFactory.getInstance().getDefaultProject();
    @NotNull List<String> pluginsCp = new BuildProcessClasspathManager(myDisposable).getBuildProcessClasspath(project);
    return pluginsCp.stream()
      .map(it -> LightJavaModule.moduleName(new File(it).getName()))
      .collect(Collectors.toSet());
  }
}
