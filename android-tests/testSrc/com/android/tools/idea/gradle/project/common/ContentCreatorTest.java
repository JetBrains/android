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

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link com.android.tools.idea.gradle.project.common.GradleInitScripts.ContentCreator}.
 */
public class ContentCreatorTest {
  private GradleInitScripts.ContentCreator myContentCreator;

  @Before
  public void setUp() {
    myContentCreator = new GradleInitScripts.ContentCreator();
  }

  @Test
  public void createLocalMavenRepoInitScriptContent() {
    List<File> repoPaths = Lists.newArrayList(new File("path1"), new File("path2"), new File("path3"));
    String content = myContentCreator.createLocalMavenRepoInitScriptContent(repoPaths);

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

    assertEquals(expected, content);
  }
}