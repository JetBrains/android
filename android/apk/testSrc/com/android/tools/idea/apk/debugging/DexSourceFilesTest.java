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
package com.android.tools.idea.apk.debugging;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;

import java.io.File;
import java.io.IOException;

/**
 * Tests for {@link DexSourceFiles}.
 */
public class DexSourceFilesTest extends HeavyPlatformTestCase {
  private File myOutputFolderPath;
  private DexSourceFiles myDexSourceFiles;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    myDexSourceFiles = new DexSourceFiles(project);

    myOutputFolderPath = DexSourceFiles.getInstance(project).getDefaultSmaliOutputFolderPath();
    ensureExists(myOutputFolderPath);
  }

  public void testIsJavaFileWithJavaFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("java");
    assertTrue(myDexSourceFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithDirectory() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(true);
    assertFalse(myDexSourceFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithTextFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("text");
    assertFalse(myDexSourceFiles.isJavaFile(file));
  }

  public void testIsJavaFileWithFileWithoutExtension() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("");
    assertFalse(myDexSourceFiles.isJavaFile(file));
  }


  public void testIsSmaliFileWithJavaFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("smali");
    assertTrue(myDexSourceFiles.isSmaliFile(file));
  }

  public void testIsSmaliFileWithDirectory() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(true);
    assertFalse(myDexSourceFiles.isSmaliFile(file));
  }

  public void testIsSmaliFileWithTextFile() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("text");
    assertFalse(myDexSourceFiles.isSmaliFile(file));
  }

  public void testIsSmaliFileWithFileWithoutExtension() {
    VirtualFile file = mock(VirtualFile.class);

    when(file.isDirectory()).thenReturn(false);
    when(file.getExtension()).thenReturn("");
    assertFalse(myDexSourceFiles.isSmaliFile(file));
  }

  public void testFindSmaliFile() {
    File smaliFilePath = new File(myOutputFolderPath, join("com", "android", "smali", "MyClass.smali"));
    createIfNotExists(smaliFilePath);

    LocalFileSystem.getInstance().refresh(false /* synchronous */);
    VirtualFile file = myDexSourceFiles.findSmaliFile("com.android.smali.MyClass");
    assertNotNull(file);

    file = myDexSourceFiles.findSmaliFile("com.android.smali.MyClass2");
    assertNull(file);
  }

  public void testFindSmaliFilePathForPackage() throws IOException {
    File packagePath = new File(myOutputFolderPath, join("com", "android", "smali"));
    ensureExists(packagePath);

    File found = myDexSourceFiles.findSmaliFilePathForPackage("com.android.smali");
    assertAbout(file()).that(found).isEquivalentAccordingToCompareTo(packagePath);
  }}