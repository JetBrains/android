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

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("javadoc")
public class FmEscapePropertyValueMethodTest extends TestCase {
  @SuppressWarnings("rawtypes")
  private static void check(String expected, String s) throws TemplateModelException {
    FmEscapePropertyValueMethod method = new FmEscapePropertyValueMethod();
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((SimpleScalar)method.exec(list)).getAsString());
  }

  public void test1() throws Exception {
    check("foo", "foo");
  }

  public void test2() throws Exception {
    check("\\  foo  ", "  foo  ");
  }

  public void test3() throws Exception {
    check("c\\:/foo/bar", "c:/foo/bar");
  }

  public void test4() throws Exception {
    check("\\!\\#\\:\\\\a\\\\b\\\\c", "!#:\\a\\b\\c");
  }

  @SuppressWarnings("SpellCheckingInspection")
  public void test5() throws Exception {
    check("foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo\\#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo",
          "foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo#foofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoofoo");
  }

}
