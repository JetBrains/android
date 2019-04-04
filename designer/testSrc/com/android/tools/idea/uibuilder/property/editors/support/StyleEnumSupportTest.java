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
package com.android.tools.idea.uibuilder.property.editors.support;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.CHECK_BOX;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.Dependencies;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.XmlName;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@RunsInEdt
public class StyleEnumSupportTest {

  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  private SyncNlModel myModel;
  private NlComponent myLayout;

  @Before
  public void setUp() {
    addProjectStyles();
    myModel = createModel().build();
    myLayout = myModel.getComponents().get(0);
  }

  @After
  public void tearDown() {
    myModel = null;
    myLayout = null;
  }

  @Test
  public void testFindPossibleValues() {
    Dependencies.INSTANCE.add(myProjectRule.fixture, APPCOMPAT_LIB_ARTIFACT_ID);
    NlComponent checkBox = myLayout.getChild(0);
    NlPropertyItem property = NlPropertyItem.create(new XmlName(ATTR_STYLE, ""), null, ImmutableList.of(checkBox), null);
    StyleEnumSupport support = new StyleEnumSupport(property);
    assertThat(support.getAllValues()).containsExactly(
      new ValueWithDisplayString("MyCheckBox", "@style/MyCheckBox"),
      ValueWithDisplayString.SEPARATOR,
      new ValueWithDisplayString("Widget.AppCompat.CompoundButton.CheckBox", "@style/Widget.AppCompat.CompoundButton.CheckBox"),
      ValueWithDisplayString.SEPARATOR,
      androidStyle("Widget.CompoundButton.CheckBox"),
      androidStyle("Widget.DeviceDefault.CompoundButton.CheckBox"),
      androidStyle("Widget.DeviceDefault.Light.CompoundButton.CheckBox"),
      androidStyle("Widget.Holo.CompoundButton.CheckBox"),
      androidStyle("Widget.Holo.Light.CompoundButton.CheckBox"),
      androidStyle("Widget.Material.CompoundButton.CheckBox"),
      androidStyle("Widget.Material.Light.CompoundButton.CheckBox")
    ).inOrder();
  }

  @Test
  public void testFindPossibleValuesWithInvalidXmlTag() {
    NlComponent checkBox = myLayout.getChild(0);
    NlPropertyItem property = NlPropertyItem.create(new XmlName(ATTR_STYLE, ""), null, ImmutableList.of(checkBox), null);
    deleteTag(checkBox);
    StyleEnumSupport support = new StyleEnumSupport(property);
    assertThat(support.getAllValues()).containsExactly(
      new ValueWithDisplayString("MyCheckBox", "@style/MyCheckBox"),
      ValueWithDisplayString.SEPARATOR,
      androidStyle("Widget.CompoundButton.CheckBox"),
      androidStyle("Widget.DeviceDefault.CompoundButton.CheckBox"),
      androidStyle("Widget.DeviceDefault.Light.CompoundButton.CheckBox"),
      androidStyle("Widget.Holo.CompoundButton.CheckBox"),
      androidStyle("Widget.Holo.Light.CompoundButton.CheckBox"),
      androidStyle("Widget.Material.CompoundButton.CheckBox"),
      androidStyle("Widget.Material.Light.CompoundButton.CheckBox")
    ).inOrder();
  }

  private void deleteTag(@NotNull NlComponent component) {
    XmlTag tag = component.getBackend().getTagPointer().getElement();
    WriteCommandAction.writeCommandAction(myProjectRule.getProject()).run(() -> tag.delete());
    UIUtil.dispatchAllInvocationEvents();
  }

  @NotNull
  private static ValueWithDisplayString androidStyle(@NotNull String styleName) {
    return new ValueWithDisplayString(styleName, "@android:style/" + styleName);
  }

  private void addProjectStyles() {
    String styles =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<resources xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <style name=\"MyCheckBox\" parent=\"android:Widget.CompoundButton.CheckBox\"/>\n" +
      "</resources>\n";
    myProjectRule.fixture.addFileToProject("res/values/styles.xml", styles);
  }

  private ModelBuilder createModel() {
    return NlModelBuilderUtil.model(
      myProjectRule,
      "layout",
      "constraint.xml",
      new ComponentDescriptor(CONSTRAINT_LAYOUT.defaultName())
        .id("@id/root")
        .withBounds(0, 0, 2000, 2000)
        .width("1000dp")
        .height("1000dp")
        .withAttribute("android:padding", "20dp")
        .children(
          new ComponentDescriptor(CHECK_BOX)
            .id("@id/button")
            .withBounds(100, 100, 200, 40)
            .width("100dp")
            .height("20dp"),
          new ComponentDescriptor(TEXT_VIEW)
            .id("@id/button")
            .withBounds(200, 100, 200, 40)
            .width("100dp")
            .height("20dp")
        ));
  }
}
