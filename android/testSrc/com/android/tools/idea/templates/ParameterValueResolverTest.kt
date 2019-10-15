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
package com.android.tools.idea.templates

import com.android.tools.idea.templates.ParameterValueResolver.Companion.resolve
import com.android.tools.idea.wizard.template.Constraint
import com.android.utils.XmlUtils
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

class ParameterValueResolverTest {
  @Test
  fun testDuplicatedUnique() {
    val template = parseTemplateMetadata(NORMAL_TEMPLATE)
    val deduplicator = object : ParameterValueResolver.Deduplicator {
      override fun deduplicate(parameter: Parameter, value: String?): String? = value + "New"
    }

    // "p2" doesn't have a suggestion field, so it will not be a candidate for de-duplication
    val defaultValuesMap1 = resolve(template.parameters, mapOf(), mapOf(), deduplicator)
    assertEquals("Hello", defaultValuesMap1[getParameterObject(template, "p2")])

    // Making "p2" unique, will now force it to de-duplicate (even if it doesn't have a suggest value)
    getParameterObject(template, "p2").constraints.add(Constraint.UNIQUE)
    val defaultValuesMap2 = resolve(template.parameters, mapOf(), mapOf(), deduplicator)
    assertEquals("HelloNew", defaultValuesMap2[getParameterObject(template, "p2")])
  }

  @Test
  fun testSimpleValuesResolution() {
    val template = parseTemplateMetadata(NORMAL_TEMPLATE)
    val defaultValuesMap = resolve(template.parameters, mapOf(), mapOf())
    assertEquals(java.lang.Boolean.FALSE, defaultValuesMap[getParameterObject(template, "p1")])
    assertEquals("Hello", defaultValuesMap[getParameterObject(template, "p2")])
    assertEquals("", defaultValuesMap[getParameterObject(template, "p3")])
  }

  @Test
  fun testComputedValuesResolution() {
    val template = parseTemplateMetadata(NORMAL_TEMPLATE)
    val defaultValuesMap = resolve(template.parameters, mapOf(), mapOf())
    assertEquals("Hello, World", defaultValuesMap[getParameterObject(template, "p4")])
    assertEquals("Hello, World!", defaultValuesMap[getParameterObject(template, "p5")])
    assertEquals(java.lang.Boolean.TRUE, defaultValuesMap[getParameterObject(template, "p6")])
  }

  @Test
  fun testComputedValuesDerivedFromNotNull() {
    val template = parseTemplateMetadata(NORMAL_TEMPLATE)
    val values = hashMapOf(getParameterObject(template, "p2") to "Goodbye")

    val defaultValuesMap = resolve(template.parameters, values, mapOf())
    assertEquals("Goodbye, World", defaultValuesMap[getParameterObject(template, "p4")])
    assertEquals("Goodbye, World!", defaultValuesMap[getParameterObject(template, "p5")])
  }

  @Test
  fun testCustomComputedParameterValue() {
    val template = parseTemplateMetadata(NORMAL_TEMPLATE)
    val values = hashMapOf(
      getParameterObject(template, "p2") to "Goodbye",
      getParameterObject(template, "p4") to "Value"
    )

    val parameterValues = resolve(template.parameters, values, mapOf())
    assertEquals("Value!", parameterValues[getParameterObject(template, "p5")])
  }

  @Test
  fun testParameterLoop() {
    val template = parseTemplateMetadata(PARAMETER_LOOP)

    try {
      resolve(template.parameters, mapOf(), mapOf())
      fail("No exception was thrown")
    }
    catch (e: CircularParameterDependencyException) {
      print(e.message)
    }
  }

  @Test
  @Throws(CircularParameterDependencyException::class)
  fun testNoComputedParameters() {
    val template = parseTemplateMetadata(NO_COMPUTED_PARAMS)

    val p1 = template.getParameter("p1")!!

    val expectedValue = "test"
    val values = resolve(template.parameters, mapOf(p1 to expectedValue), mapOf())
    assertEquals(expectedValue, values[p1])
    assertEquals("p2", values[template.getParameter("p2") as Parameter])
  }
}

private const val NORMAL_TEMPLATE =
  "<?xml version=\"1.0\"?>\n" +
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
  "        suggest=\"\${p2}, World\"/>\n" +
  "\n" +
  "    <parameter\n" +
  "        id=\"p5\"\n" +
  "        name=\"p5 name\"\n" +
  "        type=\"string\"\n" +
  "        suggest=\"\${p4}!\"/>\n" +
  "\n" +
  "    <parameter\n" +
  "        id=\"p6\"\n" +
  "        name=\"p6 name\"\n" +
  "        type=\"boolean\"\n" +
  "        suggest=\"\${(p1 = false)?c}\"/>\n" +
  "\n" +
  "    <execute file=\"recipe.xml.ftl\" />\n" +
  "\n" +
  "</template>\n"

private const val PARAMETER_LOOP =
  "<?xml version=\"1.0\"?>\n" +
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
  "        suggest=\"\${p1} \${p4}\" />\n" +
  "\n" +
  "    <parameter\n" +
  "        id=\"p3\"\n" +
  "        name=\"p3 name\"\n" +
  "        type=\"string\"\n" +
  "        constraints=\"\"" +
  "        suggest=\"\${p2}!\"/>\n" +
  "\n" +
  "    <parameter\n" +
  "        id=\"p4\"\n" +
  "        name=\"p4 name\"\n" +
  "        type=\"string\"\n" +
  "        suggest=\"\${p3}..\"/>\n" +
  "\n" +
  "    <execute file=\"recipe.xml.ftl\" />\n" +
  "\n" +
  "</template>\n"

private const val NO_COMPUTED_PARAMS =
  "<?xml version=\"1.0\"?>\n" +
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
  "</template>\n"

fun getParameterObject(templateMetadata: TemplateMetadata, name: String): Parameter =
  templateMetadata.parameters.firstOrNull { name == it.id } ?: throw IllegalArgumentException(name)

private fun parseTemplateMetadata(templateXml: String) =
  TemplateMetadata(XmlUtils.parseDocumentSilently(templateXml, false)!!)