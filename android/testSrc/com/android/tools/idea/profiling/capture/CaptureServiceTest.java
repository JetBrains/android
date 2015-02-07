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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;

public class CaptureServiceTest extends IdeaTestCase {

  public void testUpdate() throws IOException {
    CaptureService service = CaptureService.getInstance(myProject);
    assertNull(service.getCapturesDirectory());


    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(myProject.getBasePath());
    assertNotNull(projectDir);

    VirtualFile captures = projectDir.createChildDirectory(null, "captures");

    assertTrue(service.getCaptures().isEmpty());
    captures.createChildData(null, "data.capture");
    service.update();
    assertTrue(service.getCaptures().isEmpty());

    MyCaptureType type = new MyCaptureType();
    CaptureTypeService.getInstance().register(MyCaptureType.class, type);
    service.update();
    assertEquals(1, service.getCaptures().size());
    assertEquals(type, service.getCaptures().iterator().next().getType());
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
    protected Capture createCapture(@NotNull VirtualFile file) {
      return new Capture(file, this);
    }

    @NotNull
    @Override
    public String createCaptureFileName() {
      return "1234";
    }
  }
}
