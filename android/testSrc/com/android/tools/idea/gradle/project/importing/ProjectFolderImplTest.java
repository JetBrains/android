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
package com.android.tools.idea.gradle.project.importing;

import com.android.tools.idea.gradle.project.importing.ProjectFolder.ProjectFolderImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ProjectFolderImpl}.
 */
public class ProjectFolderImplTest {
  @Rule
  public TemporaryFolder myProjectFolder = new TemporaryFolder();

  private File myProjectFolderPath;
  private ProjectFolderImpl myFolder;

  @Before
  public void setUp() {
    myProjectFolderPath = myProjectFolder.getRoot();
    myFolder = new ProjectFolderImpl(myProjectFolderPath);
  }

  @Test
  public void createTopLevelBuildFile() throws IOException {
    File buildFilePath = new File(myProjectFolderPath, "build.gradle");
    assertAbout(file()).that(buildFilePath).doesNotExist();

    myFolder.createTopLevelBuildFile();
    assertAbout(file()).that(buildFilePath).isFile();
  }

  @Test
  public void createTopLevelBuildFileWhenFileAlreadyExists() throws IOException {
    File buildFilePath = myProjectFolder.newFile("build.gradle");
    long lastModified = buildFilePath.lastModified();

    myFolder.createTopLevelBuildFile();
    assertAbout(file()).that(buildFilePath).isFile();

    // verify the file did not change.
    assertEquals(lastModified, buildFilePath.lastModified());
  }

  @Test
  public void createIdeaProjectFolder() throws IOException {
    File buildFilePath = new File(myProjectFolderPath, ".idea");
    assertAbout(file()).that(buildFilePath).doesNotExist();

    myFolder.createIdeaProjectFolder();
    assertAbout(file()).that(buildFilePath).isDirectory();
  }

  @Test
  public void createIdeaProjectFolderWhenFolderAlreadyExists() throws IOException {
    File ideaFolderPath = myProjectFolder.newFolder(".idea");

    File librariesFolderPath = new File(ideaFolderPath, "libraries");
    assertTrue(librariesFolderPath.mkdirs());
    assertAbout(file()).that(librariesFolderPath).isDirectory();

    myFolder.createIdeaProjectFolder();
    assertAbout(file()).that(ideaFolderPath).isDirectory();

    // verity that "libraries" folder was deleted.
    assertAbout(file()).that(librariesFolderPath).doesNotExist();
  }
}