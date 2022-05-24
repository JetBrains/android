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
package com.android.tools.idea.gradle.project.common;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.application.PathManager.getJarPathForClass;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import java.util.Arrays;
import java.util.List;
import kotlin.reflect.KType;
import org.mockito.Mock;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.common.GradleInitScripts.ContentCreator}.
 */
public class ContentCreatorTest extends PlatformTestCase {
  @Mock private GradleInitScripts.AndroidStudioToolingPluginJars myJavaLibraryPluginJars;
  private GradleInitScripts.ContentCreator myContentCreator;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myContentCreator = new GradleInitScripts.ContentCreator(myJavaLibraryPluginJars);
  }

  public void testCreateLocalMavenRepoInitScriptContent() {
    List<String> repoPaths = Arrays.asList("path1", "path2", "path3");
    String expected = "import org.gradle.util.GradleVersion\n\n" +
                      "allprojects {\n" +
                      "  buildscript {\n" +
                      "    repositories {\n" +
                      "      maven { url 'path1'}\n" +
                      "      maven { url 'path2'}\n" +
                      "      maven { url 'path3'}\n" +
                      "    }\n" +
                      "  }\n" +
                      "  repositories {\n" +
                      "      maven { url 'path1'}\n" +
                      "      maven { url 'path2'}\n" +
                      "      maven { url 'path3'}\n" +
                      "  }\n" +
                      "}\n" +
                      "if (GradleVersion.current().baseVersion >= GradleVersion.version('7.0')) {\n" +
                      "  beforeSettings {\n" +
                      "    it.pluginManagement {\n" +
                      "      repositories {\n" +
                      "      maven { url 'path1'}\n" +
                      "      maven { url 'path2'}\n" +
                      "      maven { url 'path3'}\n" +
                      "      }\n" +
                      "    }\n" +
                      "  }\n" +
                      "}\n" +
                      "if (GradleVersion.current().baseVersion >= GradleVersion.version('6.8')) {\n" +
                      "  beforeSettings {\n" +
                      "    it.dependencyResolutionManagement {\n" +
                      "      repositories {\n" +
                      "      maven { url 'path1'}\n" +
                      "      maven { url 'path2'}\n" +
                      "      maven { url 'path3'}\n" +
                      "      }\n" +
                      "    }\n" +
                      "  }\n" +
                      "}\n";

    String content = myContentCreator.createLocalMavenRepoInitScriptContent(repoPaths);
    assertEquals(expected, content);
  }

  public void testCreateApplyJavaLibraryPluginInitScriptContent() {
    List<String> jarPaths = Arrays.asList("/path/to/jar1", "/path/to/jar2");
    when(myJavaLibraryPluginJars.getJarPaths()).thenReturn(jarPaths);

    String expected = "initscript {\n" +
                      "    dependencies {\n" +
                      "        classpath files([mapPath('/path/to/jar1'), mapPath('/path/to/jar2')])\n" +
                      "    }\n" +
                      "}\n" +
                      "allprojects {\n" +
                      "    apply plugin: com.android.ide.gradle.model.builder.AndroidStudioToolingPlugin\n" +
                      "}\n";

    String content = myContentCreator.createAndroidStudioToolingPluginInitScriptContent();
    assertEquals(expected, content);
  }

  public void testGetJarPathContainsKotlinRuntime() {
    GradleInitScripts.AndroidStudioToolingPluginJars javaLibraryPluginJars = new GradleInitScripts.AndroidStudioToolingPluginJars();
    List<String> jarPaths = javaLibraryPluginJars.getJarPaths();
    assertThat(jarPaths).hasSize(3);
    assertThat(jarPaths).contains(FileUtil.toCanonicalPath(getJarPathForClass(KType.class)));
  }
}