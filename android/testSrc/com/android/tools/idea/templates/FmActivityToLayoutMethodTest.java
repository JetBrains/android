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

import com.google.common.collect.Lists;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;

import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class FmActivityToLayoutMethodTest extends TestCase {
  @SuppressWarnings("rawtypes")
  private static void check(String s, String expected) throws TemplateModelException {
    FmActivityToLayoutMethod method = new FmActivityToLayoutMethod();
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  private static void check(String activity, String layoutPrefix, String expected) throws TemplateModelException {
    FmActivityToLayoutMethod method = new FmActivityToLayoutMethod();
    List list = Lists.newArrayList(new SimpleScalar(activity), new SimpleScalar(layoutPrefix));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  public void testBasicConversion() throws Exception {
    check("FooActivity", "activity_foo");
  }

  public void testConvertPartialSuffix() throws Exception {
    check("FooActiv", "activity_foo");
  }

  public void testConvertDoubledSuffix() throws Exception {
    check("FooActivityActivity", "activity_foo_activity");
  }

  public void testConvertNoSuffix() throws Exception {
    check("Foo", "activity_foo");
  }

  public void testConvertSuffixOnly() throws Exception {
    check("Activity", "activity_");
  }

  public void testConvertActivityActivity() throws Exception {
    check("ActivityActivity", "activity_activity");
  }

  public void testConvertActivityActivityWithBaseName() throws Exception {
    check("BaseNameActivityActiv", "activity", "activity_base_name_activity");
  }

  public void testConvertEmpty() throws Exception {
    check("", "");
  }

  public void testConvertLowercaseCharActivityName() throws Exception {
    check("x", "activity_x");
  }

  public void testConvertUppercaseCharActivityName() throws Exception {
    check("X", "activity_x");
  }

  public void testActivityNameSubsetOfTheWordActivity() throws Exception {
    check("Ac", "activity_");
  }

  public void testActivityNameSubsetOfTheWordActivityLowercase() throws Exception {
    check("ac", "activity_ac");
  }

  public void testConvertTrailingDigit() throws Exception {
    check("FooActivity2", "activity_foo2");
  }

  public void testConvertTrailingDigits() throws Exception {
    check("FooActivity200", "activity_foo200");
  }

  public void testConvertTrailingDigitsOnlyActivity() throws Exception {
    check("Activity200", "activity_200");
  }

  public void testCustomPrefixSingleWord() throws Exception {
    check("MainActivity", "simple", "simple_main");
  }

  public void testCustomPrefixMultipleWords() throws Exception {
    check("FullScreenActivity", "content", "content_full_screen");
  }
}
