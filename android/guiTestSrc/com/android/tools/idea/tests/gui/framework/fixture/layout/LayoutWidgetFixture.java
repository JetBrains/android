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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderedView;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class LayoutWidgetFixture {
  private final RenderedView myView;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private LayoutFixture myLayoutFixture;

  public LayoutWidgetFixture(LayoutFixture layoutFixture, RenderedView view) {
    myLayoutFixture = layoutFixture;
    myView = view;
  }

  public void requireTag(@NotNull String tag) {
    assertNotNull(myView.tag);
    assertEquals(tag, myView.tag.getName());
  }

  public void requireAttribute(@NotNull String attribute, @Nullable String namespace, @Nullable String value) {
    XmlTag tag = myView.tag;
    assertNotNull(tag);
    String actualValue = namespace != null ? tag.getAttributeValue(attribute, namespace) : tag.getAttributeValue(attribute);
    assertEquals(value, actualValue);
  }

  public void requireViewClass(@NotNull String fqn) {
    ViewInfo viewInfo = myView.view;
    assertNotNull("No layoutlib ViewInfo", viewInfo);
    Object viewObject = viewInfo.getViewObject();
    assertNotNull("No layoutlib view object in the ViewInfo", viewObject);
    assertEquals(fqn, viewObject.getClass().getName());
  }

  public void requireActualText(@NotNull String expectedText) {
    ViewInfo viewInfo = myView.view;
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

  @Override
  public String toString() {
    assertNotNull(myView.tag);
    return myView.tag.toString();
  }
}
