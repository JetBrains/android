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

import com.android.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.InvalidParameterException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.templates.FmUtil.stripSuffix;

/**
 * Allows a one to one mapping suggestion between different types of Android asset names, like for example mapping the name of an
 * Activity to its layout. e.g. an Activity with name "ActivityMain" may have a suggested layout name of "activity_main_layout"
 */
public class AssetNameConverter {

  public enum Type {ACTIVITY, LAYOUT, CLASS_NAME, RESOURCE}

  /**
   * Suffix added by default to activity names
   */
  private static final String ACTIVITY_NAME_SUFFIX = "Activity";
  /**
   * Prefix added to default layout names. Can be overridden via {@link #overrideLayoutPrefix(String)}
   */
  private static final String DEFAULT_LAYOUT_NAME_PREFIX = "activity";

  /**
   * When stripping the Activity suffix, we match against "Activity" plus zero or more
   * digits. The base of the name will be captured in the first group and the digits will be
   * captured in the second group.
   */
  private static final Pattern ACTIVITY_NAME_PATTERN = Pattern.compile("^(.*)" + ACTIVITY_NAME_SUFFIX + "(\\d*)$");

  /**
   * Common Android sytem endings which we strip from class names
   */
  @VisibleForTesting
  static final String[] STRIP_CLASS_SUFFIXES = new String[] {
    ACTIVITY_NAME_SUFFIX,
    "Fragment",
    "Service",
    "Provider",
  };

  @NotNull
  private final Type myType;

  @NotNull
  private final String myName;

  @Nullable
  private String myLayoutPrefixOverride;

  @SuppressWarnings("AssignmentToMethodParameter") // Name reused safely for convenience
  public AssetNameConverter(@NotNull Type type, @NotNull String name) {
    myType = type;
    myName = name;
  }

  /**
   * Convert whatever current text type we're representing into the {@link Type#CLASS_NAME} type,
   * since that can act as a common base type we can use to reliably covert into all other types.
   */
  @NotNull
  private String toClassName() {
    String className;
    switch (myType) {
      case ACTIVITY:
        className = stripActivitySuffix(myName);
        break;

      case LAYOUT:
        String layoutPrefix = getLayoutPrefixWithTrailingUnderscore();
        String layoutName = myName;
        if (layoutName.startsWith(layoutPrefix)) {
          layoutName = layoutName.substring(layoutPrefix.length());
        }

        className = TemplateUtils.underlinesToCamelCase(layoutName);
        break;

      case RESOURCE:
        className = TemplateUtils.underlinesToCamelCase(myName);
        break;

      case CLASS_NAME:
        className = myName;
        for (String suffix : STRIP_CLASS_SUFFIXES) {
          className = stripSuffix(className, suffix, true);
        }
        if (myLayoutPrefixOverride != null) {
          String prefixAsSuffix = TemplateUtils.underlinesToCamelCase(myLayoutPrefixOverride);
          className = stripSuffix(className, prefixAsSuffix, false);
        }
        break;

      default:
        throw new InvalidParameterException("Unhandled type: " + myType);
    }

    return className;
  }

  /**
   * Override the default layout prefix. This should <i>not</i> include its trailing underscore.
   * This will only be used when converting from or to the {@link Type#LAYOUT} type.
   *
   * Passing in {@code null} will clear the override, if set.
   */
  @NotNull
  public AssetNameConverter overrideLayoutPrefix(@Nullable String layoutPrefixOverride) {
    myLayoutPrefixOverride = layoutPrefixOverride;
    return this;
  }

  /**
   * Takes the existing value, and converts it to the requested type.
   * @return
   */
  @NotNull
  public String getValue(@NotNull Type type) {
    String className = toClassName();
    switch (type) {
      case ACTIVITY:
        String activityName = TemplateUtils.extractClassName(className);
        if (activityName == null) {
          activityName = "Main";
        }
        return activityName + ACTIVITY_NAME_SUFFIX;

      case LAYOUT:
        // Convert CamelCase convention used in activity class names to underlined convention
        // used in layout names
        String layoutPrefix = getLayoutPrefixWithTrailingUnderscore();
        String layoutName = TemplateUtils.camelCaseToUnderlines(className);
        return layoutPrefix + layoutName;

      case RESOURCE:
        // Convert CamelCase convention used in activity class names to underlined convention
        // used in resource names
        return TemplateUtils.camelCaseToUnderlines(className);

      case CLASS_NAME:
        return className;

      default:
        throw new InvalidParameterException("Unhandled type: " + type);
    }
  }

  /**
   * Strip the "Activity" suffix from a class name, e.g. "EditorActivity" -> "Editor". This does
   * not strip recursively, so "EditorActivityActivity" -> "EditorActivity"
   *
   * Because Studio suggests appending numbers onto new classes if they have a duplicate name,
   * e.g. "MainActivity", "MainActivity2", "MainActivity3", we take that into account, for example
   * we would convert "MainActivity3" into "Main3"
   */
  private static String stripActivitySuffix(@NotNull String activityName) {
    String finalName = stripSuffix(activityName, ACTIVITY_NAME_SUFFIX, false);
    if (finalName.equals(activityName)) {
      // activityName didn't end with "Activity". See if it ended with "Activity###".
      Matcher m = ACTIVITY_NAME_PATTERN.matcher(activityName);
      if (m.matches()) {
        String baseName = m.group(1);
        String digits = m.group(2); // May be ""
        finalName = baseName + digits;
      }
    }
    return finalName;
  }

  @NotNull
  private String getLayoutPrefixWithTrailingUnderscore() {
    return (myLayoutPrefixOverride == null ? DEFAULT_LAYOUT_NAME_PREFIX : myLayoutPrefixOverride) + "_";
  }
}