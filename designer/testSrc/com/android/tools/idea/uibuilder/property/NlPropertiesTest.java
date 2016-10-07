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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;

public class NlPropertiesTest extends LayoutTestCase {
  private static final String CUSTOM_NAMESPACE = "http://schemas.android.com/apk/res/p1.p2";
  private static final String[] NO_NAMESPACE_VIEW_ATTRS = {"style"};
  private static final String[] ANDROID_VIEW_ATTRS = {"id", "padding", "visibility", "textAlignment", "translationZ", "elevation"};
  private static final String[] TEXT_VIEW_ATTRS = {"text", "hint", "textColor", "textSize"};

  private static final String[] FRAME_LAYOUT_ATTRS = {"layout_gravity"};
  private static final String[] GRID_LAYOUT_ATTRS = {"layout_rowSpan", "layout_column"};
  private static final String[] LINEAR_LAYOUT_ATTRS = {"layout_weight"};
  private static final String[] RELATIVE_LAYOUT_ATTRS = {"layout_toLeftOf", "layout_above", "layout_alignTop"};

  public void testViewAttributes() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<View xmlns:android=\"http://schemas.android.com/apk/res/android\" />";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "View";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    Table<String, String, NlPropertyItem> properties = NlProperties.getInstance().getProperties(
      ImmutableList.of(MockNlComponent.create(rootTag)));
    assertTrue(properties.size() > 120); // at least 124 attributes (view + layouts) are available as of API 22

    // check that some of the View's attributes are there..
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);

    // check that non-existent properties haven't been added
    assertAbsent(tag, properties, SdkConstants.ANDROID_URI, TEXT_VIEW_ATTRS);

    // Views that don't have a parent layout have all the layout attributes available to them..
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, RELATIVE_LAYOUT_ATTRS);
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, GRID_LAYOUT_ATTRS);
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, FRAME_LAYOUT_ATTRS);
  }

  public void testViewInRelativeLayout() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "TextView";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(ImmutableList.of(MockNlComponent.create(subTags[0])));
    assertTrue(properties.size() > 180); // at least 190 attributes are available as of API 22

    // A text view should have all of its attributes and the parent class's (View) attributes
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, TEXT_VIEW_ATTRS);
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);

    // Since it is embedded inside a relative layout, it should only have relative layout's layout attributes
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, RELATIVE_LAYOUT_ATTRS);
    assertAbsent(tag, properties, SdkConstants.ANDROID_URI, GRID_LAYOUT_ATTRS);
    assertAbsent(tag, properties, SdkConstants.ANDROID_URI, FRAME_LAYOUT_ATTRS);
  }

  public void testCustomViewAttributes() {
    XmlFile xmlFile = setupCustomViewProject();

    String tag = "p1.p2.PieChart";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(ImmutableList.of(MockNlComponent.create(subTags[0])));
    assertTrue("# of properties lesser than expected: " + properties.size(), properties.size() > 90);

    assertPresent(tag, properties, SdkConstants.ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);
    assertPresent(tag, properties, SdkConstants.ANDROID_URI, LINEAR_LAYOUT_ATTRS);
    assertAbsent(tag, properties, SdkConstants.ANDROID_URI, TEXT_VIEW_ATTRS);
  }

  public void testPropertyNames() {
    XmlFile xmlFile = setupCustomViewProject();

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(ImmutableList.of(MockNlComponent.create(subTags[0])));

    NlPropertyItem p = properties.get(SdkConstants.ANDROID_URI, "id");
    assertNotNull(p);

    assertEquals("id", p.getName());

    String expected = "@android:id:  Supply an identifier name for this view, to later retrieve it\n" +
                      "             with {@link android.view.View#findViewById View.findViewById()} or\n" +
                      "             {@link android.app.Activity#findViewById Activity.findViewById()}.\n" +
                      "             This must be a\n" +
                      "             resource reference; typically you set this using the\n" +
                      "             <code>@+</code> syntax to create a new ID resources.\n" +
                      "             For example: <code>android:id=\"@+id/my_id\"</code> which\n" +
                      "             allows you to later retrieve the view\n" +
                      "             with <code>findViewById(R.id.my_id)</code>. ";

    assertEquals(expected, p.getTooltipText());

    p = properties.get(CUSTOM_NAMESPACE, "legend");
    assertNotNull(p);

    assertEquals("legend", p.getName());
    assertEquals("legend", p.getTooltipText());
  }

  private XmlFile setupCustomViewProject() {
    @Language("XML")
    String layoutSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                       "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                       "  <p1.p2.PieChart />" +
                       "</LinearLayout>";

    @Language("XML")
    String attrsSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<resources>\n" +
                      "    <declare-styleable name=\"PieChart\">\n" +
                      "        <attr name=\"legend\" format=\"boolean\" />\n" +
                      "        <attr name=\"labelPosition\" format=\"enum\">\n" +
                      "            <enum name=\"left\" value=\"0\"/>\n" +
                      "            <enum name=\"right\" value=\"1\"/>\n" +
                      "        </attr>\n" +
                      "    </declare-styleable>\n" +
                      "</resources>";

    @Language("JAVA")
    String javaSrc = "package p1.p2;\n" +
                     "\n" +
                     "import android.content.Context;\n" +
                     "import android.view.View;\n" +
                     "\n" +
                     "public class PieChart extends View {\n" +
                     "    public PieChart(Context context) {\n" +
                     "        super(context);\n" +
                     "    }\n" +
                     "}\n";

    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", layoutSrc);
    myFixture.addFileToProject("res/values/attrs.xml", attrsSrc);
    myFixture.addFileToProject("src/p1/p2/PieChart.java", javaSrc);
    return xmlFile;
  }

  public void testAppCompatIssues() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    @Language("XML")
    String attrsSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<resources>\n" +
                      "    <declare-styleable name=\"View\">\n" +
                      "        <attr name=\"android:focusable\" />\n" +
                      "        <attr name=\"theme\" format=\"reference\" />\n" +
                      "        <attr name=\"android:theme\" />\n" +
                      "        <attr name=\"custom\" />\n" +
                      "    </declare-styleable>\n" +
                      "</resources>";
    myFixture.addFileToProject("res/values/attrs.xml", attrsSrc);

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(ImmutableList.of(MockNlComponent.create(subTags[0])));
    assertTrue(properties.size() > 180); // at least 190 attributes are available as of API 22

    // The attrs.xml in appcompat-22.0.0 includes android:focusable, theme and android:theme.
    // The android:focusable refers to the platform attribute, and hence should not be duplicated..
    assertPresent("TextView", properties, SdkConstants.ANDROID_URI, "focusable", "theme");
    assertPresent("TextView", properties, CUSTOM_NAMESPACE, "custom");
    assertAbsent("TextView", properties, SdkConstants.ANDROID_URI, "android:focusable", "android:theme");
  }

  private static void assertPresent(String tag, Table<String, String, NlPropertyItem> properties, String namespace, String... names) {
    for (String n : names) {
      assertNotNull("Missing attribute " + n + " for " + tag, properties.get(namespace, n));
    }
  }

  private static void assertAbsent(String tag, Table<String, String, NlPropertyItem> properties, String namespace, String... names) {
    for (String n : names) {
      assertNull("Attribute " + n + " not applicable for a " + tag, properties.get(namespace, n));
    }
  }
}
