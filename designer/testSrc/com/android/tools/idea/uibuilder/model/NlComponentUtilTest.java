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
import com.android.tools.idea.common.model.NlComponentUtil;
import com.android.tools.idea.common.model.NlModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlComponentUtilTest extends AndroidTestCase {
  private NlModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myModel = mock(NlModel.class);
    when(myModel.getProject()).thenReturn(myModule.getProject());
  }

  @NotNull
  private XmlTag createTag(@NotNull String tagName) {
    String text = String.format("<%s id=\"@+id/%s\" layout_width=\"wrap_content\" layout_height=\"wrap_content\"/>", tagName, tagName);
    return XmlElementFactory.getInstance(getProject()).createTagFromText(text);
  }

  @NotNull
  private NlComponent createComponent(@NotNull XmlTag tag) {
    NlComponent result = new NlComponent(myModel, tag, createTagPointer(tag));
    NlComponentRegistrar.INSTANCE.accept(result);
    return result;
  }

  @NotNull
  private static SmartPsiElementPointer<XmlTag> createTagPointer(XmlTag tag) {
    //noinspection unchecked
    SmartPsiElementPointer<XmlTag> tagPointer = mock(SmartPsiElementPointer.class);
    when(tagPointer.getElement()).thenReturn(tag);
    return tagPointer;
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

  public void testIsDescendantInModelWithLoop() {
    NlComponent root = createComponent(createTag("LinearLayout"));
    NlComponent child1 = createComponent(createTag("TextView"));
    NlComponent child2 = createComponent(createTag("Button"));

    NlComponent notRoot = createComponent(createTag("LinearLayout"));


    root.addChild(child1);
    root.addChild(child2);
    child1.addChild(root);

    // In this case we expect an AssertionError
    try {
      NlComponentUtil.isDescendant(child1, ImmutableList.of(notRoot));
      fail("Expected assertion for a Model containing a loop");
    } catch (AssertionError e) {
      assertEquals("Loop found in NlModel. \n" +
                   "NlComponent{tag=<TextView>, bounds=[0,0:0x0}\n" +
                   "    NlComponent{tag=<LinearLayout>, bounds=[0,0:0x0}\n" +
                   "        !!LOOP!! NlComponent{tag=<TextView>, bounds=[0,0:0x0}\n" +
                   "        NlComponent{tag=<Button>, bounds=[0,0:0x0}", e.getMessage());
    }
  }
}