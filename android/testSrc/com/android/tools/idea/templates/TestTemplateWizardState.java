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

import com.android.tools.idea.gradle.npw.project.GradleAndroidProjectPaths;
import com.android.tools.idea.npw.template.TemplateValueInjector;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.npw.NewProjectWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_PACKAGE_NAME;

/**
 * Helper class that tracks the Wizard Template and the Template Values
 */
class TestTemplateWizardState {
  private final Map<String, Object> myTemplateValues = new HashMap<>();
  Template myTemplate;

  public void setParameterDefaults() {
    TemplateMetadata metadata = myTemplate.getMetadata();
    if (metadata == null) {
      Logger.getInstance(TestTemplateWizardState.class).warn("Null metadata");
      return;
    }

    for (Parameter param : metadata.getParameters()) {
      if (!myTemplateValues.containsKey(param.id) && param.initial != null) {
        assert param.id != null;

        switch (param.type) {
          case BOOLEAN:
            put(param.id, Boolean.valueOf(param.initial));
            break;
          case ENUM:
          case STRING:
            put(param.id, param.initial);
            break;
          case SEPARATOR:
          default:
            break;
        }
      }
    }
  }

  @NotNull
  public Template getTemplate() {
    return myTemplate;
  }

  public boolean hasAttr(@NotNull String attr) {
    return myTemplateValues.containsKey(attr);
  }

  public boolean getBoolean(@NotNull String attr) {
    return (boolean)myTemplateValues.get(attr);
  }

  public int getInt(@NotNull String key) {
    return (int)myTemplateValues.get(key);
  }

  @Nullable
  public String getString(@NotNull String key) {
    return (String)myTemplateValues.get(key);
  }

  @Nullable
  public Object get(@NotNull String key) {
    return myTemplateValues.get(key);
  }

  public void setTemplateLocation(@NotNull File file) {
    if (myTemplate == null || !myTemplate.getRootPath().getAbsolutePath().equals(file.getAbsolutePath())) {
      // Clear out any parameters from the old template and bring in the defaults for the new template.
      if (myTemplate != null && myTemplate.getMetadata() != null) {
        for (Parameter param : myTemplate.getMetadata().getParameters()) {
          myTemplateValues.remove(param.id);
        }
      }
      myTemplate = Template.createFromPath(file);
      setParameterDefaults();
    }
  }

  @NotNull
  public Map<String,Object> getParameters() {
    return myTemplateValues;
  }

  public void put(@NotNull String key, @Nullable Object value) {
    myTemplateValues.put(key, value);
  }

  @Nullable
  public TemplateMetadata getTemplateMetadata() {
    return myTemplate == null ? null : myTemplate.getMetadata();
  }

  public void populateDirectoryParameters() {
    File projectRoot = new File(getString(ATTR_PROJECT_LOCATION));
    File moduleRoot = new File(projectRoot, getString(ATTR_MODULE_NAME));
    String packageName = getString(ATTR_PACKAGE_NAME);

    new TemplateValueInjector(myTemplateValues)
      .setModuleRoots(GradleAndroidProjectPaths.createDefaultSourceSetAt(moduleRoot).getPaths(), packageName);
  }
}
