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

import com.android.utils.SdkUtils;
import freemarker.template.*;
import freemarker.template.utility.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.templates.FmUtil.stripSuffix;
import static com.android.tools.idea.wizard.template.TemplateWizardState.ACTIVITY_NAME_SUFFIX;
import static com.android.tools.idea.wizard.template.TemplateWizardState.LAYOUT_NAME_PREFIX;

/**
 * Method invoked by FreeMarker to convert an Activity class name into
 * a suitable layout name.
 */
public class FmActivityToLayoutMethod implements TemplateMethodModelEx {
  @Override
  public TemplateModel exec(List args) throws TemplateModelException {
    if (args.size() < 1 || args.size() > 2) {
      throw new TemplateModelException("Wrong arguments");
    }

    String activityName = ((TemplateScalarModel)args.get(0)).getAsString();
    String layoutNamePrefix = LAYOUT_NAME_PREFIX;
    if (args.size() > 1) {
      layoutNamePrefix = ((TemplateScalarModel)args.get(1)).getAsString() + "_";
    }

    if (activityName.isEmpty()) {
      return new SimpleScalar("");
    }

    activityName = stripActivitySuffix(activityName);

    // Convert CamelCase convention used in activity class names to underlined convention
    // used in layout name:
    String name = TemplateUtils.camelCaseToUnderlines(activityName);
    // We are going to add layoutNamePrefix to the result, so make sure we don't have that string already.
    name = StringUtil.replace(name, layoutNamePrefix, "", false, true);

    return new SimpleScalar(layoutNamePrefix + name);
  }

  private static String stripActivitySuffix(@NotNull String activityName) {
    // Does the name end with Activity<Number> ? If so, we don't want to
    // for example turn "MainActivity2" into "activity_main_activity2"
    int lastCharIndex = activityName.length() - 1;
    if (Character.isDigit(activityName.charAt(lastCharIndex))) {
      for (int i = lastCharIndex - 1; i > 0; i--) {
        if (!Character.isDigit(activityName.charAt(i))) {
          i++;
          if (SdkUtils.endsWith(activityName, i, ACTIVITY_NAME_SUFFIX)) {
            return activityName.substring(0, i - ACTIVITY_NAME_SUFFIX.length()) + activityName.substring(i);
          }
          break;
        }
      }
    }

    return stripSuffix(activityName, ACTIVITY_NAME_SUFFIX, false);
  }
}