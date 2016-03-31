/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.intellij.psi.xml.XmlTag;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NlComponentTest {
  private NlModel myModel;

  @Before
  public void mockModel() {
    myModel = mock(NlModel.class);
  }

  private static XmlTag createTag(String tagName) {
    XmlTag tag = mock(XmlTag.class);
    when(tag.getName()).thenReturn(tagName);
    return tag;
  }

  @Test
  public void needsDefaultId() {
    assertFalse(new NlComponent(myModel, createTag("SwitchPreference")).needsDefaultId());
  }

  @Test
  public void test() {
    NlComponent linearLayout = new NlComponent(myModel, createTag("LinearLayout"));
    NlComponent textView = new NlComponent(myModel, createTag("TextView"));
    NlComponent button = new NlComponent(myModel, createTag("Button"));

    assertEquals(Collections.emptyList(), linearLayout.getChildren());

    linearLayout.addChild(textView);
    linearLayout.addChild(button);

    assertEquals(Arrays.asList(textView, button), linearLayout.getChildren());
    assertEquals("LinearLayout", linearLayout.getTag().getName());
    assertEquals("Button", button.getTag().getName());

    assertSame(linearLayout, linearLayout.findViewByTag(linearLayout.getTag()));
    assertSame(button, linearLayout.findViewByTag(button.getTag()));
    assertSame(textView, linearLayout.findViewByTag(textView.getTag()));
    assertEquals(Collections.singletonList(textView), linearLayout.findViewsByTag(textView.getTag()));

    linearLayout.setBounds(0, 0, 1000, 800);
    textView.setBounds(0, 0, 200, 100);
    button.setBounds(10, 110, 400, 100);

    assertSame(linearLayout, linearLayout.findLeafAt(500, 500));
    assertSame(textView, linearLayout.findLeafAt(20, 20));
    assertSame(button, linearLayout.findLeafAt(20, 120));

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x800}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,0:200x100}\n" +
                 "    NlComponent{tag=<Button>, bounds=[10,110:400x100}",
                 NlComponent.toTree(Collections.singletonList(linearLayout)));
  }
}
