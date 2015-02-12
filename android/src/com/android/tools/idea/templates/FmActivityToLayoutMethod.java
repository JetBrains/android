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

import java.util.List;

import static com.android.tools.idea.templates.FmUtil.stripSuffix;
import static com.android.tools.idea.wizard.TemplateWizardState.ACTIVITY_NAME_SUFFIX;
import static com.android.tools.idea.wizard.TemplateWizardState.LAYOUT_NAME_PREFIX;

/**
 * Method invoked by FreeMarker to convert an Activity class name into
 * a suitable layout name.
 */
public class FmActivityToLayoutMethod implements TemplateMethodModelEx {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }

    String activityName = args.get(0).toString();

    if (activityName.isEmpty()) {
      return new SimpleScalar("");
    }

    activityName = stripSuffix(activityName, ACTIVITY_NAME_SUFFIX, true /* recursive strip */);

    // Convert CamelCase convention used in activity class names to underlined convention
    // used in layout name:
    String name = LAYOUT_NAME_PREFIX + TemplateUtils.camelCaseToUnderlines(activityName);

    return new SimpleScalar(name);
  }
}