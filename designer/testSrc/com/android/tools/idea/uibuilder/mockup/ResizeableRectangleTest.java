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

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;


public class ResizeableRectangleTest {
  // TODO Remove ?
  private static final int ourX = 50;
  private static final int ourY = 50;
  private static final int ourWidth = 50;
  private static final int ourHeight = 50;

  @Test
  public void newInstanceTest() throws Exception {
    final ResizeableRectangle resizeableRectangle = new ResizeableRectangle(0, 0, 10, 10);
  }

  @Test
  public void newDefaultInstanceTest() throws Exception {
    final ResizeableRectangle resizeableRectangle = new ResizeableRectangle();
  }

  @Test
  public void getHitBoxTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    final int space = ResizeableRectangle.HIT_SPACE;
    assertEquals(new Rectangle(ourX - space, ourY - space, ourWidth + space, ourHeight + space), rect.getHitBox());
  }

  @NotNull
  private static ResizeableRectangle getResizeableRectangle() {
    return new ResizeableRectangle(new Rectangle(ourX, ourY, ourWidth, ourHeight));
  }

  @Test
  public void getBorderAtCoordinateOutOfBorderTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    assertNull(rect.getBorderAtCoordinate(0,0));
    assertNull(rect.getBorderAtCoordinate(ourX, ourY));
    assertNull(rect.getBorderAtCoordinate(1100000, 1111111111));
  }

  @Test
  public void getBorderAtCoordinateCenterTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    assertNull(rect.getBorderAtCoordinate((ourX + ourWidth)/2f,(ourY + ourHeight)/2f));
  }

  @Test
  public void getBorderAtCoordinateBorderNorthTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    final int x = ourX + ResizeableRectangle.HIT_SPACE;
    assertNotNull(rect.getBorderAtCoordinate(x, ourY));
    assertEquals(ResizeableRectangle.Border.Position.N, rect.getBorderAtCoordinate(x, ourY).getPosition());
  }

  @Test
  public void getBorderAtCoordinateBorderSouthTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    final int x = ourX + ResizeableRectangle.HIT_SPACE;
    final int y = ourY + ourHeight;
    assertNotNull(rect.getBorderAtCoordinate(x, y));
    assertEquals(ResizeableRectangle.Border.Position.S, rect.getBorderAtCoordinate(x, y).getPosition());
  }

  @Test
  public void getBorderAtCoordinateBorderWestTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    final int x = ourX;
    final int y = ourY + ResizeableRectangle.HIT_SPACE;
    assertNotNull(rect.getBorderAtCoordinate(x, y));
    assertEquals(ResizeableRectangle.Border.Position.W, rect.getBorderAtCoordinate(x, y).getPosition());
  }

  @Test
  public void getBorderAtCoordinateBorderEastTest() throws Exception {
    final ResizeableRectangle rect = getResizeableRectangle();
    final int x = ourX + ourWidth;
    final int y = ourY + ResizeableRectangle.HIT_SPACE;
    assertNotNull(rect.getBorderAtCoordinate(x, y));
    assertEquals(ResizeableRectangle.Border.Position.E, rect.getBorderAtCoordinate(x, y).getPosition());
  }
}