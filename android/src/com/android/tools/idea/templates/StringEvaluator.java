/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * A Freemarker wrapper which can evaluate simple strings. Used to evaluate
 * parameter constraints during UI wizard value editing.
 * <p>
 * Unlike the more general {@link Template} which is used to instantiate
 * full template files (from resources, merging into existing files etc) this
 * evaluator supports only simple strings, referencing only values from the
 * provided map (and builtin functions).
 */
public class StringEvaluator implements TemplateLoader {
  private Configuration myFreemarker;
  private String myCurrentExpression;

  private static final String BOOLEAN_TEMPLATE = "<#if (%s)>true<#else>false</#if>";

  public StringEvaluator() {
    myFreemarker = new FreemarkerConfiguration();
    myFreemarker.setTemplateLoader(this);
  }

  /** Evaluates the given expression, with the given set of arguments */
  @Nullable
  public String evaluate(@NonNull String expression, @NonNull Map<String, Object> inputs) {
    try {
      myCurrentExpression = expression;
      Template inputsTemplate = myFreemarker.getTemplate(expression);
      StringWriter out = new StringWriter();
      Map<String, Object> args = com.android.tools.idea.templates.Template.createParameterMap(inputs);
      inputsTemplate.process(args, out);
      out.flush();
      return out.toString();
    } catch (Exception e) {
      return null;
    }
  }

  public boolean evaluateBooleanExpression(@NonNull String expression, @NonNull Map<String, Object> inputs, boolean defaultValue) {
    try {
      myCurrentExpression = String.format(BOOLEAN_TEMPLATE, expression);
      String result = evaluate(myCurrentExpression, inputs);
      return Boolean.parseBoolean(result);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  // ---- Implements TemplateLoader ----

  @Override
  public Object findTemplateSource(String name) throws IOException {
    return myCurrentExpression;
  }

  @Override
  public long getLastModified(Object templateSource) {
    return 0;
  }

  @Override
  public Reader getReader(Object templateSource, String encoding) throws IOException {
    return new StringReader(myCurrentExpression);
  }

  @Override
  public void closeTemplateSource(Object templateSource) throws IOException {
  }
}
