/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.profiling.capture;

import com.android.tools.idea.editors.hprof.HprofCaptureType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.Arrays;

import static com.intellij.openapi.util.io.FileUtil.toCanonicalPath;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class CaptureServiceTest extends IdeaTestCase {

  static Element readElement(String text) throws Exception {
    return new SAXBuilder().build(new StringReader(text)).getRootElement();
  }

  public void testUpdate() throws Exception {
    CaptureService service = CaptureService.getInstance(myProject);
    assertNull(service.getCapturesDirectory());

    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    assertNotNull(projectDir);

    VirtualFile captures = projectDir.createChildDirectory(null, "captures");

    assertTrue(service.getCaptures().isEmpty());
    captures.createChildData(null, "data.capture");
    service.update();
    assertTrue(service.getCaptures().isEmpty());

    ExtensionsArea area = Extensions.getRootArea();
    Element element = readElement("  <extensions defaultExtensionNs=\"com.android\">\n" +
                                  "    <captureType implementation=\"" + MyCaptureType.class.getName() +
                                  "\"/>\n  </extensions>");
    area.registerExtension(new DefaultPluginDescriptor(PluginId.getId("com.android")), element.getChild("captureType"));
    MyCaptureType type = CaptureTypeService.getInstance().getType(MyCaptureType.class);

    service.update();
    assertEquals(1, service.getCaptures().size());
    assertEquals(type, service.getCaptures().iterator().next().getType());
  }

  public void testSynchronousFileSaving() throws Exception {
    CaptureService service = CaptureService.getInstance(myProject);
    String testDataPath = toCanonicalPath(toSystemDependentName(AndroidTestBase.getTestDataPath()));

    File testHprofFile = new File(testDataPath, toSystemDependentName("guiTests/CapturesApplication/captures/snapshot.hprof"));
    byte[] testFileBytes = readFully(testHprofFile);

    Capture capture = service.createCapture(HprofCaptureType.class, testFileBytes, "snapshot");
    capture.getFile().refresh(false, false);

    String capturePath = capture.getFile().getCanonicalPath();
    assertNotNull(capturePath);

    File captureFile = new File(capturePath);
    assertEquals(captureFile.length(), testFileBytes.length);

    byte[] captureFileBytes = readFully(captureFile);
    assertTrue(Arrays.equals(captureFileBytes, testFileBytes));
  }

  // Failing on go/studio-builder bots (b.android.com/201546).
  public void ignore_testAsynchronousFileSaving() throws Exception {
    CaptureService service = CaptureService.getInstance(myProject);
    String testDataPath = toCanonicalPath(toSystemDependentName(AndroidTestBase.getTestDataPath()));

    File testHprofFile = new File(testDataPath, toSystemDependentName("guiTests/CapturesApplication/captures/snapshot.hprof"));
    byte[] testFileBytes = readFully(testHprofFile);

    CaptureHandle handle = service.startCaptureFile(HprofCaptureType.class, "snapshot", true);
    for (int i = 0; i < testFileBytes.length; i += 1024 * 1024) {
      service.appendData(handle, Arrays.copyOfRange(testFileBytes, i, i + Math.min(1024 * 1024, testFileBytes.length - i)));
    }
    Capture capture = service.finalizeCaptureFileSynchronous(handle);
    capture.getFile().refresh(false, false);

    String capturePath = capture.getFile().getCanonicalPath();
    assertNotNull(capturePath);

    File captureFile = new File(capturePath);
    assertEquals(captureFile.length(), testFileBytes.length);

    byte[] captureFileBytes = readFully(captureFile);
    assertTrue(Arrays.equals(captureFileBytes, testFileBytes));
  }

  public void testFileRemoval() throws Exception {
    CaptureService service = CaptureService.getInstance(myProject);
    String testDataPath = toCanonicalPath(toSystemDependentName(AndroidTestBase.getTestDataPath()));

    File testHprofFile = new File(testDataPath, toSystemDependentName("guiTests/CapturesApplication/captures/snapshot.hprof"));
    byte[] testFileBytes = readFully(testHprofFile);

    CaptureHandle handle = service.startCaptureFile(HprofCaptureType.class, "snapshot", false);
    for (int i = 0; i < testFileBytes.length; i += 1024 * 1024) {
      service.appendData(handle, Arrays.copyOfRange(testFileBytes, i, i + Math.min(1024 * 1024, testFileBytes.length - i)));
    }
    service.cancelCaptureFileSynchronous(handle);

    String capturePath = handle.getFile().getCanonicalPath();
    assertNotNull(capturePath);
    assertFalse(handle.getFile().exists());
  }

  private static byte[] readFully(@NotNull File file) throws IOException {
    FileInputStream inputStream = new FileInputStream(file);
    try {
      int fileLength = (int)file.length();

      byte[] bytes = new byte[fileLength];
      int bytesRead = 0;
      while (bytesRead < bytes.length) {
        int lengthRead = inputStream.read(bytes, bytesRead, bytes.length - bytesRead);
        if (lengthRead == -1) {
          break;
        }
        bytesRead += lengthRead;
      }
      return bytes;
    }
    finally {
      inputStream.close();
    }
  }

  private static class MyCaptureType extends CaptureType {
    @NotNull
    @Override
    public String getName() {
      return "Type";
    }

    @NotNull
    @Override
    public Icon getIcon() {
      return AllIcons.Icon;
    }

    @Override
    public boolean isValidCapture(@NotNull VirtualFile file) {
      return "capture".equals(file.getExtension());
    }

    @NotNull
    @Override
    public String getCaptureExtension() {
      return ".capture";
    }

    @NotNull
    @Override
    protected Capture createCapture(@NotNull VirtualFile file) {
      return new Capture(file, this);
    }

    @NotNull
    @Override
    public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
      return null;
    }
  }
}
