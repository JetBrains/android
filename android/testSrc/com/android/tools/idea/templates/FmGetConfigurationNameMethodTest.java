/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.Maps;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateScalarModel;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION;

@SuppressWarnings("javadoc")
public class FmGetConfigurationNameMethodTest extends TestCase {

  private static final Map<String, Object> PLUGIN_232_MAP = createMap(ATTR_GRADLE_PLUGIN_VERSION, "2.3.2");
  private static final Map<String, Object> PLUGIN_300_MAP = createMap(ATTR_GRADLE_PLUGIN_VERSION, "3.0.0-alpha1");

  private static Map<String, Object> createMap(Object... keysAndValues) {
    HashMap<String, Object> map = Maps.newHashMap();
    for (int i = 0; i < keysAndValues.length; i+= 2) {
      String key = (String)keysAndValues[i];
      Object value = keysAndValues[i + 1];
      map.put(key, value);
    }

    return map;
  }

  @SuppressWarnings("rawtypes")
  private static void check(String expected, String s, Map<String, Object> paramMap) throws TemplateModelException {
    FmGetConfigurationNameMethod method = new FmGetConfigurationNameMethod(paramMap);
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((TemplateScalarModel)method.exec(list)).getAsString());
  }

  public void testPlugin232() throws Exception {
    check("compile", "compile", PLUGIN_232_MAP);
    check("testCompile", "testCompile", PLUGIN_232_MAP);
    check("androidTestCompile", "androidTestCompile", PLUGIN_232_MAP);
    check("provided", "provided", PLUGIN_232_MAP);
    check("testProvided", "testProvided", PLUGIN_232_MAP);
  }

  public void testPlugin3() throws Exception {
    check("implementation", "compile", PLUGIN_300_MAP);
    check("testImplementation", "testCompile", PLUGIN_300_MAP);
    check("androidTestImplementation", "androidTestCompile", PLUGIN_300_MAP);
    check("compileOnly", "provided", PLUGIN_300_MAP);
    check("testCompileOnly", "testProvided", PLUGIN_300_MAP);
  }
}
