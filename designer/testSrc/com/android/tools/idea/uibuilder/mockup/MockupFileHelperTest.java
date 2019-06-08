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
package com.android.tools.idea.uibuilder.mockup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import org.mockito.Mock;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;


public class MockupFileHelperTest extends MockupTestCase {

  @Mock
  Project mockProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(mockProject.getBasePath()).thenReturn(getTestDataPath());
  }

  public void testOpenImageFile() throws Exception {
  }

  public void testGetFileChooserDescriptor() throws Exception {
  }

  public void testGetFullFilePath() throws Exception {
    final Path fullFilePath = MockupFileHelper.getFullFilePath(mockProject, MOCKUP_PSD);
    assertNotNull(fullFilePath);
    assertEquals(Paths.get(getTestDataPath(),MOCKUP_PSD).normalize().toString(), fullFilePath.toString());
  }

  public void testGetFullFilePathAbsolute() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path fullFilePath = MockupFileHelper.getFullFilePath(mockProject, "/foo/bar");
    assertNotNull(fullFilePath);
    assertEquals("/foo/bar", fullFilePath.toString());
  }

  public void testWriteOpacityToXML() throws Exception {
  }

  public void testIsInMockupDir() throws Exception {
  }

  public void testGetXMLFilePath() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path path = MockupFileHelper.getXMLFilePath(mockProject, "mockup.psd");
    assertNotNull(path);
    final String[] split = path.toString().split(FileSystems.getDefault().getSeparator());
    assertSize(1,split);
    assertEquals("mockup.psd", split[split.length - 1]);
  }

  public void testGetXMLFilePathInnerDir() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path path = MockupFileHelper.getXMLFilePath(mockProject, "dir/mockup.psd");
    assertNotNull(path);
    final String[] split = path.toString().split(FileSystems.getDefault().getSeparator());
    assertEquals("mockup.psd", split[split.length - 1]);
    assertEquals("dir", split[split.length - 2]);
  }

  public void testGetXMLFilePathOuterDir() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path path = MockupFileHelper.getXMLFilePath(mockProject, "../mockup.psd");
    assertNotNull(path);
    final String[] split = path.toString().split(FileSystems.getDefault().getSeparator());
    assertEquals("mockup.psd", split[split.length - 1]);
    final String[] testPathSplit = getTestDataPath().split(FileSystems.getDefault().getSeparator());
    assertEquals(testPathSplit[testPathSplit.length - 2], split[split.length - 2]);
  }

  public void testGetXMLFilePathAbsoluteOutside() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path path = MockupFileHelper.getXMLFilePath(mockProject, "/root/mockup.psd");
    assertNotNull(path);
    final String[] split = path.toString().split(FileSystems.getDefault().getSeparator());
    assertEmpty(split[0]);
    assertEquals("root", split[1]);
    assertEquals("mockup.psd", split[2]);
  }

  public void testGetXMLFilePathAbsoluteInside() throws Exception {
    if (SystemInfoRt.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final Path path = MockupFileHelper.getXMLFilePath(mockProject, mockProject.getBasePath() + "/mockup/test.psd");
    assertNotNull(path);
    assertEquals("mockup/test.psd", path.toString());
  }
}