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

import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.common.GradleInitScripts.ContentCreator}.
 */
public class ContentCreatorTest extends IdeaTestCase {
  @Mock private GradleInitScripts.JavaLibraryPluginJars myJavaLibraryPluginJars;
  private GradleInitScripts.ContentCreator myContentCreator;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myContentCreator = new GradleInitScripts.ContentCreator(myJavaLibraryPluginJars);
  }

  public void testCreateLocalMavenRepoInitScriptContent() {
    List<File> repoPaths = Arrays.asList(new File("path1"), new File("path2"), new File("path3"));
    String expected = "allprojects {\n" +
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
                      "}\n";

    String content = myContentCreator.createLocalMavenRepoInitScriptContent(repoPaths);
    assertEquals(expected, content);
  }

  public void testCreateApplyJavaLibraryPluginInitScriptContent() {
    List<String> jarPaths = Arrays.asList("/path/to/jar1", "/path/to/jar2");
    when(myJavaLibraryPluginJars.getJarPaths()).thenReturn(jarPaths);

    String expected = "initscript {\n" +
                      "    dependencies {\n" +
                      "        classpath files(['/path/to/jar1', '/path/to/jar2'])\n" +
                      "    }\n" +
                      "}\n" +
                      "allprojects {\n" +
                      "    apply plugin: com.android.java.model.builder.JavaLibraryPlugin\n" +
                      "}\n";

    String content = myContentCreator.createApplyJavaLibraryPluginInitScriptContent();
    assertEquals(expected, content);
  }

  public void testCreateApplyJavaLibraryPluginInitScriptContentWithBackSlash() {
    List<String> jarPaths = Arrays.asList("c:\\path\\to\\jar1", "c:\\path\\to\\jar2");
    when(myJavaLibraryPluginJars.getJarPaths()).thenReturn(jarPaths);

    String expected = "initscript {\n" +
                      "    dependencies {\n" +
                      "        classpath files(['c:\\\\path\\\\to\\\\jar1', 'c:\\\\path\\\\to\\\\jar2'])\n" +
                      "    }\n" +
                      "}\n" +
                      "allprojects {\n" +
                      "    apply plugin: com.android.java.model.builder.JavaLibraryPlugin\n" +
                      "}\n";

    String content = myContentCreator.createApplyJavaLibraryPluginInitScriptContent();
    assertEquals(expected, content);
  }
}