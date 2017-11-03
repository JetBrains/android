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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public class AttributeDefinitionsImplTest extends AndroidTestCase {
  private AttributeDefinitions myDefs;
  private AttributeDefinitions mySystemDefs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("dom/resources/attrs6.xml", "res/values/attrs.xml");
    myDefs = LocalResourceManager.getInstance(myModule).getAttributeDefinitions();
    mySystemDefs = ModuleResourceManagers.getInstance(myFacet).getSystemResourceManager().getAttributeDefinitions();
    assertNotNull(myDefs);
  }

  public void testAttrGroup() {
    assertEquals("Generic styles", myDefs.getAttrGroupByName("colorForeground"));
    assertEquals("Generic styles", myDefs.getAttrGroupByName("colorForegroundInverse"));
    assertEquals("Color palette", myDefs.getAttrGroupByName("colorPrimary"));
    assertEquals("Other non-theme attributes.", myDefs.getAttrGroupByName("textColor"));
    assertEquals("Other non-theme attributes.", myDefs.getAttrGroupByName("textColorHighlight"));
  }

  private void checkAttrDocForName(String attribute, String expectedDoc) {
    String doc = localAttr(attribute).getDocValue(null);
    assertEquals(expectedDoc, StringUtil.trim(doc));
  }

  public void testAttrDoc() {
    checkAttrDocForName("textColor", "Color of text (usually same as colorForeground).");
    checkAttrDocForName("textColorHighlight", "Color of highlighted text.");
    checkAttrDocForName("colorPrimary", "The primary branding color for the app. By default, this is the color applied to the\n" +
                                        "         action bar background.");
  }

  private void checkAttrFormatForName(String attribute, Set<AttributeFormat> expectedFormat) {
    assertEquals(expectedFormat, localAttr(attribute).getFormats());
  }

  public void testAttrFormat() {
    Set<AttributeFormat> format;
    format = EnumSet.of(AttributeFormat.Color);

    checkAttrFormatForName("colorPrimary", format);

    format = EnumSet.of(AttributeFormat.Color, AttributeFormat.Reference);
    checkAttrFormatForName("textColor", format);
    checkAttrFormatForName("textColorHighlight", format);
  }

  public void testParentStyleableName() {
    assertTrue(localAttr("colorForeground").getParentStyleables().contains("Theme"));
    assertTrue(localAttr("textColor").getParentStyleables().isEmpty());

    assertTrue(systemAttr("background").getParentStyleables().contains("View"));
    assertTrue(systemAttr("bufferType").getParentStyleables().contains("TextView"));
  }

  @NotNull
  private AttributeDefinition localAttr(@NotNull String attrName) {
    return attr(myDefs, attrName);
  }

  @NotNull
  private AttributeDefinition systemAttr(@NotNull String attrName) {
    return attr(mySystemDefs, attrName);
  }

  private static AttributeDefinition attr(@NotNull AttributeDefinitions defs, @NotNull String attrName) {
    AttributeDefinition def = defs.getAttrDefByName(attrName);
    assertNotNull("Missing attribute definition for " + attrName, def);
    return def;
  }
}
