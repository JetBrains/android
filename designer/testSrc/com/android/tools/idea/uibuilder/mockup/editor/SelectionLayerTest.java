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
package com.android.tools.idea.uibuilder.mockup.editor;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SelectionLayerTest extends TestCase {
  public void testClearSelection() throws Exception {
    final JPanel panel = mock(JPanel.class);
    when(panel.getHeight()).thenReturn(100);
    when(panel.getWidth()).thenReturn(100);
    final SelectionLayer layer = new SelectionLayer(panel);
    layer.setSelection(0,0,10,10);
    layer.clearSelection();
    assertTrue(layer.getSelection().isEmpty());
  }

  public void testSetFixedRatio() throws Exception {
    final JPanel panel = mock(JPanel.class);
    when(panel.getHeight()).thenReturn(100);
    when(panel.getWidth()).thenReturn(100);
    final SelectionLayer layer = new SelectionLayer(panel);
    layer.setBounds(0,0,10,10);
    layer.setSelection(0,0,10,10);
    assertEquals(new Rectangle(0,0,10,10), layer.getSelection());
    layer.setAspectRatio(1,2);
    assertEquals(new Rectangle(0,0,5,10), layer.getSelection());
  }
}