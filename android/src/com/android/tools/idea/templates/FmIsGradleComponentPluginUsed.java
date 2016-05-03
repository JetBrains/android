/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.intellij.openapi.module.Module;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateModelException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.android.tools.idea.gradle.util.GradleUtil.isUsingExperimentalPlugin;

/**
 * Method invoked by FreeMarker to check whether the Gradle component plugin (AKA experimental plugin) is used for a given module.
 *
 * <p>This method only works when the project is initialized and synced with Gradle. As of now this method is primarily used in the recipes
 * for actions like "New > Folder > AIDL Folder" which are only available with Gradle synced project. This method needs to be updated when
 * it needs to be available in other contexts.</p>
 */
public class FmIsGradleComponentPluginUsed implements TemplateBooleanModel {
  @NotNull private final Map<String, Object> myParamMap;

  public FmIsGradleComponentPluginUsed(@NotNull Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public boolean getAsBoolean() throws TemplateModelException {
    String modulePath = (String)myParamMap.get(TemplateMetadata.ATTR_PROJECT_OUT);
    if (modulePath == null) {
      return false;
    }

    Module module = FmUtil.findModule(modulePath);
    if (module == null) {
      return false;
    }

    return isUsingExperimentalPlugin(module);
  }
}
