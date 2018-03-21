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

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.uibuilder.property.MockNlComponent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.TOOLS_URI;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class NlComponentTest extends AndroidTestCase {
  private NlModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myModel = mock(NlModel.class);
  }

  private NlComponent createComponent(@NotNull XmlTag tag) {
    NlComponent result = new NlComponent(myModel, tag, createTagPointer(tag));
    NlComponentHelper.INSTANCE.registerComponent(result);
    return result;
  }

  static XmlTag createTag(String tagName) {
    XmlTag tag = mock(XmlTag.class);
    when(tag.getName()).thenReturn(tagName);

    return tag;
  }

  static SmartPsiElementPointer<XmlTag> createTagPointer(XmlTag tag) {
    SmartPsiElementPointer<XmlTag> tagPointer = mock(SmartPsiElementPointer.class);
    when(tagPointer.getElement()).thenReturn(tag);
    return tagPointer;
  }

  public void testNeedsDefaultId() {
    assertFalse(NlComponentHelperKt.needsDefaultId(createComponent(createTag("SwitchPreference"))));
  }

  public void testBasic() {
    NlComponent linearLayout = createComponent(createTag("LinearLayout"));
    NlComponent textView = createComponent(createTag("TextView"));
    NlComponent button = createComponent(createTag("Button"));

    assertThat(linearLayout.getChildren()).isEmpty();

    linearLayout.addChild(textView);
    linearLayout.addChild(button);

    assertEquals(Arrays.asList(textView, button), linearLayout.getChildren());
    assertEquals("LinearLayout", linearLayout.getTag().getName());
    assertEquals("Button", button.getTag().getName());

    assertSame(linearLayout, linearLayout.findViewByTag(linearLayout.getTag()));
    assertSame(button, linearLayout.findViewByTag(button.getTag()));
    assertSame(textView, linearLayout.findViewByTag(textView.getTag()));
    assertEquals(Collections.singletonList(textView), linearLayout.findViewsByTag(textView.getTag()));

    NlComponentHelperKt.setBounds(linearLayout, 0, 0, 1000, 800);
    NlComponentHelperKt.setBounds(textView, 0, 0, 200, 100);
    NlComponentHelperKt.setBounds(button, 10, 110, 400, 100);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x800}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,0:200x100}\n" +
                 "    NlComponent{tag=<Button>, bounds=[10,110:400x100}",
                 NlTreeDumper.dumpTree(Collections.singletonList(linearLayout)));
  }

  private XmlTag createTagFromXml(String xml) {
    return XmlElementFactory.getInstance(myModule.getProject()).createTagFromText(xml);
  }

  public void testAttributeTransactions() {
    XmlTag linearLayoutXmlTag = createTagFromXml(
      "<LinearLayout" +
      " xmlns:android=\"" + ANDROID_URI + "\"" +
      " xmlns:tools=\"" + TOOLS_URI + "\"" +
      " android:layout_width=\"wrap_content\"" +
      " android:layout_height=\"wrap_content\" />");
    XmlTag textViewXmlTag = createTagFromXml(
      "<TextView" +
      " xmlns:android=\"" + ANDROID_URI + "\"" +
      " xmlns:tools=\"" + TOOLS_URI + "\"" +
      " android:text=\"Initial\"" +
      " android:layout_width=\"wrap_content\"" +
      " android:layout_height=\"wrap_content\" />");

    NlComponent linearLayout = createComponent(linearLayoutXmlTag);
    NlComponent textView = createComponent(textViewXmlTag);

    linearLayout.addChild(textView);

    AttributesTransaction transaction = textView.startAttributeTransaction();
    assertEquals(transaction, textView.startAttributeTransaction());
    assertNotEquals(transaction, linearLayout.startAttributeTransaction());

    assertFalse(transaction.isComplete());
    assertFalse(transaction.isSuccessful());

    assertEquals("wrap_content", transaction.getAndroidAttribute("layout_width"));
    assertEquals("Initial", transaction.getAndroidAttribute("text"));

    transaction.setAndroidAttribute("layout_width", "150dp");
    transaction.setAttribute(ANDROID_URI, "text", "Hello world");

    // Before rollback
    assertEquals("150dp", transaction.getAndroidAttribute("layout_width"));
    assertEquals("Hello world", transaction.getAndroidAttribute("text"));
    assertEquals("wrap_content", textViewXmlTag.getAttribute("android:layout_width").getValue());
    assertEquals("Initial", textViewXmlTag.getAttribute("android:text").getValue());

    assertTrue(transaction.rollback());

    assertTrue(transaction.isComplete());
    assertFalse(transaction.isSuccessful());

    assertEquals("wrap_content", textViewXmlTag.getAttribute("android:layout_width").getValue());
    assertEquals("Initial", textViewXmlTag.getAttribute("android:text").getValue());

    AttributesTransaction oldTransaction = transaction;
    transaction = textView.startAttributeTransaction();
    assertNotEquals(oldTransaction, transaction);
    assertFalse(transaction.commit()); // Empty commit

    transaction = textView.startAttributeTransaction();
    transaction.setAttribute(ANDROID_URI, "layout_width", "150dp");
    // Check the namespace handling
    transaction.setAttribute(TOOLS_URI, "layout_width", "TOOLS-WIDTH");
    transaction.setAndroidAttribute("text", "Hello world");

    // Before commit
    // - The XML tag will have the old values
    // - The NlComponent will have the old values
    // - Only the transaction will have the new values
    assertEquals("wrap_content", textViewXmlTag.getAttribute("android:layout_width").getValue());
    assertEquals("Initial", textViewXmlTag.getAttribute("android:text").getValue());
    assertEquals("wrap_content", textView.getAndroidAttribute("layout_width"));
    assertEquals("Initial", textView.getAndroidAttribute("text"));
    assertEquals("150dp", transaction.getAndroidAttribute("layout_width"));
    assertEquals("Hello world", transaction.getAndroidAttribute("text"));

    assertTrue(transaction.commit());
    assertTrue(transaction.isSuccessful());
    assertTrue(transaction.isComplete());

    // Check XML tag values after the commit
    assertEquals("150dp", textViewXmlTag.getAttribute("android:layout_width").getValue());
    assertEquals("TOOLS-WIDTH", textViewXmlTag.getAttribute("tools:layout_width").getValue());
    assertEquals("Hello world", textViewXmlTag.getAttribute("android:text").getValue());
  }

  public void testAttributeTransactionsConflicts() {
    XmlTag linearLayoutXmlTag = createTagFromXml(
      "<LinearLayout" +
      " xmlns:android=\"" + ANDROID_URI + "\"" +
      " xmlns:tools=\"" + TOOLS_URI + "\"" +
      " android:layout_width=\"wrap_content\"" +
      " android:layout_height=\"wrap_content\" />");
    XmlTag textViewXmlTag = createTagFromXml(
      "<TextView" +
      " xmlns:android=\"" + ANDROID_URI + "\"" +
      " xmlns:tools=\"" + TOOLS_URI + "\"" +
      " android:text=\"Initial\"" +
      " android:layout_width=\"wrap_content\"" +
      " android:layout_height=\"wrap_content\" />");
    linearLayoutXmlTag.addSubTag(textViewXmlTag, true);

    NlComponent linearLayout = createComponent(linearLayoutXmlTag);
    NlComponent textView = createComponent(textViewXmlTag);

    linearLayout.addChild(textView);

    AttributesTransaction transaction = textView.startAttributeTransaction();
    transaction.setAndroidAttribute("layout_width", "150dp");
    transaction.setAndroidAttribute("layout_height", null);

    // Change the XML tag content before the transaction is committed and remove one tag
    textViewXmlTag.setAttribute("android:layout_width", "300dp");
    textViewXmlTag.setAttribute("android:layout_height", "900dp");
    assertTrue(transaction.commit());
    assertTrue(transaction.isComplete());
    assertTrue(transaction.isSuccessful());

    // Check XML tag values after the commit (the commit takes precedence to the XML modifications)
    assertEquals("150dp", textViewXmlTag.getAttribute("android:layout_width").getValue());
    // Check we haven't executed the delete on the attribute that was modified
    assertEquals("900dp", textViewXmlTag.getAttribute("android:layout_height").getValue());
  }

  public void testRemoveObsoleteAttributes() throws Exception {
    @Language("XML")
    String editText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<RelativeLayout\n" +
                      "  xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "  xmlns:tools=\"http://schemas.android.com/tools\">" +
                      "  <Button\n" +
                      "         android:id=\"@+id/editText\"\n" +
                      "         android:layout_width=\"wrap_content\"\n" +
                      "         android:layout_height=\"wrap_content\"\n" +
                      "         android:ems=\"10\"\n" +
                      "         android:inputType=\"textEmailAddress\"\n" +
                      "         android:orientation=\"vertical\"\n" +
                      "         tools:layout_editor_absoluteX=\"32dp\"\n" +
                      "         tools:layout_editor_absoluteY=\"43dp\"\n/>" +
                      "</RelativeLayout>";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", editText);

    XmlTag[] subTags = xmlFile.getRootTag().getSubTags();
    assertEquals(1, subTags.length);

    NlComponent component = MockNlComponent.create(subTags[0]);
    component.setMixin(new NlComponentMixin(component));
    CommandProcessor.getInstance().runUndoTransparentAction(component::removeObsoleteAttributes);

    @Language("XML")
    String expected = "<Button\n" +
                      "         android:id=\"@+id/editText\"\n" +
                      "         android:layout_width=\"wrap_content\"\n" +
                      "         android:layout_height=\"wrap_content\"\n" +
                      "         android:ems=\"10\"\n" +
                      "         android:inputType=\"textEmailAddress\"\n" +
                      "         />";
    assertEquals(expected, component.getTag().getText());
  }

  public void testIdFromMixin() {
    XmlTag tag = mock(XmlTag.class);
    when(tag.isValid()).thenReturn(true);
    NlComponent component = new NlComponent(mock(NlModel.class), tag, mock(SmartPsiElementPointer.class));

    NlComponent.XmlModelComponentMixin mixin = mock(NlComponent.XmlModelComponentMixin.class);
    when(mixin.getAttribute(ANDROID_URI, ATTR_ID)).thenReturn("@id/mixinId");
    component.setMixin(mixin);
    assertEquals("mixinId", component.getId());

    when(tag.getAttributeValue(ATTR_ID, ANDROID_URI)).thenReturn("@id/componentId");
    component = new NlComponent(mock(NlModel.class), tag, mock(SmartPsiElementPointer.class));
    component.setMixin(mixin);
    assertEquals("componentId", component.getId());
  }
}
