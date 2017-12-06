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
package com.android.tools.idea.uibuilder.property;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.common.property.inspector.InspectorProvider;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.XmlName;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.cleanUsageTrackerAfterTesting;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockNlUsageTracker;
import static com.google.common.truth.Truth.assertThat;

public abstract class PropertyTestCase extends LayoutTestCase {
  private static final String UNKNOWN_TAG = "UnknownTagName";
  protected static final int MOST_RECENT_API_LEVEL = AndroidVersion.VersionCodes.O_MR1;
  protected static final int DEFAULT_MIN_API_LEVEL = AndroidVersion.VersionCodes.LOLLIPOP_MR1;

  protected NlComponent myTextView;
  protected NlComponent myProgressBar;
  protected NlComponent myCheckBox1;
  protected NlComponent myCheckBox2;
  protected NlComponent myCheckBox3;
  protected NlComponent mySwitch;
  protected NlComponent myUnknown;
  protected NlComponent myMerge;
  protected NlComponent myConstraintLayout;
  protected NlComponent myConstraintLayoutWithConstraintSet;
  protected NlComponent myButton;
  protected NlComponent myImageView;
  protected NlComponent myAutoCompleteTextView;
  protected NlComponent myRadioGroup;
  protected NlComponent myButtonInConstraintLayout;
  protected NlComponent myImageViewInCollapsingToolbarLayout;
  protected NlComponent myTabLayout;
  protected NlComponent myRelativeLayout;
  protected NlComponent myViewTag;
  protected NlComponent myFragment;
  protected SyncNlModel myModel;
  protected DesignSurface myDesignSurface;
  protected NlPropertiesManager myPropertiesManager;
  protected NlUsageTracker myUsageTracker;
  private AndroidDomElementDescriptorProvider myDescriptorProvider;
  private Map<String, NlComponent> myComponentMap;
  protected PropertiesComponent myPropertiesComponent;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setUpManifest();
    myModel = createModel();
    myComponentMap = createComponentMap();
    myTextView = myComponentMap.get("textView");
    myProgressBar = myComponentMap.get("progress");
    myCheckBox1 = myComponentMap.get("checkBox1");
    myCheckBox2 = myComponentMap.get("checkBox2");
    myCheckBox3 = myComponentMap.get("checkBox3");
    mySwitch = myComponentMap.get("switch");
    myImageView = myComponentMap.get("imageView");
    myUnknown = myComponentMap.get("unknown");
    myAutoCompleteTextView = myComponentMap.get("autoCompleteTextView");
    myRadioGroup = myComponentMap.get("group");
    myButton = myComponentMap.get("button");
    myMerge = myComponentMap.get("merge");
    myConstraintLayout = myComponentMap.get("constraintLayout");
    myConstraintLayoutWithConstraintSet = myComponentMap.get("constraintLayoutWithConstraintSet");
    myButtonInConstraintLayout = myComponentMap.get("button2");
    myImageViewInCollapsingToolbarLayout = myComponentMap.get("imgv");
    myTabLayout = myComponentMap.get("tabLayout");
    myRelativeLayout = myComponentMap.get("relativeLayout");
    myViewTag = myComponentMap.get("viewTag");
    myFragment = myComponentMap.get("fragmentTag");
    myDesignSurface = myModel.getSurface();
    myPropertiesManager = new NlPropertiesManager(myFacet, myDesignSurface);
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myPropertiesComponent = new PropertiesComponentMock();
    myUsageTracker = mockNlUsageTracker(myDesignSurface);
    registerApplicationComponent(PropertiesComponent.class, myPropertiesComponent);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      cleanUsageTrackerAfterTesting(myDesignSurface);
      Disposer.dispose(myModel);
      Disposer.dispose(myPropertiesManager);
      Disposer.dispose(myDesignSurface);
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myTextView = null;
      myProgressBar = null;
      myCheckBox1 = null;
      myCheckBox2 = null;
      myCheckBox3 = null;
      mySwitch = null;
      myUnknown = null;
      myMerge = null;
      myConstraintLayout = null;
      myConstraintLayoutWithConstraintSet = null;
      myButton = null;
      myImageView = null;
      myAutoCompleteTextView = null;
      myRadioGroup = null;
      myButtonInConstraintLayout = null;
      myImageViewInCollapsingToolbarLayout = null;
      myTabLayout = null;
      myRelativeLayout = null;
      myModel = null;
      myDesignSurface = null;
      myPropertiesManager = null;
      myUsageTracker = null;
      myDescriptorProvider = null;
      myComponentMap = null;
      myPropertiesComponent = null;
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public boolean providesCustomManifest() {
    return true;
  }

