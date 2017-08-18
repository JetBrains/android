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

import com.intellij.idea.IdeaTestApplication;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.loadFile;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradleInitScripts}.
 */
public class GradleInitScriptsTest {
  private File myInitScriptPath;

  @Before
  public void setUp() {
    IdeaTestApplication.getInstance();
  }

  @After
  public void tearDown() {
    if (myInitScriptPath != null) {
      boolean deleted = myInitScriptPath.delete();
      if (!deleted) {
        // We need to delete the file, otherwise the file names will end with numbers (e.g. "asLocalRepo1.gradle".
        fail(String.format("Failed to delete file '%1$s'", myInitScriptPath.getPath()));
      }
    }
  }

  @Test
  public void createLocalMavenRepoInitScriptFile() throws IOException {
    GradleInitScripts.ContentCreator contentCreator = Mockito.mock(GradleInitScripts.ContentCreator.class);
    GradleInitScripts initScripts = new GradleInitScripts(contentCreator);

    String content = "Hello World!";
    when(contentCreator.createLocalMavenRepoInitScriptContent(anyListOf(File.class))).thenReturn(content);

    myInitScriptPath = initScripts.createLocalMavenRepoInitScriptFile();
    assertNotNull(myInitScriptPath);
    assertEquals("asLocalRepo.gradle", myInitScriptPath.getName());
    assertAbout(file()).that(myInitScriptPath).isFile();

    String actual = loadFile(myInitScriptPath);
    assertEquals(content, actual);

    verify(contentCreator).createLocalMavenRepoInitScriptContent(anyListOf(File.class));
  }

  @Test
  public void createLocalMavenRepoInitScriptFileWithoutContent() throws IOException {
    GradleInitScripts.ContentCreator contentCreator = Mockito.mock(GradleInitScripts.ContentCreator.class);
    GradleInitScripts initScripts = new GradleInitScripts(contentCreator);

    when(contentCreator.createLocalMavenRepoInitScriptContent(anyListOf(File.class))).thenReturn(null);

    myInitScriptPath = initScripts.createLocalMavenRepoInitScriptFile();
    assertNull(myInitScriptPath);
  }

  @Test
  public void addLocalMavenRepoInitScriptCommandLineArgTo() {
    GradleInitScripts initScripts = new GradleInitScripts();

    List<String> args = new ArrayList<>();
    initScripts.addLocalMavenRepoInitScriptCommandLineArgTo(args);
    assertThat(args).hasSize(2);

    assertEquals("--init-script", args.get(0));
    String initScriptTextPath = args.get(1);
    assertNotNull(initScriptTextPath);

    myInitScriptPath = new File(initScriptTextPath);
    assertAbout(file()).that(myInitScriptPath).isFile();
    assertEquals("asLocalRepo.gradle", myInitScriptPath.getName());
  }
}