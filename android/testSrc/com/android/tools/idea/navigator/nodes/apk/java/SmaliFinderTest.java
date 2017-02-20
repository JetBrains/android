/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.navigator.nodes.apk.java.SmaliFinder.getDefaultSmaliOutputFolderPath;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;

/**
 * Tests for {@link SmaliFinder}.
 */
public class SmaliFinderTest extends IdeaTestCase {
  private File myOutputFolderPath;
  private SmaliFinder mySmaliFinder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    mySmaliFinder = new SmaliFinder(project);

    myOutputFolderPath = getDefaultSmaliOutputFolderPath(project);
    ensureExists(myOutputFolderPath);
  }

  public void testFindSmaliFile() {
    File smaliFilePath = new File(myOutputFolderPath, join("com", "android", "smali", "MyClass.smali"));
    createIfNotExists(smaliFilePath);

    LocalFileSystem.getInstance().refresh(false /* synchronous */);
    VirtualFile file = mySmaliFinder.findSmaliFile("com.android.smali.MyClass");
    assertNotNull(file);

    file = mySmaliFinder.findSmaliFile("com.android.smali.MyClass2");
    assertNull(file);
  }

  public void testFindPackageFilePath() throws IOException {
    File packagePath = new File(myOutputFolderPath, join("com", "android", "smali"));
    ensureExists(packagePath);

    File found = mySmaliFinder.findPackageFilePath("com.android.smali");
    assertAbout(file()).that(found).isEquivalentAccordingToCompareTo(packagePath);
  }
}