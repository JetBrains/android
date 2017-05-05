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
package com.android.tools.idea.editors.layoutInspector;

import com.android.tools.idea.editors.layoutInspector.ui.LayoutInspectorPanel;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.editor.ImageZoomModel;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.nio.file.Paths;

public class LayoutInspectorPanelZoomTest extends AndroidTestCase {
  private LayoutInspectorPanel myPanel;
  private LayoutInspectorContext myContext;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File testFile = Paths.get(getTestDataPath(), "editors/layoutInspector/LayoutCapture.li").toFile();
    VirtualFile layoutFile = LocalFileSystem.getInstance().findFileByIoFile(testFile);
    LayoutFileData testData = new LayoutFileData(layoutFile);

    myContext = new LayoutInspectorContext(testData, getProject());
    myPanel = new LayoutInspectorPanel(myContext);
    myPanel.setSize(800, 800);
  }

  public void testZoomModel() {
    ImageZoomModel zoom = myPanel.getZoomModel();

    assertNotNull(zoom);
    assertTrue(zoom.canZoomIn());
    assertTrue(zoom.canZoomOut());
    // default zoom factor is changed from 1 to fit the image
    // inside the given space
    assertNotSame(zoom.getZoomFactor(), 1);
  }

  public void testZoomChanges() {
    ImageZoomModel zoom = myPanel.getZoomModel();

    double initialFactor = zoom.getZoomFactor();
    zoom.zoomIn();
    double newFactor = zoom.getZoomFactor();

    assertTrue(initialFactor != newFactor);

    initialFactor = zoom.getZoomFactor();
    zoom.zoomOut();
    newFactor = zoom.getZoomFactor();
    assertTrue(initialFactor != newFactor);
  }

  // test grid is not visible by default
  public void testShowGrid() {
    assertFalse(myPanel.isGridVisible());

    myPanel.setGridVisible(true);
    assertTrue(myPanel.isGridVisible());
  }
}