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
package com.android.tools.idea.templates;

import com.google.common.collect.Maps;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModelException;
import junit.framework.TestCase;

import java.util.*;

@SuppressWarnings("javadoc")
public class FmHasDependencyMethodTest extends TestCase {

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
  private static void check(boolean expected, String s, Map<String, Object> paramMap) throws TemplateModelException {
    FmHasDependencyMethod method = new FmHasDependencyMethod(paramMap);
    List list = Collections.singletonList(new SimpleScalar(s));
    assertEquals(expected, ((TemplateBooleanModel)method.exec(list)).getAsBoolean());
  }

  public void testEmpty() throws Exception {
    check(false, "com.android.support:support-v4", createMap());
  }

  public void testProvidedDependencies() throws Exception {
    check(false, "com.android.support:support-v4", createMap(
      TemplateMetadata.ATTR_DEPENDENCIES_LIST,
      Arrays.asList("com.android.support:appcompat-v7:21.0.0")));

    check(true, "com.android.support:support-v4", createMap(
      TemplateMetadata.ATTR_DEPENDENCIES_LIST,
      Arrays.asList("com.android.support:appcompat-v7:21.0.0", "com.android.support:support-v4:v21.+")));
  }

  public void testAppCompatBasedOnMinAndCompileApis() throws Exception {
    // Too old: appcompat requires API 8
    check(false, "com.android.support:appcompat-v7", createMap(TemplateMetadata.ATTR_BUILD_API, 21,
                                                               TemplateMetadata.ATTR_MIN_API_LEVEL, 4));
    // Too high: not needed with minSdkVersion 21
    check(false, "com.android.support:appcompat-v7", createMap(TemplateMetadata.ATTR_BUILD_API, 21,
                                                               TemplateMetadata.ATTR_MIN_API_LEVEL, 21));

    // Not needed when minSdkVersion >= 14 and compileSdkVersion < 21
    check(false, "com.android.support:appcompat-v7", createMap(TemplateMetadata.ATTR_BUILD_API, 18,
                                                               TemplateMetadata.ATTR_MIN_API_LEVEL, 15));

    // Use it with minSdkVersion 21
    check(true, "com.android.support:appcompat-v7", createMap(TemplateMetadata.ATTR_BUILD_API, 21,
                                                               TemplateMetadata.ATTR_MIN_API_LEVEL, 15));
  }
}
