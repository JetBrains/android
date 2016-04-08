/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Objects;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static org.junit.Assert.assertEquals;

/**
 * Represents a view in the layout editor
 */
public class LayoutEditorComponentFixture {
  private final Robot myRobot;
  private final RadViewComponent myComponent;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final LayoutEditorFixture myEditorFixture;
  private final AndroidDesignerEditorPanel myPanel;

  LayoutEditorComponentFixture(@NotNull Robot robot,
                               @NotNull RadViewComponent component,
                               @NotNull LayoutEditorFixture editorFixture,
                               @NotNull AndroidDesignerEditorPanel panel) {
    myRobot = robot;
    myComponent = component;
    myEditorFixture = editorFixture;
    myPanel = panel;
  }

  /** Returns the bounds of this view in panel coordinates */
  @NotNull
  public Rectangle getViewBounds() {
    return myComponent.getBounds(myPanel.getComponent());
  }

  /** Returns the center point in panel coordinates */
  @NotNull
  public Point getMidPoint() {
    Rectangle viewBounds = getViewBounds();
    return new Point((int)viewBounds.getCenterX(), (int)viewBounds.getCenterY());
  }

  /** Click on the view (typically selects it) */
  public void click() {
    new ComponentDriver(myRobot).click(myPanel.getComponent(), getMidPoint());
    myRobot.waitForIdle();
  }

  /**
   * Returns the tag name of the component
   */
  @NotNull
  public String getTagName() {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> myComponent.getTag().getName());
  }

  @NotNull
  RadViewComponent getComponent() {
    return myComponent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    LayoutEditorComponentFixture that = (LayoutEditorComponentFixture)o;
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
  private static String describe(@NotNull RadViewComponent root) {
    return Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.getTag()))
      .add("id", root.getId())
      .add("bounds", describe(root.getBounds()))
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

  @NotNull
  private static String describe(@NotNull Rectangle rectangle) {
    // More brief description than toString default: java.awt.Rectangle[x=0,y=100,width=768,height=1084]
    return "[" + rectangle.x + "," + rectangle.y + ":" + rectangle.width + "x" + rectangle.height;
  }

  public void requireXml(String expected, boolean collapseSpaces) {
    String xml = ApplicationManager.getApplication().runReadAction((Computable<String>)() -> myComponent.getTag().getText());
    if (collapseSpaces) {
      while (true) {
        String prev = xml;
        xml = xml.replaceAll("  ", " ");
        if (xml.equals(prev)) {
          break;
        }
      }
      xml = xml.replaceAll("\n ", "\n");
    }
    assertEquals(xml, expected);
  }
}
