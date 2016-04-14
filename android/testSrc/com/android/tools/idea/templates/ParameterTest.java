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
package com.android.tools.idea.templates;

import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.w3c.dom.Element;

import javax.imageio.metadata.IIOMetadataNode;

import static com.android.tools.idea.templates.Parameter.Constraint.*;
import static com.android.tools.idea.templates.Template.*;

/**
 * Tests for parameter checking except for uniqueness/existence. For those, see {@link UniqueParameterTest}
 */
public class ParameterTest extends AndroidTestCase {

  private static final String BASE_PATH = "resourceRepository/";

  @Override
  protected boolean requireRecentSdk() {
    // Need valid layoutlib install
    return true;
  }

  Parameter myParameter;
  Parameter.Constraint myConstraintUnderTest;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TemplateMetadata mockMetadata = Mockito.mock(TemplateMetadata.class);

    Element elem = new IIOMetadataNode();

    elem.setAttribute(ATTR_TYPE, Parameter.Type.STRING.toString());
    elem.setAttribute(ATTR_ID, "testParam");
    elem.setAttribute(ATTR_DEFAULT, "");
    elem.setAttribute(ATTR_SUGGEST, null);
    elem.setAttribute(ATTR_NAME, "Test Param");
    elem.setAttribute(ATTR_HELP, "This is a test parameter");
    elem.setAttribute(ATTR_CONSTRAINTS, "");

    myParameter = new Parameter(mockMetadata, elem);
  }

  private void setConstraint(Parameter.Constraint c) {
    myConstraintUnderTest = c;
    myParameter.constraints.add(c);
  }

  private void assertViolates(@Nullable String packageName, @Nullable String value) {
    assertViolates(packageName, value, myConstraintUnderTest);
  }

  private void assertViolates(@Nullable String packageName, @Nullable String value, Parameter.Constraint c) {
    assertTrue(myParameter.validateStringType(getProject(), myModule, null, packageName, value, null).contains(c));
  }

  private void assertPasses(@Nullable String packageName, @Nullable String value) {
    assertPasses(packageName, value, myConstraintUnderTest);
  }

  private void assertPasses(@Nullable String packageName, @Nullable String value, Parameter.Constraint c) {
    assertFalse(myParameter.validateStringType(getProject(), myModule, null, packageName, value, null).contains(c));
  }


  public void testNonEmpty() throws Exception {
    setConstraint(NONEMPTY);

    // Violates

    // Null and empty values should violate NonEmpty constraint
    assertViolates(null, null);
    assertViolates(null, "");

    // Doesn't Violate

    // Any non-empty value should not violate NonEmpty constraint
    assertPasses(null, "foo");
  }

  public void testActivity() throws Exception {
    setConstraint(ACTIVITY);

    // Package name
    String pn = "com.foo";

    // Invalid package name
    String badPn = "_com-foo%bar^bad";

    // Violates
    assertViolates(pn, "bad-foo%bar^name");
    assertViolates(badPn, "GoodName");


    // Doesn't Violate
    assertPasses(pn, "GoodName");
  }

  public void testClass() throws Exception {
    setConstraint(CLASS);

    // Package name
    String pn = "com.foo";

    // Invalid package name
    String badPn = "_com-foo%bar^bad";

    // Violates
    assertViolates(pn, "bad-foo%bar^name");
    assertViolates(badPn, "GoodName");


    // Doesn't Violate
    assertPasses(pn, "GoodName");
  }

  public void testPackage() throws Exception {
    setConstraint(PACKAGE);

    // Violates
    assertViolates(null, "_com-foo%bar^bad");
    assertViolates(null, ".");
    assertViolates(null, "foo");
    assertViolates(null, "foo.1bar");
    assertViolates(null, "foo.if");
    assertViolates(null, "foo.new");


    // Doesn't Violate
    assertPasses(null, "com.foo.bar");
    assertPasses(null, "foo.bar");
    assertPasses(null, "foo._bar");
    assertPasses(null, "my.p\u00f8");
    assertPasses(null, "foo.f$");
    assertPasses(null, "f_o.ba1r.baz");
    assertPasses(null, "com.example");
  }

  // App package is like package but slightly more strict
  public void testAppPackage() throws Exception {
    setConstraint(APP_PACKAGE);

    // Violates

    assertViolates(null, "if.then");
    assertViolates(null, "foo._bar");
    assertViolates(null, "foo.1bar");
    assertViolates(null, "foo.p\u00f8");
    assertViolates(null, "foo.bar$");

    // Doesn't Violate

    assertPasses(null, "foo.bar");
    assertPasses(null, "foo.b1.ar_");
    assertPasses(null, "Foo.Bar");
  }

  public void testModule() throws Exception {
    myParameter.constraints.add(MODULE);
    setConstraint(UNIQUE);

    // Violates module uniqueness
    assertViolates(null, myModule.getName());

    // Doesn't violate
    assertFalse(myModule.getName().equals("foobar"));
    assertPasses(null, "foobar");
  }

  public void testLayout() throws Exception {
    setConstraint(LAYOUT);

    // Violates

    assertViolates(null, "not-xml-or-png.txt");
    assertViolates(null, "\u00f8foo");
    assertViolates(null, "ACapitalLetter");
    assertViolates(null, "midCapitalLetters");
    assertViolates(null, "hyphens-bad");
    assertViolates(null, "if");
    assertViolates(null, "void");

    // Does not violate

    assertPasses(null, "good_layout");
  }

  public void testDrawable() throws Exception {
    setConstraint(LAYOUT);

    // Violates

    assertViolates(null, "not-xml-or-png.txt");
    assertViolates(null, "\u00f8foo");
    assertViolates(null, "ACapitalLetter");
    assertViolates(null, "midCapitalLetters");
    assertViolates(null, "hyphens-bad");
    assertViolates(null, "if");
    assertViolates(null, "void");

    // Does not violate

    assertPasses(null, "good_drawable");
  }

  public void testUriAuthority() throws Exception {
    setConstraint(URI_AUTHORITY);

    // Violates
    assertViolates(null, "has spaces");
    assertViolates(null, "has/slash");
    assertViolates(null, "has:too:many:colons");
    assertViolates(null, "has.alpha:port");
    assertViolates(null, "8starts.with.a.number");
    assertViolates(null, ";starts.with.semicolon");
    assertViolates(null, "ends.with.semicolon;");

    // Does not violate
    assertPasses(null, "foo");
    assertPasses(null, "fo_o.bar34.com");
    assertPasses(null, "foo:1234");
    assertPasses(null, "foo.bar.com:1234");
    assertPasses(null, "foo:1234;bar.baz:1234");
  }
}
