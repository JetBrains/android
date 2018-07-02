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
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

public class AttributeDefinitionsImplTest extends AndroidTestCase {
  private AttributeDefinitions myDefs;

  private AttributeDefinition attrDef(@NotNull ResourceReference attr) {
    AttributeDefinition def = myDefs.getAttrDefinition(attr);
    assertNotNull("Missing attribute definition for " + attr.getQualifiedName(), def);
    return def;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("dom/resources/attrs6.xml", "res/values/attrs.xml");
    myDefs = LocalResourceManager.getInstance(myModule).getAttributeDefinitions();
    assertNotNull(myDefs);
  }

  public void testAttrGroup() {
    assertEquals("Generic styles", myDefs.getAttrGroup(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorForeground")));
    assertEquals("Generic styles", myDefs.getAttrGroup(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorForegroundInverse")));
    assertEquals("Color palette", myDefs.getAttrGroup(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorPrimary")));
    assertEquals("Other non-theme attributes", myDefs.getAttrGroup(ResourceReference.attr(ResourceNamespace.RES_AUTO, "textColor")));
    assertEquals("Other non-theme attributes",
                 myDefs.getAttrGroup(ResourceReference.attr(ResourceNamespace.RES_AUTO, "textColorHighlight")));
  }

  public void testAttrDoc() {
    assertEquals("Color of text (usually same as colorForeground).",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColor")).getDocValue(null));
    assertEquals("Color of highlighted text.",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColorHighlight")).getDocValue(null));
    assertEquals("The primary branding color for the app. By default, this is the color applied to the\n" +
                 "             action bar background.",
                 attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "colorPrimary")).getDocValue(null));
  }

  public void testAttrFormat() {
    Set<AttributeFormat> expectedFormats = EnumSet.of(AttributeFormat.COLOR);
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "colorPrimary")).getFormats());

    expectedFormats = EnumSet.of(AttributeFormat.COLOR, AttributeFormat.REFERENCE);
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColor")).getFormats());
    assertEquals(expectedFormats, attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "textColorHighlight")).getFormats());
  }

  public void testParentStyleableName() {
    assertThat(attrDef(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorForeground")).getParentStyleables()).contains(
        ResourceReference.styleable(ResourceNamespace.RES_AUTO, "Theme"));
    assertThat(attrDef(ResourceReference.attr(ResourceNamespace.RES_AUTO, "textColor")).getParentStyleables()).isEmpty();

    assertThat(attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "background")).getParentStyleables()).contains(
        ResourceReference.styleable(ResourceNamespace.ANDROID, "View"));
    assertThat(attrDef(ResourceReference.attr(ResourceNamespace.ANDROID, "bufferType")).getParentStyleables()).contains(
        ResourceReference.styleable(ResourceNamespace.ANDROID, "TextView"));
  }

  public void testStyleByName() {
    assertThat(myDefs.getStyleableByName("Theme").getResourceReference())
        .isEqualTo(ResourceReference.styleable(ResourceNamespace.RES_AUTO, "Theme"));
    assertThat(myDefs.getStyleableByName("View").getResourceReference())
        .isEqualTo(ResourceReference.styleable(ResourceNamespace.ANDROID, "View"));
  }

  public void testAttrDefByName() {
    assertThat(myDefs.getAttrDefByName("colorForeground").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.RES_AUTO, "colorForeground"));
    assertThat(myDefs.getAttrDefByName("android:background").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, "background"));
    assertThat(myDefs.getAttrDefByName("background")).isNull();
    AttributeDefinitions frameworkDefs = ModuleResourceManagers.getInstance(myFacet).getSystemResourceManager().getAttributeDefinitions();
    assertThat(frameworkDefs.getAttrDefByName("background").getResourceReference())
        .isEqualTo(ResourceReference.attr(ResourceNamespace.ANDROID, "background"));
  }
}
