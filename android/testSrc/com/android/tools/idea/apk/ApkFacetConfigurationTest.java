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
package com.android.tools.idea.apk;

import com.android.sdklib.devices.Abi;
import com.android.tools.idea.apk.debugging.DebuggableSharedObjectFile;
import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.android.tools.idea.apk.debugging.SetupIssue;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtilRt.createIfNotExists;

/**
 * Tests for {@link ApkFacetConfiguration}.
 */
public class ApkFacetConfigurationTest {
  @Rule
  public TemporaryFolder myTemporaryFolder = new TemporaryFolder();

  private ApkFacetConfiguration myConfiguration;

  @Before
  public void setUp()  {
    myConfiguration = new ApkFacetConfiguration();
  }

  @Test
  public void getDebugSymbolFolderPaths() throws Exception {
    myTemporaryFolder.create();
    File x86Folder = myTemporaryFolder.newFolder(Abi.X86.toString());
    File armeabiV7aFolder = myTemporaryFolder.newFolder("armeabiV7a");
    File x86_64 = myTemporaryFolder.newFolder("x86_64");

    File testX86File = createFile(x86Folder, "test.so");
    File testArmeabiV7aFile = createFile(armeabiV7aFolder, "test.so");

    NativeLibrary testLibrary = new NativeLibrary("test.so");
    DebuggableSharedObjectFile testSoFile = new DebuggableSharedObjectFile();
    testSoFile.path = testX86File.getPath();
    testLibrary.debuggableSharedObjectFilesByAbi.put(Abi.X86, testSoFile);

    testSoFile = new DebuggableSharedObjectFile();
    testSoFile.path = testArmeabiV7aFile.getPath();
    testLibrary.debuggableSharedObjectFilesByAbi.put(Abi.ARMEABI_V7A, testSoFile);

    myConfiguration.NATIVE_LIBRARIES.add(testLibrary);

    File debugX86File = createFile(x86Folder, "debub.so");
    File debugX86_64File = createFile(x86_64, "debug.so");

    NativeLibrary debugLibrary = new NativeLibrary("test.so");
    DebuggableSharedObjectFile debugSoFile = new DebuggableSharedObjectFile();
    debugSoFile.path = debugX86File.getPath();
    debugLibrary.debuggableSharedObjectFilesByAbi.put(Abi.X86, debugSoFile);

    debugSoFile = new DebuggableSharedObjectFile();
    debugSoFile.path = debugX86_64File.getPath();
    debugLibrary.debuggableSharedObjectFilesByAbi.put(Abi.X86_64, debugSoFile);

    myConfiguration.NATIVE_LIBRARIES.add(debugLibrary);

    Collection<String> paths = myConfiguration.getDebugSymbolFolderPaths(Arrays.asList(Abi.X86, Abi.ARMEABI_V7A));
    assertThat(paths).containsExactly(x86Folder.getPath(), armeabiV7aFolder.getPath());
  }

  @NotNull
  private static File createFile(@NotNull File parent, @NotNull String name) {
    File child = new File(parent, name);
    createIfNotExists(child);
    return child;
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithEmptyLibraries() {
    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).isEmpty();
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithLibrariesMissingDebugSymbols() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.hasDebugSymbols = true;

    NativeLibrary library2 = new NativeLibrary("x.h");
    NativeLibrary library3 = new NativeLibrary("y.c");

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).containsAllOf(library2, library3);
  }

  @Test
  public void getLibrariesWithoutDebugSymbolsWithLibrariesHavingDebugSymbols() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.hasDebugSymbols = true;

    NativeLibrary library2 = new NativeLibrary("x.h");
    library2.hasDebugSymbols = true;

    NativeLibrary library3 = new NativeLibrary("y.c");
    library3.hasDebugSymbols = true;

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    List<NativeLibrary> libraries = myConfiguration.getLibrariesWithoutDebugSymbols();
    assertThat(libraries).isEmpty();
  }

  @Test
  public void removeIssues() {
    SetupIssue issue1 = new SetupIssue();
    issue1.category = "category1";

    SetupIssue issue2 = new SetupIssue();
    issue2.category = "category1";

    SetupIssue issue3 = new SetupIssue();
    issue3.category = "category2";

    myConfiguration.SETUP_ISSUES.add(issue1);
    myConfiguration.SETUP_ISSUES.add(issue2);
    myConfiguration.SETUP_ISSUES.add(issue3);

    myConfiguration.removeIssues("category1");

    assertThat(myConfiguration.SETUP_ISSUES).containsExactly(issue3);
  }

  @Test
  public void getSymbolFolderPathMappings() {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.pathMappings.put("a1.so", "b1.so");

    NativeLibrary library2 = new NativeLibrary("x.c");
    library2.pathMappings.put("a2.so", "b2.so");

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);

    Map<String, String> mappings = myConfiguration.getSymbolFolderPathMappings();
    assertThat(mappings).hasSize(2);
    assertThat(mappings).containsEntry("a1.so", "b1.so");
    assertThat(mappings).containsEntry("a2.so", "b2.so");
  }
}