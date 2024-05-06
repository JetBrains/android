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
package com.android.tools.idea.common.analytics;

 import static com.android.SdkConstants.AD_VIEW;
 import static com.android.SdkConstants.ANDROID_URI;
 import static com.android.SdkConstants.ATTR_LAYOUT_COLLAPSE_MODE;
 import static com.android.SdkConstants.ATTR_TEXT;
 import static com.android.SdkConstants.AUTO_URI;
 import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
 import static com.android.SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT;
 import static com.android.AndroidXConstants.COORDINATOR_LAYOUT;
 import static com.android.SdkConstants.DESIGN_LIB_ARTIFACT;
 import static com.android.SdkConstants.GRID_LAYOUT_LIB_ARTIFACT;
 import static com.android.SdkConstants.LEANBACK_V17_ARTIFACT;
 import static com.android.SdkConstants.MAPS_ARTIFACT;
 import static com.android.SdkConstants.TEXT_VIEW;
 import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
 import static com.android.SdkConstants.TOOLS_URI;
 import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.CUSTOM_NAME;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.acceptedGoogleLibraryNamespace;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.acceptedGoogleTagNamespace;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.convertAttribute;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.convertAttributeName;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.convertNamespace;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.convertTagName;
 import static com.android.tools.idea.common.analytics.UsageTrackerUtil.lookupAttributeResource;
 import static com.google.common.truth.Truth.assertThat;
 import static com.google.wireless.android.sdk.stats.AndroidAttribute.AttributeNamespace.ANDROID;
 import static com.google.wireless.android.sdk.stats.AndroidAttribute.AttributeNamespace.APPLICATION;
 import static com.google.wireless.android.sdk.stats.AndroidAttribute.AttributeNamespace.TOOLS;
 import static org.mockito.Mockito.mock;
 import static org.mockito.Mockito.when;

 import com.android.ide.common.rendering.api.ResourceNamespace;
 import com.android.ide.common.rendering.api.ResourceReference;
 import com.intellij.openapi.util.text.StringUtil;
 import com.intellij.testFramework.ServiceContainerUtil;
 import java.util.HashMap;
 import java.util.Map;
 import java.util.Set;
 import org.jetbrains.android.AndroidTestCase;
 import com.android.tools.dom.attrs.AttributeDefinition;
 import com.android.tools.dom.attrs.AttributeDefinitions;
 import com.android.tools.dom.attrs.StyleableDefinition;
 import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
 import org.jetbrains.android.resourceManagers.LocalResourceManager;
 import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
 import org.jetbrains.annotations.NotNull;
 import org.jetbrains.annotations.Nullable;

public class UsageTrackerUtilTest extends AndroidTestCase {
  private static final String SDK_VERSION = ":" + HIGHEST_KNOWN_API + ".0.1";
  private static final String DESIGN_COORDINATE = DESIGN_LIB_ARTIFACT + SDK_VERSION;
  private static final String GRID_LAYOUT_COORDINATE = GRID_LAYOUT_LIB_ARTIFACT + SDK_VERSION;
  private static final String CONSTRAINT_LAYOUT_COORDINATE = CONSTRAINT_LAYOUT_LIB_ARTIFACT + ":1.0.2";
  private static final String MAPS_COORDINATE = MAPS_ARTIFACT + ":2.0.0";
  private static final String LEANBACK_V17_COORDINATE = LEANBACK_V17_ARTIFACT + ":7.0.0";
  private static final String ACME_LIB_COORDINATE = "com.acme:my-layout:1.0.0";
  private static final String ATTR_ACME_LAYOUT_MARGIN = "layout_my_custom_right_margin";

