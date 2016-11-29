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
package com.android.tools.idea.templates;

import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.w3c.dom.Document;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ParameterValueResolverTest {
  private static final String NORMAL_TEMPLATE = "<?xml version=\"1.0\"?>\n" +
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
  private static final String PARAMETER_LOOP = "<?xml version=\"1.0\"?>\n" +
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
                                               "        type=\"string\"\n" +
                                               "        constraints=\"\"\n" +
                                               "        default=\"Hi!\" />\n" +
                                               "\n" +
                                               "    <parameter\n" +
                                               "        id=\"p2\"\n" +
                                               "        name=\"p2 name\"\n" +
                                               "        type=\"string\"\n" +
                                               "        constraints=\"\"\n" +
                                               "        suggest=\"${p1} ${p4}\" />\n" +
                                               "\n" +
                                               "    <parameter\n" +
                                               "        id=\"p3\"\n" +
                                               "        name=\"p3 name\"\n" +
                                               "        type=\"string\"\n" +
                                               "        constraints=\"\"" +
                                               "        suggest=\"${p2}!\"/>\n" +
                                               "\n" +
                                               "    <parameter\n" +
                                               "        id=\"p4\"\n" +
                                               "        name=\"p4 name\"\n" +
                                               "        type=\"string\"\n" +
                                               "        suggest=\"${p3}..\"/>\n" +
                                               "\n" +
                                               "    <execute file=\"recipe.xml.ftl\" />\n" +
                                               "\n" +
                                               "</template>\n";
  private static final String NO_COMPUTED_PARAMS = "<?xml version=\"1.0\"?>\n" +
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
                                                   "        type=\"string\"\n" +
                                                   "        constraints=\"\"\n" +
                                                   "        default=\"p1\" />\n" +
                                                   "\n" +
                                                   "    <parameter\n" +
                                                   "        id=\"p2\"\n" +
                                                   "        name=\"p2 name\"\n" +
                                                   "        type=\"string\"\n" +
                                                   "        constraints=\"\"\n" +
                                                   "        default=\"p2\" />\n" +
                                                   "\n" +
                                                   "    <execute file=\"recipe.xml.ftl\" />\n" +
                                                   "\n" +
                                                   "</template>\n";

  @NotNull
  public static Parameter getParameterObject(@NotNull TemplateMetadata templateMetadata, @NotNull String name) {
    for (Parameter parameter : templateMetadata.getParameters()) {
      if (name.equals(parameter.id)) {
        return parameter;
      }
    }
    throw new IllegalArgumentException(name);
  }

  private static TemplateMetadata parseTemplateMetadata(String templateXml) {
    Document document = XmlUtils.parseDocumentSilently(templateXml, false);
    assert document != null;
    return new TemplateMetadata(document);
  }

  @Test
  public void testSimpleValuesResolution() throws CircularParameterDependencyException {
    TemplateMetadata template = parseTemplateMetadata(NORMAL_TEMPLATE);
    Map<Parameter, Object> defaultValuesMap =
      ParameterValueResolver.resolve(template.getParameters(), ImmutableMap.<Parameter, Object>of(), ImmutableMap.<String, Object>of());
    assertEquals(Boolean.FALSE, defaultValuesMap.get(getParameterObject(template, "p1")));
    assertEquals("Hello", defaultValuesMap.get(getParameterObject(template, "p2")));
    assertEquals("", defaultValuesMap.get(getParameterObject(template, "p3")));
  }

  @Test
  public void testComputedValuesResolution() throws CircularParameterDependencyException {
    TemplateMetadata template = parseTemplateMetadata(NORMAL_TEMPLATE);
    Map<Parameter, Object> defaultValuesMap =
      ParameterValueResolver.resolve(template.getParameters(), ImmutableMap.<Parameter, Object>of(), ImmutableMap.<String, Object>of());
    assertEquals("Hello, World", defaultValuesMap.get(getParameterObject(template, "p4")));
    assertEquals("Hello, World!", defaultValuesMap.get(getParameterObject(template, "p5")));
    assertEquals(Boolean.TRUE, defaultValuesMap.get(getParameterObject(template, "p6")));
  }

  @Test
  public void testComputedValuesDerivedFromNotNull() throws CircularParameterDependencyException {
    TemplateMetadata template = parseTemplateMetadata(NORMAL_TEMPLATE);
    Map<Parameter, Object> values = Maps.newHashMap();
    values.put(getParameterObject(template, "p2"), "Goodbye");

    Map<Parameter, Object> defaultValuesMap =
      ParameterValueResolver.resolve(template.getParameters(), values, ImmutableMap.<String, Object>of());
    assertEquals("Goodbye, World", defaultValuesMap.get(getParameterObject(template, "p4")));
    assertEquals("Goodbye, World!", defaultValuesMap.get(getParameterObject(template, "p5")));
  }

  @Test
  public void testCustomComputedParameterValue() throws CircularParameterDependencyException {
    TemplateMetadata template = parseTemplateMetadata(NORMAL_TEMPLATE);
    Map<Parameter, Object> values = Maps.newHashMap();
    values.put(getParameterObject(template, "p2"), "Goodbye");
    values.put(getParameterObject(template, "p4"), "Value");

    Map<Parameter, Object> parameterValues =
      ParameterValueResolver.resolve(template.getParameters(), values, ImmutableMap.<String, Object>of());
    assertEquals("Value!", parameterValues.get(getParameterObject(template, "p5")));
  }

  @Test
  public void testParameterLoop() {
    TemplateMetadata template = parseTemplateMetadata(PARAMETER_LOOP);

    try {
      ParameterValueResolver.resolve(template.getParameters(), ImmutableMap.<Parameter, Object>of(), ImmutableMap.<String, Object>of());
      fail("No exception was thrown");
    }
    catch (CircularParameterDependencyException e) {
      System.out.printf(e.getMessage());
    }
  }

  @Test
  public void testNoComputedParameters() throws CircularParameterDependencyException {
    TemplateMetadata template = parseTemplateMetadata(NO_COMPUTED_PARAMS);

    Parameter p1 = template.getParameter("p1");
    assert p1 != null;

    String expectedValue = "test";
    Map<Parameter, Object> values = ParameterValueResolver
      .resolve(template.getParameters(), ImmutableMap.<Parameter, Object>of(p1, expectedValue), ImmutableMap.<String, Object>of());
    assertEquals(expectedValue, values.get(p1));
    assertEquals("p2", values.get(template.getParameter("p2")));
  }
}