  // By default we setup a manifest file with minSdkVersion and targetSdkVersion specified.
  // The minSdkVersion can be specified by the test name:
  //    testXyzMinApi17   -   will cause a manifest with minSdkVersion set to 17.
  // If no MinApi is specified in the test name the default if LOLLIPOP_MR1.
  // Alternatively a test can override this method to customize the manifest.
  protected void setUpManifest() throws Exception {
    String minApiAsString = StringUtil.substringAfter(getTestName(true), "MinApi");
    int minApi = minApiAsString != null ? Integer.parseInt(minApiAsString) : DEFAULT_MIN_API_LEVEL;
    myFixture.addFileToProject(FN_ANDROID_MANIFEST_XML, String.format(MANIFEST_SOURCE, minApi, MOST_RECENT_API_LEVEL));
  }

  @Language("XML")
  private static final String MANIFEST_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" \n" +
    "    package='com.example'>\n" +
    "        <uses-sdk android:minSdkVersion=\"%1$d\"\n" +
    "                  android:targetSdkVersion=\"%2$d\" />\n" +
    "</manifest>\n";

  @NotNull
  private Map<String, NlComponent> createComponentMap() {
    return addToMap(ImmutableMap.builder(), myModel.getComponents()).build();
  }

  private static ImmutableMap.Builder<String, NlComponent> addToMap(@NotNull ImmutableMap.Builder<String, NlComponent> builder,
                                                                    @NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getId() != null) {
        builder.put(component.getId(), component);
      }
      addToMap(builder, component.getChildren());
    }
    return builder;
  }

  @NotNull
  protected SyncNlModel createModel() {
    ModelBuilder builder = model("merge.xml",
                                 component(VIEW_MERGE)
                                   .withBounds(0, 0, 1000, 1500)
                                   .id("@id/merge")
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .withAttribute(TOOLS_URI, ATTR_CONTEXT, "com.example.MyActivity")
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/textView")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .withAttribute(ANDROID_URI, ATTR_TEXT_COLOR, "#FF00FFFF")
                                       .withAttribute(ANDROID_URI, ATTR_ELEVATION, "2dp")
                                       .text("SomeText"),
                                     component(PROGRESS_BAR)
                                       .withBounds(100, 200, 100, 100)
                                       .id("@id/progress")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 300, 100, 100)
                                       .id("@id/checkBox1")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Wifi"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 400, 100, 100)
                                       .id("@id/checkBox2")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeText"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 500, 100, 100)
                                       .id("@id/checkBox3")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(SWITCH)
                                       .withBounds(100, 600, 100, 100)
                                       .id("@id/switch")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Roaming"),
                                     component(IMAGE_VIEW)
                                       .withBounds(100, 700, 100, 100)
                                       .id("@id/imageView")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(UNKNOWN_TAG)
                                       .withBounds(100, 800, 100, 100)
                                       .id("@id/unknown")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(BUTTON)
                                       .withBounds(400, 900, 100, 100)
                                       .id("@id/button")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeButton"),
                                     component(AUTO_COMPLETE_TEXT_VIEW)
                                       .withBounds(100, 1000, 100, 100)
                                       .id("@id/autoCompleteTextView")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(RADIO_GROUP)
                                       .withBounds(100, 1100, 100, 150)
                                       .id("@id/group")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .children(
                                         component(RADIO_BUTTON)
                                           .withBounds(100, 1150, 100, 50)
                                           .id("@id/radio1")
                                           .width("wrap_content")
                                           .height("wrap_content"),
                                         component((RADIO_BUTTON))
                                           .withBounds(100, 1200, 100, 50)
                                           .id("@+id/radio2")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                       ),
                                     component(CONSTRAINT_LAYOUT.defaultName())
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/constraintLayout")
                                       .width("700dp")
                                       .height("1000dp")
                                       .children(
                                         component(BUTTON)
                                           .withBounds(400, 100, 100, 100)
                                           .id("@id/button2")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                           .text("OtherButton")
                                       ),
                                     component(CONSTRAINT_LAYOUT.defaultName())
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/constraintLayoutWithConstraintSet")
                                       .width("700dp")
                                       .height("1000dp")
                                       .withAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET, "@+id/constraints")
                                       .children(
                                         component(CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.defaultName())
                                           .withBounds(400, 100, 100, 100)
                                           .id("@+id/constraints")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                       ),
                                     component(TAB_LAYOUT.defaultName())
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/tabLayout")
                                       .width("700dp")
                                       .height("1000dp"),
                                     component(RELATIVE_LAYOUT)
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/relativeLayout")
                                       .width("700dp")
                                       .height("1000dp"),
                                     component(VIEW_TAG)
                                       .withBounds(400, 0, 100, 100)
                                       .id("@id/viewTag")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(VIEW_FRAGMENT)
                                       .withBounds(400, 100, 100, 100)
                                       .id("@id/fragmentTag")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(LINEAR_LAYOUT)
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/linearlayout")
                                       .width("700dp")
                                       .height("1000dp")
                                       .children(
                                         component(TEXT_VIEW)
                                           .withBounds(400, 100, 100, 100)
                                           .id("@id/textview_in_linearlayout")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                           .text("TextViewInLinearLayout"),
                                         component(BUTTON)
                                           .withBounds(400, 100, 100, 100)
                                           .id("@id/button_in_linearlayout")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                           .text("ButtonInLinearLayout"),
                                         component(COLLAPSING_TOOLBAR_LAYOUT.defaultName())
                                           .withBounds(400, 300, 100, 200)
                                           .children(
                                             component(IMAGE_VIEW)
                                               .withBounds(410, 310, 50, 100)
                                               .id("@id/imgv")
                                               .withAttribute(AUTO_URI, ATTR_COLLAPSE_PARALLAX_MULTIPLIER, ".2")
                                           ))));
    return builder.build();
  }

  protected void clearSnapshots() {
    clearSnapshots(myModel.getComponents());
  }

  private static void clearSnapshots(@NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      component.setSnapshot(null);
      clearSnapshots(component.getChildren());
    }
  }

  @NotNull
  protected NlPropertyItem createFrom(@NotNull NlComponent component, @NotNull String attributeName) {
    List<NlComponent> components = ImmutableList.of(component);
    XmlAttributeDescriptor descriptor = getDescriptor(component, attributeName);
    assertThat(descriptor).isNotNull();
    AttributeDefinition definition = getDefinition(component, descriptor);

    return NlPropertyItem.create(getXmlName(component, descriptor), definition, components, myPropertiesManager);
  }

  @NotNull
  protected NlPropertyItem createFrom(@NotNull String attributeName, @NotNull NlComponent... componentArray) {
    List<NlComponent> components = Arrays.asList(componentArray);
    XmlAttributeDescriptor descriptor = getDescriptor(components.get(0), attributeName);
    assertThat(descriptor).isNotNull();
    AttributeDefinition definition = getDefinition(components.get(0), descriptor);

    return NlPropertyItem.create(getXmlName(components.get(0), descriptor), definition, components, myPropertiesManager);
  }

  @Nullable
  protected XmlAttributeDescriptor getDescriptor(@NotNull NlComponent component, @NotNull String attributeName) {
    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(component.getTag());
    assertThat(elementDescriptor).isNotNull();
    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(component.getTag());
    for (XmlAttributeDescriptor descriptor : descriptors) {
      if (descriptor.getName().equals(attributeName)) {
        return descriptor;
      }
    }
    return null;
  }

  @Nullable
  private static AttributeDefinition getDefinition(@NotNull NlComponent component, @NotNull XmlAttributeDescriptor descriptor) {
    AndroidFacet facet = component.getModel().getFacet();
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager systemResourceManager = resourceManagers.getSystemResourceManager();
    assertThat(systemResourceManager).isNotNull();

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = systemResourceManager.getAttributeDefinitions();
    XmlName name = getXmlName(component, descriptor);

    AttributeDefinitions attrDefs = NS_RESOURCES.equals(name.getNamespaceKey()) ? systemAttrDefs : localAttrDefs;
    return attrDefs == null ? null : attrDefs.getAttrDefByName(descriptor.getName());
  }

  @NotNull
  private static XmlName getXmlName(@NotNull NlComponent component, @NotNull XmlAttributeDescriptor descriptor) {
    String namespace = null;
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(component.getTag());
    }
    return new XmlName(descriptor.getName(), namespace);
  }

  @NotNull
  protected Table<String, String, NlPropertyItem> getPropertyTable(@NotNull List<NlComponent> components) {
    NlProperties propertiesProvider = NlProperties.getInstance();
    return propertiesProvider.getProperties(myFacet, myPropertiesManager, components);
  }

  @NotNull
  protected Map<String, NlProperty> getPropertyMap(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      return Collections.emptyMap();
    }
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    Map<String, NlProperty> propertiesByName = new HashMap<>();
    for (NlProperty property : properties.row(ANDROID_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row(AUTO_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row("").values()) {
      propertiesByName.put(property.getName(), property);
    }
    // Add access to known design properties
    NlDesignProperties designProperties = new NlDesignProperties();
    for (NlProperty property : designProperties.getKnownProperties(components, myPropertiesManager)) {
      propertiesByName.putIfAbsent(property.getName(), property);
    }
    return propertiesByName;
  }

  @NotNull
  protected NlPropertyItem getProperty(@NotNull NlComponent component, @NotNull String propertyName) {
    Map<String, NlProperty> properties = getPropertyMap(ImmutableList.of(component));
    NlProperty property = properties.get(propertyName);
    assert property != null;
    return (NlPropertyItem)property;
  }

  @NotNull
  protected NlProperty getPropertyWithDefaultValue(@NotNull NlComponent component,
                                                   @NotNull String propertyName,
                                                   @NotNull String resource) {
    NlPropertyItem property = getProperty(component, propertyName);
    property.setDefaultValue(new PropertiesMap.Property(resource, null));
    return property;
  }

  protected boolean isApplicable(@NotNull InspectorProvider<NlPropertiesManager> provider, @NotNull NlComponent... componentArray) {
    return provider.isApplicable(Arrays.asList(componentArray), getPropertyMap(Arrays.asList(componentArray)), myPropertiesManager);
  }
}
