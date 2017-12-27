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
package com.android.tools.idea.uibuilder.model;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.uibuilder.model.NlComponentTest.createTag;

import static org.mockito.Mockito.mock;

public class NlComponentUtilTest extends AndroidTestCase {
  private NlModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myModel = mock(NlModel.class);
  }

  @NotNull
  private NlComponent createComponent(@NotNull XmlTag tag) {
    NlComponent result = new NlComponent(myModel, tag);
    NlComponentHelper.INSTANCE.registerComponent(result);
    return result;
  }

  public void testGroupSiblings() {
    NlComponent linearLayout = createComponent(createTag("LinearLayout"));
    NlComponent textView = createComponent(createTag("TextView"));
    NlComponent button = createComponent(createTag("Button"));
    linearLayout.addChild(textView);
    linearLayout.addChild(button);

    NlComponent linearLayout2 = createComponent(createTag("LinearLayout"));
    NlComponent textView2 = createComponent(createTag("TextView"));
    NlComponent imageView2 = createComponent(createTag("TextView"));
    linearLayout2.addChild(textView2);
    linearLayout2.addChild(imageView2);

    NlComponent emptyLinearLayout = createComponent(createTag("LinearLayout"));

    Multimap<NlComponent, NlComponent> result = NlComponentUtil.groupSiblings(
      ImmutableList.of(linearLayout, textView, button));
    assertSize(2, result.keySet()); // null key, LinearLayout
    assertSameElements(result.get(null), linearLayout);
    assertSameElements(result.get(linearLayout), textView, button); // textView and button are siblings with linearLayout being their parent

    result = NlComponentUtil.groupSiblings(
      ImmutableList.of(linearLayout, linearLayout2, emptyLinearLayout, button, textView, textView2, imageView2));
    assertSize(3, result.keySet()); // null key, linearLayout, linearLayout2
    assertSameElements(result.get(null), linearLayout, linearLayout2, emptyLinearLayout);
    assertSameElements(result.get(linearLayout), textView, button);
    assertSameElements(result.get(linearLayout2), textView2, imageView2);

    result = NlComponentUtil.groupSiblings(ImmutableList.of());
    assertEmpty(result.keySet());
  }


  public void testIsDescendant() {
    NlComponent linearLayout = createComponent(createTag("LinearLayout"));
    NlComponent textView = createComponent(createTag("TextView"));
    NlComponent button = createComponent(createTag("Button"));

    NlComponent linearLayout2 = createComponent(createTag("LinearLayout"));

    linearLayout.addChild(textView);
    linearLayout.addChild(button);

    assertTrue(NlComponentUtil.isDescendant(textView, ImmutableList.of(textView)));
    assertTrue(NlComponentUtil.isDescendant(textView, ImmutableList.of(linearLayout)));
    assertTrue(NlComponentUtil.isDescendant(button, ImmutableList.of(linearLayout)));
    assertTrue(NlComponentUtil.isDescendant(button, ImmutableList.of(linearLayout2, linearLayout)));
    assertFalse(NlComponentUtil.isDescendant(button, ImmutableList.of(linearLayout2)));
    assertFalse(NlComponentUtil.isDescendant(button, ImmutableList.of(linearLayout2)));
    assertFalse(NlComponentUtil.isDescendant(linearLayout2, ImmutableList.of(linearLayout)));
    assertFalse(NlComponentUtil.isDescendant(textView, ImmutableList.of()));
  }
}