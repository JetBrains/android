/*
 * Copyright (C) 2013 The Android Open Source Project
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

import org.mockito.Mockito

import javax.imageio.metadata.IIOMetadataNode

import com.android.tools.idea.templates.Parameter.Constraint.*
import com.android.tools.idea.templates.Template.*
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

/**
 * Tests for parameter checking except for uniqueness/existence. For those, see [UniqueParameterTest]
 */
class ParameterTest: AndroidTestCase() {
  private lateinit var myParameter: Parameter
  private lateinit var myConstraintUnderTest: Parameter.Constraint

  private val mockMetadata = Mockito.mock(TemplateMetadata::class.java)

  private val elem = IIOMetadataNode().apply {
    setAttribute(ATTR_TYPE, Parameter.Type.STRING.toString())
    setAttribute(ATTR_ID, "testParam")
    setAttribute(ATTR_DEFAULT, "")
    setAttribute(ATTR_SUGGEST, null)
    setAttribute(ATTR_NAME, "Test Param")
    setAttribute(ATTR_HELP, "This is a test parameter")
    setAttribute(ATTR_CONSTRAINTS, "")
  }

  override fun setUp() {
    super.setUp()

    myParameter = Parameter(mockMetadata, elem)
  }

  private fun setConstraint(c: Parameter.Constraint) {
    myConstraintUnderTest = c
    myParameter.constraints.add(c)
  }

  private fun assertViolates(packageName: String?, value: String?, c: Parameter.Constraint = myConstraintUnderTest) {
    assertThat(myParameter.validateStringType(project, myModule, null, packageName, value)).contains(c)
  }

  private fun assertPasses(packageName: String?, value: String?, c: Parameter.Constraint = myConstraintUnderTest) {
    assertThat(myParameter.validateStringType(project, myModule, null, packageName, value)).doesNotContain(c)
  }


  fun testNonEmpty() {
    setConstraint(NONEMPTY)

    // Null and empty values should violate NonEmpty constraint
    assertViolates(null, null)
    assertViolates(null, "")

    // Any non-empty value should not violate NonEmpty constraint
    assertPasses(null, "foo")
  }

  fun testActivity() {
    setConstraint(ACTIVITY)
    val packageName = "com.foo"
    val invalidPackageName = "_com-foo%bar^bad"

    assertViolates(packageName, "bad-foo%bar^name")
    assertViolates(invalidPackageName, "GoodName")

    assertPasses(packageName, "GoodName")
  }

  fun testClass() {
    setConstraint(CLASS)
    val packageName = "com.foo"
    val invalidPackageName = "_com-foo%bar^bad"

    assertViolates(packageName, "bad-foo%bar^name")
    assertViolates(invalidPackageName, "GoodName")

    assertPasses(packageName, "GoodName")
  }

  fun testPackage() {
    setConstraint(PACKAGE)

    val violating = listOf("_com-foo%bar^bad", ".", "foo", "foo.1bar", "foo.if", "foo.new")

    violating.forEach {
      assertViolates(null, it)
    }

    val passing = listOf("com.foo.bar", "foo.bar", "foo._bar", "my.p\u00f8", "foo.f$", "f_o.ba1r.baz", "com.example")

    passing.forEach {
      assertPasses(null, it)
    }
  }

  // App package is like package but slightly more strict
  fun testAppPackage() {
    setConstraint(APP_PACKAGE)

    val violating = listOf("if.then", "foo._bar", "foo.1bar", "foo.p\u00f8", "foo.bar$")

    violating.forEach {
      assertViolates(null, it)
    }

    val passing = listOf( "foo.bar", "foo.b1.ar_", "Foo.Bar")

    passing.forEach {
      assertPasses(null, it)
    }
  }

  fun testModule() {
    myParameter.constraints.add(MODULE)
    setConstraint(UNIQUE)

    assertViolates(null, myModule.name)

    assertFalse(myModule.name == "foobar")
    assertPasses(null, "foobar")
  }

  fun testLayout() {
    setConstraint(LAYOUT)

    val violating = listOf("not-xml-or-png.txt", "\u00f8foo", "ACapitalLetter", "midCapitalLetters", "hyphens-bad", "if", "void")

    violating.forEach {
      assertViolates(null, it)
    }

    assertPasses(null, "good_layout")
  }

  fun testDrawable() {
    setConstraint(DRAWABLE)

    val violating = listOf("not-xml-or-png.txt", "\u00f8foo", "ACapitalLetter", " midCapitalLetters ", " hyphens -bad", "if ", " void ")

    violating.forEach {
      assertViolates(null, it)
    }

    assertPasses(null, "good_drawable")
  }

  fun testUriAuthority() {
    setConstraint(URI_AUTHORITY)

    val violating = listOf("has spaces", "has/slash", "has:too:many:colons", "has.alpha:port", "8starts.with.a.number",
                           ";starts.with.semicolon", "ends.with.semicolon;")

    violating.forEach {
      assertViolates(null, it)
    }

    val passing = listOf("foo", "fo_o.bar34.com", "foo:1234", "foo.bar.com:1234", "foo:1234;bar.baz:1234")

    passing.forEach {
      assertPasses(null, it)
    }
  }
}
