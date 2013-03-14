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

package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Value object which holds the current state of the wizard pages for
 * {@link NewTemplateWizard}-derived wizards.
 */
public class TemplateWizardState {
  /** Suffix added by default to activity names */
  public static final String ACTIVITY_NAME_SUFFIX = "Activity";
  /** Prefix added to default layout names */
  public static final String LAYOUT_NAME_PREFIX = "activity_";

  /** Template handler responsible for instantiating templates and reading resources */
  protected Template myTemplate;

  /** Configured parameters, by id */
  protected final Map<String, Object> myParameters = new HashMap<String, Object>();

  /** Ids for parameters which should be hidden (because the client wizard already
   * has information for these parameters) */
  protected final Set<String> myHidden = new HashSet<String>();

  /** Ids for parameters which have been modified directly by the user. */
  protected final Set<String> myModified = new HashSet<String>();

  /**
   * Create a new state object for use by the {@link NewTemplatePage}
   */
  public TemplateWizardState() {
    put(TemplateMetadata.ATTR_IS_NEW_PROJECT, false);
  }

  public boolean hasTemplate() {
    return myTemplate != null && myTemplate.getMetadata() != null;
  }

  @NotNull
  public Template getTemplate() {
    return myTemplate;
  }

  @NotNull
  public TemplateMetadata getTemplateMetadata() {
    return myTemplate.getMetadata();
  }

  @Nullable
  public Object get(String key) {
    return myParameters.get(key);
  }

  public void put(@NotNull String key, @Nullable Object value) {
    myParameters.put(key, value);
  }

  /**
   * Sets the current template
   */
  public void setTemplateLocation(@NotNull File file) {
    if (myTemplate == null || !myTemplate.getRootPath().getAbsolutePath().equals(file.getAbsolutePath())) {
      myTemplate = Template.createFromPath(file);
      setParameterDefaults();
    }
  }

  protected void setParameterDefaults() {
    for (Parameter param : myTemplate.getMetadata().getParameters()) {
      if (!myParameters.containsKey(param.id) && param.initial != null) {
        put(param.id, param.initial);
      }
    }
  }
}
