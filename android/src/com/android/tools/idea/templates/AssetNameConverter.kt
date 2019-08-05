/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.google.common.annotations.VisibleForTesting
import java.util.regex.Pattern

/** Suffix added by default to activity names */
private const val ACTIVITY_NAME_SUFFIX = "Activity"
/** Suffix added by default to fragment names */
private const val FRAGMENT_NAME_SUFFIX = "Fragment"
/** Prefix added to default layout names. Can be overridden via [.overrideLayoutPrefix] */
private const val DEFAULT_LAYOUT_NAME_PREFIX = "activity"

/**
 * When stripping the Activity suffix, we match against "Activity" plus zero or more digits.
 * The base of the name will be captured in the first group and the digits will be captured in the second group.
 */
private val ACTIVITY_NAME_PATTERN = Regex("^(.*)$ACTIVITY_NAME_SUFFIX(\\d*)$").toPattern()
/**
 * When stripping the Fragment suffix, we match against "Fragment" plus zero or more digits.
 * The base of the name will be captured in the first group and the digits will be captured in the second group.
 */
private val FRAGMENT_NAME_PATTERN = Regex("^(.*)$FRAGMENT_NAME_SUFFIX(\\d*)$").toPattern()

/** Common Android system endings which we strip from class names */
@VisibleForTesting
val STRIP_CLASS_SUFFIXES = arrayOf(ACTIVITY_NAME_SUFFIX, FRAGMENT_NAME_SUFFIX, "Service", "Provider")

/**
 * Strip the "Activity" or "Fragment" suffix from a class name, e.g. "EditorActivity" -> "Editor",
 * "EditorFragment" -> "Editor".
 * This does not strip recursively, so "EditorActivityActivity" -> "EditorActivity"
 *
 * Because Studio suggests appending numbers onto new classes if they have a duplicate name,
 * e.g. "MainActivity", "MainActivity2", "MainActivity3", we take that into account, for example
 * we would convert "MainActivity3" into "Main3"
 */
private fun stripSuffix(name: String, suffix: String, pattern: Pattern): String {
  val finalName = name.stripSuffix(suffix)
  if (finalName == name) {
    // pattern is expected to be either of [ACTIVITY_NAME_PATTERN] or [FRAGMENT_NAME_PATTERN], both
    // have digits pattern at the end because Studio suggests appending numbers to Activity or Fragment
    // classes if they have a duplicate name. This matcher is to preserve those digits.
    // E.g. "MainActivity3" is converted to "Main3"
    val m = pattern.matcher(name)
    if (m.matches()) {
      val baseName = m.group(1)
      val digits = m.group(2) // May be ""
      return baseName + digits
    }
  }
  return finalName
}

private fun stripActivitySuffix(activityName: String): String = stripSuffix(activityName, ACTIVITY_NAME_SUFFIX, ACTIVITY_NAME_PATTERN)

private fun stripFragmentSuffix(fragmentName: String): String = stripSuffix(fragmentName, FRAGMENT_NAME_SUFFIX, FRAGMENT_NAME_PATTERN)

/**
 * Allows a one to one mapping suggestion between different types of Android asset names, like for example mapping the name of an
 * Activity to its layout. e.g. an Activity with name "ActivityMain" may have a suggested layout name of "activity_main_layout"
 */
class AssetNameConverter(private val myType: Type, private val myName: String) {
  private var myLayoutPrefixOverride: String? = null

  private val layoutPrefixWithTrailingUnderscore: String
    get() = (if (myLayoutPrefixOverride == null) DEFAULT_LAYOUT_NAME_PREFIX else myLayoutPrefixOverride) + "_"

  enum class Type {
    ACTIVITY, LAYOUT, CLASS_NAME, RESOURCE, FRAGMENT
  }

  /**
   * Convert whatever current text type we're representing into the [Type.CLASS_NAME] type,
   * since that can act as a common base type we can use to reliably covert into all other types.
   */
  private fun toClassName(): String = when (myType) {
    Type.ACTIVITY -> stripActivitySuffix(myName)
    Type.LAYOUT -> {
      val layoutPrefix = layoutPrefixWithTrailingUnderscore
      var layoutName = myName
      if (layoutName.startsWith(layoutPrefix)) {
        layoutName = layoutName.substring(layoutPrefix.length)
      }
      TemplateUtils.underlinesToCamelCase(layoutName)
    }
    Type.RESOURCE -> TemplateUtils.underlinesToCamelCase(myName)
    Type.CLASS_NAME -> {
      var className = myName
      // TODO(qumeric): it should not depend on the order
      STRIP_CLASS_SUFFIXES.forEach {
        className = className.stripSuffix(it, recursively=true)
      }
      if (myLayoutPrefixOverride != null) {
        val prefixAsSuffix = TemplateUtils.underlinesToCamelCase(myLayoutPrefixOverride!!)
        className = className.stripSuffix(prefixAsSuffix)
      }
      className
    }
    Type.FRAGMENT -> {
      stripFragmentSuffix(myName)
    }
  }

  /**
   * Override the default layout prefix. This should *not* include its trailing underscore.
   * This will only be used when converting from or to the [Type.LAYOUT] type.
   *
   * Passing in `null` will clear the override, if set.
   */
  fun overrideLayoutPrefix(layoutPrefixOverride: String?): AssetNameConverter {
    myLayoutPrefixOverride = layoutPrefixOverride
    return this
  }

  /**
   * Takes the existing value, and converts it to the requested type.
   */
  fun getValue(type: Type): String {
    if (type == Type.FRAGMENT) {
      overrideLayoutPrefix("fragment")
    }
    val className = this.toClassName()
    return when (type) {
      Type.ACTIVITY -> {
        val activityName = TemplateUtils.extractClassName(className) ?: "Main"
        activityName + ACTIVITY_NAME_SUFFIX
      }
      Type.LAYOUT -> {
        val layoutPrefix = layoutPrefixWithTrailingUnderscore
        val layoutName = TemplateUtils.camelCaseToUnderlines(className)
        // We are going to add layoutNamePrefix to the result, so make sure we don't have that string already.
        layoutPrefix + layoutName.replaceFirst(layoutPrefix, "", false)
      }
      Type.RESOURCE -> TemplateUtils.camelCaseToUnderlines(className)
      Type.CLASS_NAME -> className
      Type.FRAGMENT -> {
        val fragmentName = TemplateUtils.extractClassName(className) ?: "Main"
        fragmentName + FRAGMENT_NAME_SUFFIX
      }
    }
  }
}