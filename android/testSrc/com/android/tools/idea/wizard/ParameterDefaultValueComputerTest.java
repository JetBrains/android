/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

import java.util.Map;

import static com.android.tools.idea.wizard.ParameterDefaultValueComputer.newDefaultValuesMap;

public final class ParameterDefaultValueComputerTest extends TestCase {
  private static final String METADATA_XML = "<?xml version=\"1.0\"?>\n" +
                                             "<template\n" +
                                             "    format=\"4\"\n" +
                                             "    revision=\"2\"\n" +
                                             "    name=\"Android Manifest File\"\n" +
                                             "    description=\"Creates an Android Manifest XML File.\"\n" +
                                             "    >\n" +
                                             "\n" +
                                             "    <category value=\"Other\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p1\"\n" +
                                             "        name=\"p1 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"false\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p2\"\n" +
                                             "        name=\"p2 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"Hello\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p3\"\n" +
                                             "        name=\"p3 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p4\"\n" +
                                             "        name=\"p4 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p2}, World\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p5\"\n" +
                                             "        name=\"p5 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p4}!\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p6\"\n" +
                                             "        name=\"p6 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        suggest=\"${(p1 = false)?c}\"/>\n" +
                                             "\n" +
                                             "    <execute file=\"recipe.xml.ftl\" />\n" +
                                             "\n" +
                                             "</template>\n";
  private TemplateMetadata myTemplateMetadata;

  public static Parameter getParameterObject(@NotNull TemplateMetadata templateMetadata, @NotNull String name) {
    for (Parameter parameter : templateMetadata.getParameters()) {
      if (name.equals(parameter.id)) {
        return parameter;
      }
    }
    throw new IllegalArgumentException(name);
  }

  public void testSimpleValuesDerival() {
    Map<Parameter, Object> defaultValuesMap = newDefaultValuesMap(myTemplateMetadata.getParameters(),
                                                                  ImmutableMap.<Parameter, Object>of(),
                                                                  ImmutableMap.<String, Object>of(), null);
    assertEquals(Boolean.FALSE, defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p1")));
    assertEquals("Hello", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p2")));
    assertEquals("", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p3")));
  }

  public void testComputedValuesDerival() {
    Map<Parameter, Object> defaultValuesMap = newDefaultValuesMap(myTemplateMetadata.getParameters(),
                                                                  ImmutableMap.<Parameter, Object>of(),
                                                                  ImmutableMap.<String, Object>of(), null);
    assertEquals("Hello, World", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p4")));
    assertEquals("Hello, World!", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p5")));
    assertEquals(Boolean.TRUE, defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p6")));
  }

  public void testComputedValuesDerivedFromNotNull() {
    Map<Parameter, Object> values = Maps.newHashMap();
    Map<Parameter, Object> defaultValuesMap = newDefaultValuesMap(myTemplateMetadata.getParameters(), values,
                                                                  ImmutableMap.<String, Object>of(), null);
    values.put(getParameterObject(myTemplateMetadata, "p2"), "Goodbye");
    assertEquals("Goodbye, World", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p4")));
    assertEquals("Goodbye, World!", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p5")));

    values.put(getParameterObject(myTemplateMetadata, "p4"), "Value");
    assertEquals("Value!", defaultValuesMap.get(getParameterObject(myTemplateMetadata, "p5")));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Document document = XmlUtils.parseDocumentSilently(METADATA_XML, false);
    assert document != null;
    myTemplateMetadata = new TemplateMetadata(document);
  }

}