  public void testConvertAttributeByName() {
    setUpApplicationAttributes();

    assertThat(convertAttribute(ATTR_TEXT, myFacet).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(ATTR_TEXT, myFacet).getAttributeNamespace()).isEqualTo(ANDROID);

    assertThat(convertAttribute(ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeName()).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeNamespace()).isEqualTo(APPLICATION);

    assertThat(convertAttribute(ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeNamespace()).isEqualTo(APPLICATION);
  }

  public void testConvertToolsAttributeByName() {
    setUpApplicationAttributes();

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_TEXT, myFacet).getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_TEXT, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeName())
      .isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_LAYOUT_COLLAPSE_MODE, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);

    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttribute(TOOLS_NS_NAME_PREFIX + ATTR_ACME_LAYOUT_MARGIN, myFacet).getAttributeNamespace()).isEqualTo(TOOLS);
  }

  public void testConvertNamespace() {
    assertThat(convertNamespace(null)).isEqualTo(ANDROID);
    assertThat(convertNamespace("")).isEqualTo(ANDROID);
    assertThat(convertNamespace(TOOLS_URI)).isEqualTo(TOOLS);
    assertThat(convertNamespace(ANDROID_URI)).isEqualTo(ANDROID);
    assertThat(convertNamespace(AUTO_URI)).isEqualTo(APPLICATION);
    assertThat(convertNamespace("unknown")).isEqualTo(APPLICATION);
  }

  public void testConvertAttributeName() {
    setUpApplicationAttributes();

    assertThat(convertAttributeName(ATTR_TEXT, ANDROID, null, myFacet)).isEqualTo(ATTR_TEXT);

    assertThat(convertAttributeName(ATTR_LAYOUT_COLLAPSE_MODE, APPLICATION, DESIGN_COORDINATE, myFacet))
      .isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttributeName(ATTR_TEXT, APPLICATION, null, myFacet)).isEqualTo(CUSTOM_NAME);
    assertThat(convertAttributeName(ATTR_ACME_LAYOUT_MARGIN, APPLICATION, ACME_LIB_COORDINATE, myFacet)).isEqualTo(CUSTOM_NAME);

    assertThat(convertAttributeName(ATTR_TEXT, TOOLS, null, myFacet)).isEqualTo(ATTR_TEXT);
    assertThat(convertAttributeName(ATTR_LAYOUT_COLLAPSE_MODE, TOOLS, null, myFacet)).isEqualTo(ATTR_LAYOUT_COLLAPSE_MODE);
    assertThat(convertAttributeName(ATTR_ACME_LAYOUT_MARGIN, TOOLS, null, myFacet)).isEqualTo(CUSTOM_NAME);
  }

  public void testConvertTagName() {
    assertThat(convertTagName(TEXT_VIEW).getTagName()).isEqualTo(TEXT_VIEW);
    assertThat(convertTagName(COORDINATOR_LAYOUT.defaultName()).getTagName()).isEqualTo(StringUtil.getShortName(COORDINATOR_LAYOUT.defaultName()));
    assertThat(convertTagName(CONSTRAINT_LAYOUT.defaultName()).getTagName()).isEqualTo(StringUtil.getShortName(CONSTRAINT_LAYOUT.defaultName()));
    assertThat(convertTagName(AD_VIEW).getTagName()).isEqualTo(StringUtil.getShortName(AD_VIEW));
    assertThat(convertTagName("com.acme.MyClass").getTagName()).isEqualTo(CUSTOM_NAME);
  }

  public void testAcceptedGoogleLibraryNamespace() {
    assertThat(acceptedGoogleLibraryNamespace(DESIGN_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(CONSTRAINT_LAYOUT_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(GRID_LAYOUT_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(MAPS_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(LEANBACK_V17_COORDINATE)).isTrue();
    assertThat(acceptedGoogleLibraryNamespace(ACME_LIB_COORDINATE)).isFalse();

    assertThat(acceptedGoogleLibraryNamespace("constraint-layout-1.0.0-beta3")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("constraint-layout-2.0.0")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("design-25.0.1")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("cardview-v7-25.0.1")).isTrue();
    assertThat(acceptedGoogleLibraryNamespace("acme-layout")).isFalse();
  }

  public void testAcceptedGoogleTagNamespace() {
    assertThat(acceptedGoogleTagNamespace(TEXT_VIEW)).isTrue();
    assertThat(acceptedGoogleTagNamespace(COORDINATOR_LAYOUT.defaultName())).isTrue();
    assertThat(acceptedGoogleTagNamespace(CONSTRAINT_LAYOUT.defaultName())).isTrue();
    assertThat(acceptedGoogleTagNamespace(AD_VIEW)).isTrue();
    assertThat(acceptedGoogleTagNamespace("com.acme.MyClass")).isFalse();
  }

  public void testLookupAttributeResource() {
    setUpApplicationAttributes();

    assertThat(lookupAttributeResource(myFacet, ATTR_TEXT).getNamespace()).isEqualTo(ANDROID);
    assertThat(lookupAttributeResource(myFacet, ATTR_TEXT).getLibraryName()).isNull();

    assertThat(lookupAttributeResource(myFacet, ATTR_LAYOUT_COLLAPSE_MODE).getNamespace()).isEqualTo(APPLICATION);
    assertThat(lookupAttributeResource(myFacet, ATTR_LAYOUT_COLLAPSE_MODE).getLibraryName()).isEqualTo(DESIGN_COORDINATE);

    assertThat(lookupAttributeResource(myFacet, ATTR_ACME_LAYOUT_MARGIN).getNamespace()).isEqualTo(APPLICATION);
    assertThat(lookupAttributeResource(myFacet, ATTR_ACME_LAYOUT_MARGIN).getLibraryName()).isEqualTo(ACME_LIB_COORDINATE);
  }

  private void setUpApplicationAttributes() {
    Attributes frameworkAttributes = new Attributes();
    frameworkAttributes.add(new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_TEXT));

    Attributes localAttributes = new Attributes();
    localAttributes.add(new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_LAYOUT_COLLAPSE_MODE, DESIGN_COORDINATE, null));
    localAttributes.add(new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_ACME_LAYOUT_MARGIN, ACME_LIB_COORDINATE, null));

    FrameworkResourceManager frameworkResourceManager = mock(FrameworkResourceManager.class);
    when(frameworkResourceManager.getAttributeDefinitions()).thenReturn(frameworkAttributes);

    LocalResourceManager localResourceManager = mock(LocalResourceManager.class);
    when(localResourceManager.getAttributeDefinitions()).thenReturn(localAttributes);

    ModuleResourceManagers resourceManagers = mock(ModuleResourceManagers.class);
    when(resourceManagers.getFrameworkResourceManager()).thenReturn(frameworkResourceManager);
    when(resourceManagers.getLocalResourceManager()).thenReturn(localResourceManager);

    ServiceContainerUtil.replaceService(myModule, ModuleResourceManagers.class, resourceManagers, getTestRootDisposable());
  }

  private static class Attributes implements AttributeDefinitions {
    private Map<ResourceReference, AttributeDefinition> myDefinitions = new HashMap<>();

    private void add(@NotNull AttributeDefinition definition) {
      myDefinitions.put(definition.getResourceReference(), definition);
    }

    @Nullable
    @Override
    public StyleableDefinition getStyleableDefinition(@NotNull ResourceReference styleable) {
      throw new UnsupportedOperationException();
    }

    @Deprecated
    @Nullable
    @Override
    public StyleableDefinition getStyleableByName(@NotNull String name) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Set<ResourceReference> getAttrs() {
      return myDefinitions.keySet();
    }

    @Nullable
    @Override
    public AttributeDefinition getAttrDefinition(@NotNull ResourceReference attr) {
      return myDefinitions.get(attr);
    }

    @Deprecated
    @Nullable
    @Override
    public AttributeDefinition getAttrDefByName(@NotNull String name) {
      AttributeDefinition attribute = myDefinitions.get(ResourceReference.attr(ResourceNamespace.RES_AUTO, name));
      if (attribute == null) {
        attribute = myDefinitions.get(ResourceReference.attr(ResourceNamespace.ANDROID, name));
      }
      return attribute;
    }

    @Nullable
    @Override
    public String getAttrGroup(@NotNull ResourceReference attr) {
      throw new UnsupportedOperationException();
    }
  }
}
