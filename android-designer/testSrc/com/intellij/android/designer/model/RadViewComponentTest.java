/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model;

import com.intellij.android.designer.designSurface.TransformedComponent;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

public class RadViewComponentTest extends TestCase {
  public void testModelRectangleDpConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    assertEquals(new Rectangle(100, 110, 300, 400), testComponent.toModelDp(160, nativeComponent, new Rectangle(100, 110, 300, 400)));
    assertEquals(new Rectangle(100, 110, 300, 400),
                 testComponent.toModelDp(160, panel, new Rectangle(100 * 2 + 15, 110 * 2 + 20, 300 * 2, 400 * 2)));
    assertEquals(new Rectangle(100, 110, 300, 400),
                 testComponent.toModelDp(160, other, new Rectangle(100 * 2 + 15 - 5, 110 * 2 + 20 - 5, 300 * 2, 400 * 2)));
    assertEquals(new Rectangle(100 / 2, 110 / 2, 300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, nativeComponent, new Rectangle(100, 110, 300, 400)));
    assertEquals(new Rectangle(100 / 2, 110 / 2, 300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, panel, new Rectangle(100 * 2 + 15, 110 * 2 + 20, 300 * 2, 400 * 2)));
    assertEquals(new Rectangle(100 / 2, 110 / 2, 300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, other, new Rectangle(100 * 2 + 15 - 5, 110 * 2 + 20 - 5, 300 * 2, 400 * 2)));
  }

  public void testModelPointDpConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    assertEquals(new Point(100, 110), testComponent.toModelDp(160, nativeComponent, new Point(100, 110)));
    assertEquals(new Point(100, 110),
                 testComponent.toModelDp(160, panel, new Point(100 * 2 + 15, 110 * 2 + 20)));
    assertEquals(new Point(100, 110),
                 testComponent.toModelDp(160, other, new Point(100 * 2 + 15 - 5, 110 * 2 + 20 - 5)));
    assertEquals(new Point(100 / 2, 110 / 2),
                 testComponent.toModelDp(2 * 160, nativeComponent, new Point(100, 110)));
    assertEquals(new Point(100 / 2, 110 / 2),
                 testComponent.toModelDp(2 * 160, panel, new Point(100 * 2 + 15, 110 * 2 + 20)));
    assertEquals(new Point(100 / 2, 110 / 2),
                 testComponent.toModelDp(2 * 160, other, new Point(100 * 2 + 15 - 5, 110 * 2 + 20 - 5)));
  }

  public void testModelDimensionDpConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    assertEquals(new Dimension(300, 400), testComponent.toModelDp(160, nativeComponent, new Dimension(300, 400)));
    assertEquals(new Dimension(300, 400),
                 testComponent.toModelDp(160, panel, new Dimension(300 * 2, 400 * 2)));
    assertEquals(new Dimension(300, 400),
                 testComponent.toModelDp(160, other, new Dimension(300 * 2, 400 * 2)));
    assertEquals(new Dimension(300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, nativeComponent, new Dimension(300, 400)));
    assertEquals(new Dimension(300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, panel, new Dimension(300 * 2, 400 * 2)));
    assertEquals(new Dimension(300 / 2, 400 / 2),
                 testComponent.toModelDp(2 * 160, other, new Dimension(300 * 2, 400 * 2)));
  }

  private static class TestComponent extends RadViewComponent { // Because RadViewComponent is abstract
  }

  private static class MyRoot extends JComponent implements TransformedComponent {
    private double myScale;

    private MyRoot(double scale) {
      myScale = scale;
    }

    @Override
    public double getScale() {
      return myScale;
    }

    @Override
    public int getShiftX() {
      return 0;
    }

    @Override
    public int getShiftY() {
      return 0;
    }
  }
}
