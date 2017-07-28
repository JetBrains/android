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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.idea.common.model.NlComponent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public class NlXmlPropertyBuilderTest extends PropertyTestCase {
  private PTableModel myModel;
  private NlPTable myTable;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addFileToProject("res/values/strings.xml", getStrings());
    myFixture.addFileToProject("res/values/dimens.xml", getDimens());
    myModel = new PTableModel();
    myTable = new NlPTable(myModel);
  }

  public void testTextView() {
    setProperties(myTextView);
    NlXmlPropertyBuilder builder = createBuilder(myTextView);
    builder.build();
    int rows = myModel.getRowCount();
    checkHeader(0, "layout/merge.xml");
    checkProperty(1, ANDROID_URI, ATTR_ID);
    checkProperty(2, ANDROID_URI, ATTR_LAYOUT_WIDTH);
    checkProperty(3, ANDROID_URI, ATTR_LAYOUT_HEIGHT);
    checkProperty(4, ANDROID_URI, ATTR_ELEVATION);
    checkProperty(5, ANDROID_URI, ATTR_TEXT);
    checkProperty(6, ANDROID_URI, ATTR_PADDING_BOTTOM);
    checkAddProperty(7);
    checkHeader(8, "values/dimens.xml");
    checkResourceItem(9, "bottom", "35dp");
    checkHeader(10, "values/strings.xml");
    checkResourceItem(11, "hello_world", "Hello World!");
    assertThat(rows).isEqualTo(12);
  }

  private void checkHeader(int rowIndex, @NotNull String expectedHeader) {
    PTableItem item = myTable.getItemAt(rowIndex);
    assertThat(item).isInstanceOf(NlResourceHeader.class);
    assertThat(item).isNotNull();
    assertThat(item.getName()).isEqualTo(expectedHeader);
  }

  private void checkProperty(int rowIndex, @NotNull String namespace, @NotNull String attribute) {
    PTableItem item = myTable.getItemAt(rowIndex);
    assertThat(item).isInstanceOf(NlPropertyItem.class);
    NlPropertyItem propertyItem = (NlPropertyItem)item;
    assertThat(propertyItem).isNotNull();
    assertThat(propertyItem.getNamespace()).isEqualTo(namespace);
    assertThat(propertyItem.getName()).isEqualTo(attribute);
  }

  @SuppressWarnings("SameParameterValue")
  private void checkAddProperty(int rowIndex) {
    PTableItem item = myTable.getItemAt(rowIndex);
    assertThat(item).isInstanceOf(AddPropertyItem.class);
  }

  private void checkResourceItem(int rowIndex, @NotNull String name, @NotNull String value) {
    PTableItem item = myTable.getItemAt(rowIndex);
    assertThat(item).isInstanceOf(NlResourceItem.class);
    NlResourceItem resourceItem = (NlResourceItem)item;
    assertThat(resourceItem).isNotNull();
    assertThat(resourceItem.getName()).isEqualTo(name);
    assertThat(resourceItem.getValue()).isEqualTo(value);
  }

  @NotNull
  private NlXmlPropertyBuilder createBuilder(@NotNull NlComponent... componentArray) {
    List<NlComponent> components = Arrays.asList(componentArray);
    return new NlXmlPropertyBuilder(myPropertiesManager, myTable, components, getPropertyTable(components));
  }

  @Language("XML")
  private static String getStrings() {
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
           "<resources>\n" +
           "    <string name=\"app_name\">My Application</string>\n" +
           "    <string name=\"hello_world\">Hello World!</string>\n" +
           "</resources>\n";
  }

  @Language("XML")
  private static String getDimens() {
    return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
           "<resources>\n" +
           "    <dimen name=\"bottom\">35dp</dimen>\n" +
           "    <dimen name=\"top\">20dp</dimen>\n" +
           "</resources>\n";
  }

  private void setProperties(@NotNull NlComponent textView) {
    new WriteCommandAction.Simple(getProject(), "Set Text property") {
      @Override
      protected void run() throws Throwable {
        textView.setAttribute(ANDROID_URI, ATTR_TEXT, "@string/hello_world");
        textView.setAttribute(ANDROID_URI, ATTR_PADDING_BOTTOM, "@dimen/bottom");
      }
    }.execute();
    UIUtil.dispatchAllInvocationEvents();
  }
}
