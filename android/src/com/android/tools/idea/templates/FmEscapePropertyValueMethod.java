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

import freemarker.template.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

/** Escapes a property value (such that its syntax is valid in a Java properties file */
public class FmEscapePropertyValueMethod implements TemplateMethodModelEx {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }

    // Slow, stupid implementation, but is 100% compatible with Java's property file implementation
    Properties properties = new Properties();
    String value = ((TemplateScalarModel)args.get(0)).getAsString();
    properties.setProperty("k", value); // key doesn't matter
    StringWriter writer = new StringWriter();
    String escaped;
    try {
      properties.store(writer, null);
      String s = writer.toString();
      int end = s.length();

      // Writer inserts trailing newline
      String lineSeparator = System.lineSeparator();
      if (s.endsWith(lineSeparator)) {
        end -= lineSeparator.length();
      }

      int start = s.indexOf('=');
      assert start != -1 : s;
      escaped = s.substring(start + 1, end);
    }
    catch (IOException e) {
      escaped = value; // shouldn't happen; we're not going to disk
    }

    return new SimpleScalar(escaped);
  }
}
