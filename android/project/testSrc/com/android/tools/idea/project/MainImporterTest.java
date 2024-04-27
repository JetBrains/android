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
package com.android.tools.idea.project;

import com.android.tools.idea.project.CustomProjectTypeImporter.MainImporter;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link MainImporter}.
 */
public class MainImporterTest {
  @Mock private VirtualFile myFile;
  @Mock private CustomProjectTypeImporter myImporter1;
  @Mock private CustomProjectTypeImporter myImporter2;
  @Mock private CustomProjectTypeImporter myImporter3;

  private MainImporter myImporter;

  @Before
  public void setUp() {
    initMocks(this);
    myImporter = new MainImporter(myImporter1, myImporter2, myImporter3);
  }

  @Test
  public void importFileAsProject() {
    // Only 'myImporter2' can import the file.
    when(myImporter1.canImport(myFile)).thenReturn(false);
    when(myImporter2.canImport(myFile)).thenReturn(true);
    when(myImporter3.canImport(myFile)).thenReturn(false);

    boolean imported = myImporter.importFileAsProject(myFile);
    assertTrue(imported);

    verify(myImporter1, times(1)).canImport(myFile);
    verify(myImporter2, times(1)).canImport(myFile);
    verify(myImporter3, never()).canImport(myFile);

    verify(myImporter1, never()).importFile(myFile);
    verify(myImporter2, times(1)).importFile(myFile);
    verify(myImporter3, never()).importFile(myFile);
  }

  @Test
  public void importFileAsProjectWithUnsupportedFile() {
    // None of the importers can import the file.
    when(myImporter1.canImport(myFile)).thenReturn(false);
    when(myImporter2.canImport(myFile)).thenReturn(false);
    when(myImporter3.canImport(myFile)).thenReturn(false);

    boolean imported = myImporter.importFileAsProject(myFile);
    assertFalse(imported);

    verify(myImporter1, times(1)).canImport(myFile);
    verify(myImporter2, times(1)).canImport(myFile);
    verify(myImporter3, times(1)).canImport(myFile);

    verify(myImporter1, never()).importFile(myFile);
    verify(myImporter2, never()).importFile(myFile);
    verify(myImporter3, never()).importFile(myFile);
  }
}