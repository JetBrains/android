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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.adtui.ptable.StarState;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemComponent;
import com.android.tools.idea.projectsystem.gradle.GradleDependencyVersion;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlProperties.STARRED_PROP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class NlPropertiesTest extends PropertyTestCase {
  private static final String CUSTOM_NAMESPACE = "http://schemas.android.com/apk/res/com.example";
  private static final String[] NO_NAMESPACE_VIEW_ATTRS = {"style"};
  private static final String[] ANDROID_VIEW_ATTRS = {"id", "padding", "visibility", "textAlignment", "translationZ", "elevation"};
  private static final String[] TEXT_VIEW_ATTRS = {"text", "hint", "textColor", "textSize"};

  private static final String[] FRAME_LAYOUT_ATTRS = {"layout_gravity"};
  private static final String[] GRID_LAYOUT_ATTRS = {"layout_rowSpan", "layout_column"};
  private static final String[] LINEAR_LAYOUT_ATTRS = {"layout_weight"};
  private static final String[] RELATIVE_LAYOUT_ATTRS = {"layout_toLeftOf", "layout_above", "layout_alignTop"};

  private void setUpAppCompat() {
    GradleVersion gradleVersion = GradleVersion.parse(String.format("%1$d.0.0", MOST_RECENT_API_LEVEL));
    GradleDependencyVersion version = new GradleDependencyVersion(gradleVersion);
    ProjectSystemComponent projectSystem = mock(ProjectSystemComponent.class);
    AndroidProjectSystem androidProjectSystem = mock(AndroidProjectSystem.class);
    AndroidModuleSystem androidModuleSystem = mock(AndroidModuleSystem.class);
    when(projectSystem.getProjectSystem()).thenReturn(androidProjectSystem);
    when(androidProjectSystem.getModuleSystem(any(Module.class))).thenReturn(androidModuleSystem);
    when(androidModuleSystem.getResolvedVersion(eq(GoogleMavenArtifactId.APP_COMPAT_V7))).thenReturn(version);
    registerProjectComponentImplementation(ProjectSystemComponent.class, projectSystem);
    myFixture.addFileToProject("src/android/support/v7/app/AppCompatImageView.java", APPCOMPAT_ACTIVITY);
    myFixture.addFileToProject("src/android/support/v7/widget/AppCompatImageView.java", APPCOMPAT_IMAGEVIEW);
    myFixture.addFileToProject("src/android/support/v7/widget/AppCompatTextView.java", APPCOMPAT_TEXTVIEW);
    myFixture.addFileToProject("res/values/attrs.xml", APPCOMPAT_ATTRS);
    myFixture.addFileToProject("src/com/example/MyActivity.java", MY_ACTIVITY);
  }

  public void testFontFamilyFromAppCompatForMinApi14() {
    setUpAppCompat();
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myTextView));
    assertPresent(myTextView.getTagName(), properties, AUTO_URI, ATTR_FONT_FAMILY);
    assertAbsent(myTextView.getTagName(), properties, ANDROID_URI, ATTR_FONT_FAMILY);
  }

  public void testFontFamilyFromAndroidForMinApi16() {
    setUpAppCompat();
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myTextView));
    assertAbsent(myTextView.getTagName(), properties, AUTO_URI, ATTR_FONT_FAMILY);
    assertPresent(myTextView.getTagName(), properties, ANDROID_URI, ATTR_FONT_FAMILY);
  }

  public void testViewAttributes() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<View/>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "View";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(MockNlComponent.create(myModel, rootTag)));

    assertTrue(properties.size() > 120); // at least 124 attributes (view + layouts) are available as of API 22

    // check that some of the View's attributes are there..
    assertPresent(tag, properties, ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);

    // check that non-existent properties haven't been added
    assertAbsent(tag, properties, ANDROID_URI, TEXT_VIEW_ATTRS);

    // Views that don't have a parent layout have all the layout attributes available to them..
    assertPresent(tag, properties, ANDROID_URI, RELATIVE_LAYOUT_ATTRS);
    assertPresent(tag, properties, ANDROID_URI, GRID_LAYOUT_ATTRS);
    assertPresent(tag, properties, ANDROID_URI, FRAME_LAYOUT_ATTRS);
  }

  public void testViewInRelativeLayout() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout>" +
                    "  <TextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);
    String tag = "TextView";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    int minApi = AndroidModuleInfo.getInstance(myFacet).getMinSdkVersion().getFeatureLevel();
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(MockNlComponent.create(myModel, subTags[0])));

    // at least 190 attributes are available as of API 22
    assertTrue("Was: " + properties.size() + "  minApi: " + minApi, properties.size() > 180);

    // A text view should have all of its attributes and the parent class's (View) attributes
    assertPresent(tag, properties, ANDROID_URI, TEXT_VIEW_ATTRS);
    assertPresent(tag, properties, ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);

    // Since it is embedded inside a relative layout, it should only have relative layout's layout attributes
    assertPresent(tag, properties, ANDROID_URI, RELATIVE_LAYOUT_ATTRS);
    assertAbsent(tag, properties, ANDROID_URI, GRID_LAYOUT_ATTRS);
    assertAbsent(tag, properties, ANDROID_URI, FRAME_LAYOUT_ATTRS);
  }

  public void testCustomViewAttributes() {
    XmlFile xmlFile = setupCustomViewProject();

    String tag = "com.example.PieChart";

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(MockNlComponent.create(myModel, subTags[0])));
    assertTrue("# of properties lesser than expected: " + properties.size(), properties.size() > 90);

    assertPresent(tag, properties, ANDROID_URI, ANDROID_VIEW_ATTRS);
    assertPresent(tag, properties, "", NO_NAMESPACE_VIEW_ATTRS);
    assertPresent(tag, properties, ANDROID_URI, LINEAR_LAYOUT_ATTRS);
    assertAbsent(tag, properties, ANDROID_URI, TEXT_VIEW_ATTRS);
  }

  public void testPropertyNames() {
    XmlFile xmlFile = setupCustomViewProject();

    XmlTag rootTag = xmlFile.getRootTag();
    assert rootTag != null;

    XmlTag[] subTags = rootTag.getSubTags();
    assertEquals(1, subTags.length);

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(MockNlComponent.create(myModel, subTags[0])));

    NlPropertyItem p = properties.get(ANDROID_URI, "id");
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
                       "<LinearLayout>" +
                       "  <com.example.PieChart />" +
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
    String javaSrc = "package com.example;\n" +
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
    myFixture.addFileToProject("src/com/example/PieChart.java", javaSrc);
    return xmlFile;
  }

  public void testAppCompatIssues() {
    @Language("JAVA")
    String java = "package com.example;\n" +
                  "\n" +
                  "import android.content.Context;\n" +
                  "import android.widget.TextView;\n" +
                  "\n" +
                  "public class MyTextView extends TextView {\n" +
                  "    public MyTextView(Context context) {\n" +
                  "        super(context);\n" +
                  "    }\n" +
                  "}\n";
    myFixture.addFileToProject("src/com/example/MyTextView.java", java);

    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<RelativeLayout>" +
                    "  <com.example.MyTextView />" +
                    "</RelativeLayout>";
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/layout.xml", source);

    @Language("XML")
    String attrsSrc = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<resources>\n" +
                      "    <declare-styleable name=\"MyTextView\">\n" +
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
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(MockNlComponent.create(myModel, subTags[0])));
    assertTrue(properties.size() > 180); // at least 190 attributes are available as of API 22

    // The attrs.xml in appcompat-22.0.0 includes android:focusable, theme and android:theme.
    // The android:focusable refers to the platform attribute, and hence should not be duplicated..
    assertPresent("com.example.MyTextView", properties, ANDROID_URI, "focusable", "theme");
    assertPresent("com.example.MyTextView", properties, CUSTOM_NAMESPACE, "custom");
    assertAbsent("com.example.MyTextView", properties, ANDROID_URI, "android:focusable", "android:theme");
  }

  public void testVisibleIsStarredPropertyByDefault() {
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myTextView));
    List<NlPropertyItem> starred = properties.values().stream()
      .filter(property -> property.getStarState() == StarState.STARRED)
      .collect(Collectors.toList());
    assertThat(starred.size()).isEqualTo(1);
    assertThat(starred.get(0).getName()).isEqualTo(ATTR_VISIBILITY);
    assertThat(NlProperties.getStarredPropertiesAsString()).isEqualTo(ATTR_VISIBILITY);
    assertThat(NlProperties.getStarredProperties()).containsExactly(ATTR_VISIBILITY);
  }

  public void testStarredPropertiesAreReadFromComponentProperties() {
    myPropertiesComponent.setValue(STARRED_PROP, propertyList(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK, ATTR_CARD_ELEVATION));
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myTextView));
    List<String> starred = properties.values().stream()
      .filter(property -> property.getStarState() == StarState.STARRED)
      .map(NlPropertyItem::getName)
      .collect(Collectors.toList());
    assertThat(starred).containsExactly(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK);
    assertThat(NlProperties.getStarredProperties())
      .containsExactly(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK, ATTR_CARD_ELEVATION);
  }

  public void testAddStarredProperty() {
    myPropertiesComponent.setValue(STARRED_PROP, propertyList(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK, ATTR_CARD_ELEVATION));
    NlProperties.saveStarState(null, ATTR_NAME, true, myPropertiesManager);
    List<String> expected = ImmutableList.of(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK, ATTR_CARD_ELEVATION, ATTR_NAME);
    assertThat(myPropertiesComponent.getValue(STARRED_PROP)).isEqualTo(propertyList(expected));
    verify(myUsageTracker).logFavoritesChange(ATTR_NAME, "", expected, myFacet);
  }

  public void testAddStarredToolsProperty() {
    myPropertiesComponent.setValue(STARRED_PROP, propertyList(ATTR_PADDING_BOTTOM, ATTR_ELEVATION));
    NlProperties.saveStarState(TOOLS_URI, ATTR_TEXT, true, myPropertiesManager);
    List<String> expected = ImmutableList.of(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, TOOLS_NS_NAME_PREFIX + ATTR_TEXT);
    assertThat(myPropertiesComponent.getValue(STARRED_PROP)).isEqualTo(propertyList(expected));
    verify(myUsageTracker).logFavoritesChange(TOOLS_NS_NAME_PREFIX + ATTR_TEXT, "", expected, myFacet);
  }

  public void testRemoveStarredProperty() {
    myPropertiesComponent.setValue(STARRED_PROP, propertyList(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK, ATTR_CARD_ELEVATION));
    NlProperties.saveStarState(ANDROID_URI, ATTR_CARD_ELEVATION, false, myPropertiesManager);
    List<String> expected = ImmutableList.of(ATTR_PADDING_BOTTOM, ATTR_ELEVATION, ATTR_ON_CLICK);
    assertThat(myPropertiesComponent.getValue(STARRED_PROP)).isEqualTo(propertyList(expected));
    verify(myUsageTracker).logFavoritesChange("", ATTR_CARD_ELEVATION, expected, myFacet);
  }

  @NotNull
  private static String propertyList(@NotNull String... propertyNames) {
    return Joiner.on(";").join(propertyNames);
  }

  @NotNull
  private static String propertyList(@NotNull List<String> propertyNames) {
    return Joiner.on(";").join(propertyNames);
  }

  public void testSrcCompatIncludedWhenUsingAppCompat() {
    setUpAppCompat();

    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myImageView));

    assertPresent("ImageView", properties, ANDROID_URI, ATTR_SRC);
    assertPresent("ImageView", properties, AUTO_URI, ATTR_SRC_COMPAT);
  }

  public void testSrcNotCompatIncludedWhenNotUsingAppCompat() {
    Table<String, String, NlPropertyItem> properties =
      NlProperties.getInstance().getProperties(myFacet, myPropertiesManager, ImmutableList.of(myImageView));

    assertPresent("ImageView", properties, ANDROID_URI, ATTR_SRC);
    assertAbsent("ImageView", properties, AUTO_URI, ATTR_SRC_COMPAT);
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

  private static final String MY_ACTIVITY =
    "package com.example;\n" +
    "\n" +
    "import android.support.v7.app.AppCompatActivity;\n" +
    "\n" +
    "public class MyActivity extends AppCompatActivity {" +
    "}";

  private static final String APPCOMPAT_ACTIVITY =
    "package android.support.v7.app;\n" +
    "public class AppCompatActivity {" +
    "}";

  @Language("Java")
  private static final String APPCOMPAT_IMAGEVIEW =
    "package android.support.v7.widget;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.util.AttributeSet;\n" +
    "import android.widget.ImageView;\n" +
    "\n" +
    "public class AppCompatImageView extends ImageView {\n" +
    "\n" +
    " public AppCompatImageView(Context context) {\n" +
    "        this(context, null);\n" +
    "    }\n" +
    "\n" +
    "    public AppCompatImageView(Context context, AttributeSet attrs) {\n" +
    "        this(context, attrs, 0);\n" +
    "    }\n" +
    "\n" +
    "    public AppCompatImageView(Context context, AttributeSet attrs, int defStyleAttr) {\n" +
    "        super(context, attrs, defStyleAttr);\n" +
    "    }" +
    "}\n";

  @Language("Java")
  private static final String APPCOMPAT_TEXTVIEW =
    "package android.support.v7.widget;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.util.AttributeSet;\n" +
    "import android.widget.TextView;\n" +
    "\n" +
    "public class AppCompatTextView extends TextView {\n" +
    "\n" +
    " public AppCompatTextView(Context context) {\n" +
    "        this(context, null);\n" +
    "    }\n" +
    "\n" +
    "    public AppCompatTextView(Context context, AttributeSet attrs) {\n" +
    "        this(context, attrs, 0);\n" +
    "    }\n" +
    "\n" +
    "    public AppCompatTextView(Context context, AttributeSet attrs, int defStyleAttr) {\n" +
    "        super(context, attrs, defStyleAttr);\n" +
    "    }" +
    "}\n";

  @Language("XML")
  private static final String APPCOMPAT_ATTRS =
    "<resources>\n" +
    "    <declare-styleable name=\"AppCompatTextView\">\n" +
    "        <!-- Present the text in ALL CAPS. This may use a small-caps form when available. -->\n" +
    "        <attr name=\"textAllCaps\" format=\"reference|boolean\" />\n" +
    "        <attr name=\"android:textAppearance\" />\n" +
    "        <!-- Specify the type of auto-size. Note that this feature is not supported by EditText,\n" +
    "        works only for TextView. -->\n" +
    "        <attr name=\"autoSizeTextType\" format=\"enum\">\n" +
    "            <!-- No auto-sizing (default). -->\n" +
    "            <enum name=\"none\" value=\"0\" />\n" +
    "            <!-- Uniform horizontal and vertical text size scaling to fit within the\n" +
    "            container. -->\n" +
    "            <enum name=\"uniform\" value=\"1\" />\n" +
    "        </attr>\n" +
    "        <!-- Specify the auto-size step size if <code>autoSizeTextType</code> is set to\n" +
    "        <code>uniform</code>. The default is 1px. Overwrites\n" +
    "        <code>autoSizePresetSizes</code> if set. -->\n" +
    "        <attr name=\"autoSizeStepGranularity\" format=\"dimension\" />\n" +
    "        <!-- Resource array of dimensions to be used in conjunction with\n" +
    "        <code>autoSizeTextType</code> set to <code>uniform</code>. Overrides\n" +
    "        <code>autoSizeStepGranularity</code> if set. -->\n" +
    "        <attr name=\"autoSizePresetSizes\" format=\"reference\"/>\n" +
    "        <!-- The minimum text size constraint to be used when auto-sizing text. -->\n" +
    "        <attr name=\"autoSizeMinTextSize\" format=\"dimension\" />\n" +
    "        <!-- The maximum text size constraint to be used when auto-sizing text. -->\n" +
    "        <attr name=\"autoSizeMaxTextSize\" format=\"dimension\" />\n" +
    "        <!-- The attribute for the font family. -->\n" +
    "        <attr name=\"fontFamily\" format=\"string\" />\n" +
    "    </declare-styleable>\n" +
    "\n" +
    "  <declare-styleable name=\"AppCompatImageView\">\n" +
    "        <attr name=\"android:src\"/>\n" +
    "        <!-- Sets a drawable as the content of this ImageView. Allows the use of vector drawable\n" +
    "             when running on older versions of the platform. -->\n" +
    "        <attr name=\"srcCompat\" format=\"reference\" />\n" +
    "\n" +
    "        <!-- Tint to apply to the image source. -->\n" +
    "        <attr name=\"tint\" format=\"color\" />\n" +
    "\n" +
    "        <!-- Blending mode used to apply the image source tint. -->\n" +
    "        <attr name=\"tintMode\">\n" +
    "            <!-- The tint is drawn on top of the drawable.\n" +
    "                 [Sa + (1 - Sa)*Da, Rc = Sc + (1 - Sa)*Dc] -->\n" +
    "            <enum name=\"src_over\" value=\"3\" />\n" +
    "            <!-- The tint is masked by the alpha channel of the drawable. The drawable’s\n" +
    "                 color channels are thrown out. [Sa * Da, Sc * Da] -->\n" +
    "            <enum name=\"src_in\" value=\"5\" />\n" +
    "            <!-- The tint is drawn above the drawable, but with the drawable’s alpha\n" +
    "                 channel masking the result. [Da, Sc * Da + (1 - Sa) * Dc] -->\n" +
    "            <enum name=\"src_atop\" value=\"9\" />\n" +
    "            <!-- Multiplies the color and alpha channels of the drawable with those of\n" +
    "                 the tint. [Sa * Da, Sc * Dc] -->\n" +
    "            <enum name=\"multiply\" value=\"14\" />\n" +
    "            <!-- [Sa + Da - Sa * Da, Sc + Dc - Sc * Dc] -->\n" +
    "            <enum name=\"screen\" value=\"15\" />\n" +
    "            <!-- Combines the tint and icon color and alpha channels, clamping the\n" +
    "                 result to valid color values. Saturate(S + D) -->\n" +
    "            <enum name=\"add\" value=\"16\" />\n" +
    "        </attr>\n" +
    "    </declare-styleable>\n" +
    "</resources>\n";
}
