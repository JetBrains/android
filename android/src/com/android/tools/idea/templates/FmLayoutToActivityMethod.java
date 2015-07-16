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

import static com.android.tools.idea.wizard.template.TemplateWizardState.ACTIVITY_NAME_SUFFIX;
import static com.android.tools.idea.wizard.template.TemplateWizardState.LAYOUT_NAME_PREFIX;

import freemarker.template.*;

import java.util.List;

/**
 * Method invoked by FreeMarker to convert a layout name into an appropriate
 * Activity class.
 */
public class FmLayoutToActivityMethod implements TemplateMethodModelEx {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }

    String name = ((TemplateScalarModel)args.get(0)).getAsString();

    // Strip off the beginning portion of the layout name. The user might be typing
    // the activity name such that only a portion has been entered so far (e.g.
    // "MainActivi") and we want to chop off that portion too such that we don't
    // offer a layout name partially containing the activity suffix (e.g. "main_activi").
    if (name.startsWith(LAYOUT_NAME_PREFIX)) {
      name = name.substring(LAYOUT_NAME_PREFIX.length());
    }

    name = TemplateUtils.underlinesToCamelCase(name);
    String className = TemplateUtils.extractClassName(name);
    if (className == null) {
      className = "Main";
    }
    String activityName = className + ACTIVITY_NAME_SUFFIX;

    return new SimpleScalar(activityName);
  }
}