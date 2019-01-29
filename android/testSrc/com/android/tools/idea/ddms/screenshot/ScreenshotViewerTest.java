/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.intellij.images.editor.ImageZoomModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.Assert.assertEquals;

public final class ScreenshotViewerTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.onDisk("ScreenshotViewerTest");

  private ScreenshotViewer myViewer;

  @Before
  public void initScreenshotViewer() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      File file = VfsUtilCore.virtualToIoFile(myRule.fixture.getTempDirFixture().createFile("screenshot1.png"));
      myViewer = new ScreenshotViewer(myRule.getProject(), new BufferedImage(280, 280, BufferedImage.TYPE_INT_ARGB), file, null, null);
    });
  }

  @After
  public void disposeOfScreenshotViewer() {
    ApplicationManager.getApplication().invokeAndWait(() -> Disposer.dispose(myViewer.getDisposable()));
  }

  @Test
  public void updateEditorImageDoesntClobberZoomFactor() {
    JViewport viewport = Mockito.mock(JViewport.class);
    Mockito.when(viewport.isShowing()).thenReturn(true);
    Mockito.when(viewport.getHeight()).thenReturn(598);
    Mockito.when(viewport.getWidth()).thenReturn(1089);

    JScrollPane scrollPane = Mockito.mock(JScrollPane.class);
    Mockito.when(scrollPane.getViewport()).thenReturn(viewport);

    myViewer.setScrollPane(scrollPane);

    ImageZoomModel model = myViewer.getImageFileEditor().getImageEditor().getZoomModel();
    model.setZoomFactor(1);

    myViewer.updateEditorImage();
    assertEquals(1, model.getZoomFactor(), 0);
  }
}
