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
package com.android.tools.idea.uibuilder.mockup.tools;

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupBaseTest;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MockupEditorTest extends MockupBaseTest {

  // Avoid no test warning
  // while still extending MockupBaseTest
  public void testEmptyTest(){}

  public static void main(String[] args) throws IOException {

    final File imageFile = new File(getTestDataPath(), "/mockup/inbox.png");
    final BufferedImage image = ImageIO.read(imageFile);
    final Mockup mockup = Mockito.mock(Mockup.class);
    final NlComponent component = Mockito.mock(NlComponent.class);
    final VirtualFile virtualFile = Mockito.mock(VirtualFile.class);

    Mockito.when(mockup.getImage()).thenReturn(image);
    Mockito.when(mockup.getCropping()).thenReturn(new Rectangle(0,0,-1,-1));
    Mockito.when(mockup.getComponent()).thenReturn(component);
    Mockito.when(mockup.getVirtualFile()).thenReturn(virtualFile);
    Mockito.when(component.getTagName()).thenReturn("ConstraintLayout");
    Mockito.when(virtualFile.getPath()).thenReturn(imageFile.getPath());

    JFrame frame = new JFrame();
    final MockupEditor mockupEditor = new MockupEditor(mockup);
    frame.setContentPane(mockupEditor.getContentPane());
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setAlwaysOnTop(true);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}