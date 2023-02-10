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
package org.jetbrains.android.dom.attrs;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test for {@link AttributeDefinitions} and related classes.
 */
public class AttributeDefinitionsTest extends AndroidTestCase {
  private LocalResourceManager myResourceManager;

  @Nullable
  private AttributeDefinition attrDef(@NotNull ResourceReference attr) {
    AttributeDefinition def = getDefs().getAttrDefinition(attr);
    assertNotNull("Missing attribute definition for " + attr.getQualifiedName(), def);
    return def;
  }

  @Nullable
  private StyleableDefinition styleableDef(@NotNull ResourceReference styleable) {
    StyleableDefinition def = getDefs().getStyleableDefinition(styleable);
    assertNotNull("Missing styleable definition for " + styleable.getQualifiedName(), def);
    return def;
  }

  @NotNull
  private AttributeDefinitions getDefs() {
    return myResourceManager.getAttributeDefinitions();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("dom/resources/attrs6.xml", "res/values/attrs.xml");
    myResourceManager = LocalResourceManager.getInstance(myModule);
  }

  public void testAttrGroup() {
    assertEquals("Generic styles", getDefs().getAttrGroup(ResourceReference.attr(ResourceNamespace.ANDROID, "colorForeground")));
    assertEquals("Generic styles", getDefs().getAttrGroup(ResourceReference.attr(ResourceNamespace.ANDROID, "colorForegroundInverse")));
    assertEquals("Color palette", getDefs().getAttrGroup(ResourceReference.attr(ResourceNamespace.ANDROID, "colorPrimary")));
    assertEquals("Other non-theme attributes", getDefs().getAttrGroup(ResourceReference.attr(ResourceNamespace.ANDROID, "textColor")));
    assertEquals("Other non-theme attributes",
                 getDefs().getAttrGroup(ResourceReference.attr(ResourceNamespace.ANDROID, "textColorHighlight")));
  }

  public void testAttrDescription() {
    assertEquals("Color of text (usually same as colorForeground).",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColor")).getDescription(null));
    assertEquals("Color of highlighted text.",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColorHighlight")).getDescription(null));
    assertEquals("The primary branding color for the app. By default, this is the color applied to the\n" +
                 "             action bar background.",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "colorPrimary")).getDescription(null));
  }

  public void testAttrFormat() {
    Set<AttributeFormat> expectedFormats = EnumSet.of(AttributeFormat.COLOR);
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "colorPrimary")).getFormats());

    expectedFormats = EnumSet.of(AttributeFormat.COLOR, AttributeFormat.REFERENCE);
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColor")).getFormats());
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColorHighlight")).getFormats());
  }

  public void testStyleableAttributes() {
    checkStyleableAttributes();
  }

  public void testStyleableAttributesNamespaced() {
    enableNamespacing("p1.p2");
    checkStyleableAttributes();
  }

  private void checkStyleableAttributes() {
    ResourceNamespace appNamespace = StudioResourceRepositoryManager.getInstance(myFacet).getNamespace();
    StyleableDefinition theme = styleableDef(ResourceReference.styleable(appNamespace, "Theme"));
    assertThat(theme).isNotNull();
    assertThat(theme.getAttributes()).contains(attrDef(ResourceReference.attr(appNamespace, "colorForeground")));

    StyleableDefinition view = styleableDef(ResourceReference.styleable(ResourceNamespace.ANDROID, "View"));
    assertThat(view).isNotNull();
    assertThat(view.getAttributes()).contains(attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "background")));

    StyleableDefinition textView = styleableDef(ResourceReference.styleable(ResourceNamespace.ANDROID, "TextView"));
    assertThat(textView).isNotNull();
    assertThat(textView.getAttributes()).contains(attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "bufferType")));
  }

  // TODO: Remove this test when the AttributeDefinitions.getStyleableByName method is removed.
  @Deprecated
  public void testStyleByName() {
    assertThat(getDefs().getStyleableByName("Theme").getResourceReference())
        .isEqualTo(ResourceReference.styleable(ResourceNamespace.RES_AUTO, "Theme"));
    assertThat(getDefs().getStyleableByName("View").getResourceReference())
        .isEqualTo(ResourceReference.styleable(ResourceNamespace.ANDROID, "View"));
  }

  // TODO: Remove this test when the AttributeDefinitions.getAttrDefByName method is removed.
  @Deprecated
  public void testAttrDefByName() {
    assertThat(getDefs().getAttrDefByName("colorForeground").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorForeground"));
    assertThat(getDefs().getAttrDefByName("android:background").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, "background"));
    assertThat(getDefs().getAttrDefByName("background")).isNull();
    AttributeDefinitions frameworkDefs = ModuleResourceManagers.getInstance(myFacet).getFrameworkResourceManager().getAttributeDefinitions();
    assertThat(frameworkDefs.getAttrDefByName("background").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, "background"));
  }
}
