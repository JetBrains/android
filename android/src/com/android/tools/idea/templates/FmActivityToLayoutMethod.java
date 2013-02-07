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

import static com.android.tools.idea.wizard.TemplateWizardState.ACTIVITY_NAME_SUFFIX;
import static com.android.tools.idea.wizard.TemplateWizardState.LAYOUT_NAME_PREFIX;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import java.util.List;

/**
 * Method invoked by FreeMarker to convert an Activity class name into
 * a suitable layout name.
 */
public class FmActivityToLayoutMethod implements TemplateMethodModel {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }

    String activityName = args.get(0).toString();

    if (activityName.isEmpty()) {
      return new SimpleScalar("");
    }

    // Strip off the end portion of the activity name. The user might be typing
    // the activity name such that only a portion has been entered so far (e.g.
    // "MainActivi") and we want to chop off that portion too such that we don't
    // offer a layout name partially containing the activity suffix (e.g. "main_activi").
    int suffixStart = activityName.lastIndexOf(ACTIVITY_NAME_SUFFIX.charAt(0));
    if (suffixStart != -1 && activityName.regionMatches(suffixStart, ACTIVITY_NAME_SUFFIX, 0, activityName.length() - suffixStart)) {
      activityName = activityName.substring(0, suffixStart);
    }
    assert !activityName.endsWith(ACTIVITY_NAME_SUFFIX) : activityName;

    // Convert CamelCase convention used in activity class names to underlined convention
    // used in layout name:
    String name = LAYOUT_NAME_PREFIX + TemplateUtils.camelCaseToUnderlines(activityName);

    return new SimpleScalar(name);
  }
}