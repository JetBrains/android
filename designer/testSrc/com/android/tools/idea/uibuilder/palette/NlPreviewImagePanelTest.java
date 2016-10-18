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
package com.android.tools.idea.uibuilder.palette;

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.configurations.ConfigurationListener.CFG_DEVICE;
import static com.android.tools.idea.uibuilder.model.NlLayoutType.LAYOUT;
import static java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
import static java.awt.event.InputEvent.BUTTON1_MASK;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.mockito.Mockito.*;

public class NlPreviewImagePanelTest extends LayoutTestCase {
  private static final String BASE_PATH = "palette/";
  private static final int WIDTH = 600;
  private static final int HEIGHT = 200;

  private DesignSurface mySurface;
  private NlPreviewImagePanel myPanel;
  private DependencyManager myDependencyManager;
  private IconPreviewFactory myIconPreviewFactory;
  private RepaintManager myRepaintManager;
  private VirtualFile myFile;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFile = myFixture.copyFileToProject(BASE_PATH + "colors.xml", "res/values/colors.xml");
    NlModel model = createModel();
    mySurface = new DesignSurface(getProject(), mock(DesignerEditorPanelFacade.class));
    mySurface.setModel(model);
    myDependencyManager = mock(DependencyManager.class);
    myIconPreviewFactory = mock(IconPreviewFactory.class);
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(250, 50, TYPE_INT_ARGB);
    when(myDependencyManager.getProject()).thenReturn(getProject());
    when(myIconPreviewFactory.renderDragImage(any(Palette.Item.class), any(ScreenView.class))).thenReturn(image);
    myPanel = new NlPreviewImagePanel(myIconPreviewFactory, myDependencyManager);
    myPanel.setDesignSurface(mySurface);
    myPanel.setItem(PaletteTestCase.findItem(NlPaletteModel.get(getProject()).getPalette(LAYOUT), BUTTON));
    myPanel.setSize(WIDTH, HEIGHT);
    myPanel.doLayout();
    paint();
    myRepaintManager = mock(RepaintManager.class);
    RepaintManager.setCurrentManager(myRepaintManager);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    RepaintManager.setCurrentManager(null);
    reset(myRepaintManager);
    Disposer.dispose(mySurface);
  }

  public void testSetItemInvalidatesUI() {
    myPanel.setItem(PaletteTestCase.findItem(NlPaletteModel.get(getProject()).getPalette(LAYOUT), TEXT_VIEW));
    verify(myRepaintManager).addDirtyRegion(myPanel, 0, 0, WIDTH, HEIGHT);
  }

  public void testConfigurationChangesInvalidatesUI() {
    Configuration configuration = mySurface.getConfiguration();
    configuration.updated(CFG_DEVICE);
    verify(myRepaintManager).addDirtyRegion(myPanel, 0, 0, WIDTH, HEIGHT);
  }

  public void testResourceChangesInvalidatesUI() {
    updateBackgroundColor(myFile);
    UIUtil.dispatchAllInvocationEvents();
    verify(myRepaintManager).addDirtyRegion(myPanel, 0, 0, WIDTH, HEIGHT);
  }

  public void testScaleChangesInvalidatesUI() {
    mySurface.zoomIn();
    verify(myRepaintManager).addDirtyRegion(myPanel, 0, 0, WIDTH, HEIGHT);
  }

  public void testClickOnItemStartsDragAndDrop() {
    TransferHandler handler = mock(TransferHandler.class);
    myPanel.setTransferHandler(handler);
    mousePressed();
    verify(handler).exportAsDrag(eq(myPanel), any(), anyInt());
  }

  public void testClickOnItemWithMissingDependency() {
    Palette.Item item = PaletteTestCase.findItem(NlPaletteModel.get(getProject()).getPalette(LAYOUT), BUTTON);
    myPanel.setItem(item);
    when(myDependencyManager.needsLibraryLoad(eq(item))).thenReturn(true);

    mousePressed();
    verify(myDependencyManager).ensureLibraryIsIncluded(eq(item));
  }

  private void updateBackgroundColor(@NotNull VirtualFile file) {
    VirtualFile resourceDir = file.getParent().getParent();
    AndroidResourceUtil.changeValueResource(getProject(), resourceDir, "colorPrimary", ResourceType.COLOR, "@android/color/holo_red_light",
                                            file.getName(), ImmutableList.of(file.getParent().getName()), false);
  }

  private void paint() {
    BufferedImage image = UIUtil.createImage(myPanel.getWidth(), myPanel.getHeight(), TYPE_INT_ARGB);
    Graphics graphics = image.getGraphics();
    myPanel.paint(graphics);
    graphics.dispose();
  }

  private void mousePressed() {
    int x = 10;
    int y = 10;
    MouseEvent event = new MouseEvent(myPanel, MOUSE_PRESSED, System.currentTimeMillis(), BUTTON1_MASK | BUTTON1_DOWN_MASK, x, y, 1, false);
    for (MouseListener listener : myPanel.getMouseListeners()) {
      listener.mousePressed(event);
    }
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("absolute.xml",
                                 component(ABSOLUTE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/myText")
                                       .width("100dp")
                                       .height("100dp")
                                       .withAttribute("android:layout_x", "100dp")
                                       .withAttribute("android:layout_y", "100dp")
                                   ));
    return builder.build();
  }
}
