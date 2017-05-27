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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.GradleUtil;
import freemarker.template.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Method invoked by FreeMarker to compute the right dependency string to use
 * in the current module. The right string to use depends on the version
 * of Gradle used in the module.
 *
 * <p>Arguments:
 * <ol>
 *   <li>The configuration (if left out, defaults to "compile")
 * </ol>
 * <p>Example usage: {@code espresso=getDependency('androidTestCompile')}, which (for Gradle 3.0) will return "androidTestImplementation"
 */
public class FmGetConfigurationNameMethod implements TemplateMethodModelEx {
  private final Map<String, Object> myParamMap;

  public FmGetConfigurationNameMethod(Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() >= 2) {
      throw new TemplateModelException("Wrong arguments");
    }
    String configuration = args.size() == 1 ? ((TemplateScalarModel)args.get(0)).getAsString() : SdkConstants.GRADLE_COMPILE_CONFIGURATION;
    return new SimpleScalar(convertConfiguration(myParamMap, configuration));
  }

  public static String convertConfiguration(@NotNull Map<String, Object> myParamMap, @NotNull String configuration) {
    String gradlePluginVersion = null;
    if (myParamMap.containsKey(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION)) {
      Object untyped = myParamMap.get(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION);
      if (untyped instanceof String) {
        gradlePluginVersion = (String)untyped;
      }
    }

    return GradleUtil.mapConfigurationName(configuration, gradlePluginVersion, false);
  }
}