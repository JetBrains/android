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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link DisposedModules}.
 */
public class DisposedModulesTest extends PlatformTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    getProject().putUserData(DisposedModules.KEY, null); // ensure nothing is set.
  }

  public void testGetInstanceReturnsSameInstance() {
    Project project = getProject();

    DisposedModules disposedModules1 = DisposedModules.getInstance(project);
    DisposedModules disposedModules2 = DisposedModules.getInstance(project);

    assertSame(disposedModules1, disposedModules2);
  }

  public void testGetInstanceReturnsNewIfExistingOneIsDisposed() {
    Project project = getProject();

    DisposedModules disposedModules1 = DisposedModules.getInstance(project);
    Disposer.dispose(disposedModules1);

    DisposedModules disposedModules2 = DisposedModules.getInstance(project);

    assertNotSame(disposedModules1, disposedModules2);
  }

  public void testMarkImlFilesForRemoval() {
    List<File> filesToRemove = Arrays.asList(new File("1"), new File("2"));

    DisposedModules disposedModules = DisposedModules.getInstance(getProject());
    disposedModules.markImlFilesForDeletion(filesToRemove);

    assertThat(disposedModules.getFilesToDelete()).containsAllIn(filesToRemove);
  }

  public void testMarkImlFilesForRemovalWhenDisposed() {
    DisposedModules disposedModules = DisposedModules.getInstance(getProject());
    Disposer.dispose(disposedModules);

    try {
      disposedModules.markImlFilesForDeletion(Collections.emptyList());
      fail("Expecting error due to disposed instance");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  public void testDeleteImlFilesForDisposedModules() throws IOException {
    File imlFileToDelete = createTempFile("temp.iml", "");

    DisposedModules disposedModules = DisposedModules.getInstance(getProject());
    disposedModules.markImlFilesForDeletion(Collections.singletonList(imlFileToDelete));

    disposedModules.deleteImlFilesForDisposedModules();
    assertAbout(file()).that(imlFileToDelete).doesNotExist();

    // Verify that the instance of DisposedModules has been disposed.
    assertTrue(disposedModules.isDisposed());

    try {
      disposedModules.markImlFilesForDeletion(Collections.emptyList());
      fail("Expecting error due to disposed instance");
    }
    catch (IllegalStateException ignored) {
      // expected
    }
  }

  public void testDispose() {
    List<File> filesToRemove = Arrays.asList(new File("1"), new File("2"));

    DisposedModules disposedModules = DisposedModules.getInstance(getProject());
    disposedModules.markImlFilesForDeletion(filesToRemove);
    Disposer.dispose(disposedModules);

    assertTrue(disposedModules.isDisposed());
    assertThat(disposedModules.getFilesToDelete()).isEmpty();
  }
}