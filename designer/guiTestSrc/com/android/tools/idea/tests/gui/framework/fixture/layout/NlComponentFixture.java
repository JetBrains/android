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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.base.Objects;
import com.intellij.psi.xml.XmlTag;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Represents a view in the layout editor
 */
public class NlComponentFixture {
  private final Robot myRobot;
  private final NlComponent myComponent;
  private final DesignSurface mySurface;

  public NlComponentFixture(@NotNull Robot robot,
                            @NotNull NlComponent component,
                            @NotNull DesignSurface surface) {
    myRobot = robot;
    myComponent = component;
    mySurface = surface;
  }

  /** Returns the center point in panel coordinates */
  @NotNull
  private Point getMidPoint() {
    ScreenView screenView = mySurface.getCurrentScreenView();
    assertNotNull(screenView);
    int midX = Coordinates.getSwingX(screenView, myComponent.x + myComponent.w / 2);
    int midY = Coordinates.getSwingX(screenView, myComponent.y + myComponent.h / 2);
    return new Point(midX, midY);
  }

  public void requireAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value) {
    String actualValue = myComponent.getAttribute(namespace, attribute);
    assertEquals(value, actualValue);
  }

  public void requireViewClass(@NotNull String fqn) {
    ViewInfo viewInfo = myComponent.viewInfo;
    assertNotNull("No layoutlib ViewInfo", viewInfo);
    Object viewObject = viewInfo.getViewObject();
    assertNotNull("No layoutlib view object in the ViewInfo", viewObject);
    assertEquals(fqn, viewObject.getClass().getName());
  }

  public void requireActualText(@NotNull String expectedText) {
    ViewInfo viewInfo = myComponent.viewInfo;
    assertNotNull("No layoutlib ViewInfo", viewInfo);
    Object viewObject = viewInfo.getViewObject();
    assertNotNull("No layoutlib view object in the ViewInfo", viewObject);
    try {
      Method getText = viewObject.getClass().getMethod("getText");
      String actualText = (String)getText.invoke(viewObject);
      assertEquals(expectedText, actualText);
    }
    catch (NoSuchMethodException e) {
      fail("No getText() method on " + viewObject.getClass().getName());
    }
    catch (InvocationTargetException e) {
      fail("Can't invoke getText() method on " + viewObject.getClass().getName());
    }
    catch (IllegalAccessException e) {
      fail("Can't access getText() method on " + viewObject.getClass().getName());
    }
  }

  /** Click in the middle of the view (typically selects it) */
  public void click() {
    new ComponentDriver(myRobot).click(mySurface, getMidPoint());
    myRobot.waitForIdle();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    NlComponentFixture that = (NlComponentFixture)o;
    if (!myComponent.equals(that.myComponent)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myComponent.hashCode();
  }

  @Override
  public String toString() {
    return describe(myComponent);
  }

  @NotNull
  private static String describe(@NotNull NlComponent root) {
    return Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.getTag()))
      .add("id", root.getId())
      .add("bounds", "[" + root.x + "," + root.y + ":" + root.w + "x" + root.h)
      .toString();
  }

  @NotNull
  private static String describe(@Nullable XmlTag tag) {
    if (tag == null) {
      return "";
    } else {
      return '<' + tag.getName() + '>';
    }
  }
}
