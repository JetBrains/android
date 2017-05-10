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
package com.android.tools.adtui;

import com.android.tools.adtui.model.event.KeyboardAction;
import com.android.tools.adtui.model.event.KeyboardData;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class KeyboardEventRendererTest {

  private KeyboardEventRenderer myRenderer;

  @Mock private Icon myIcon;
  @Mock private Graphics2D myGraphics2D;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    JPanel jp = new JPanel();
    FontMetrics defaultMetrics = jp.getFontMetrics(jp.getFont());
    when(myGraphics2D.getFontMetrics()).thenReturn(defaultMetrics);
    when(myGraphics2D.drawImage(any(Image.class), anyInt(), anyInt(), any(Component.class))).thenReturn(true);
    myRenderer = new KeyboardEventRenderer();
  }

  @Test
  public void testStringPaint() {
    String textToDraw = "Text";
    myRenderer.draw(new JPanel(), myGraphics2D, new AffineTransform(), 0,
                    new KeyboardAction(0, 0, new KeyboardData(textToDraw)));
    verify(myGraphics2D).drawString(eq(textToDraw), anyInt(), anyInt());
  }

  @Test
  public void testIconPaint() {
    JPanel panel = new JPanel();
    myRenderer.draw(panel, myGraphics2D, new AffineTransform(), 0,
                    new KeyboardAction(0, 0, new KeyboardData("KEYCODE_BACK")));
    verify(myGraphics2D).drawImage(any(Image.class), anyInt(), anyInt(), eq(panel));
  }
}
