/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf

import com.android.tools.idea.wear.dwf.dom.raw.expressions.Function
import com.android.tools.idea.wear.dwf.dom.raw.expressions.PatternedDataSource
import com.android.tools.idea.wear.dwf.dom.raw.expressions.StaticDataSource
import com.android.tools.wear.wff.WFFVersion
import com.android.tools.wear.wff.WFFVersion.WFFVersion1
import com.android.tools.wear.wff.WFFVersion.WFFVersion2
import com.android.tools.wear.wff.WFFVersion.WFFVersion3
import com.android.tools.wear.wff.WFFVersion.WFFVersion4

object WFFConstants {
  const val CONFIGURATION_PREFIX = "CONFIGURATION."
  const val COMPLICATION_PREFIX = "COMPLICATION."

  const val ATTRIBUTE_ID = "id"
  const val ATTRIBUTE_COLORS = "colors"
  const val ATTRIBUTE_SOURCE = "source"

  const val TAG_USER_CONFIGURATIONS = "UserConfigurations"
  const val TAG_COLOR_CONFIGURATION = "ColorConfiguration"
  const val TAG_COLOR_OPTION = "ColorOption"
  const val TAG_LIST_CONFIGURATION = "ListConfiguration"
  const val TAG_BOOLEAN_CONFIGURATION = "BooleanConfiguration"
  const val TAG_PHOTOS_CONFIGURATION = "PhotosConfiguration"
  const val TAG_PHOTOS = "Photos"
  const val TAG_PARAMETER = "Parameter"
  const val TAG_TEMPLATE = "Template"

  /**
   * Attributes that can reference drawable resources.
   *
   * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
   *   Face Format reference</a>
   */
  val DRAWABLE_RESOURCE_ATTRIBUTES = setOf("resource", "icon", "defaultImageResource")

  /**
   * Attributes that can reference colors.
   *
   * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
   *   Face Format reference</a>
   */
  val COLOR_ATTRIBUTES = setOf("color", "backgroundColor", "tintColor")

  /**
   * Data sources that can be used in an expression.
   *
   * @see <a
   *   href="https://developer.android.com/reference/wear-os/wff/common/attributes/source-type">Source
   *   Type</a>
   */
  object DataSources {
    val WEATHER_DAYS_PATTERN = "WEATHER\\.DAYS\\.(\\d+)".toRegex()
    val WEATHER_HOURS_PATTERN = "WEATHER\\.HOURS\\.(\\d+)".toRegex()
    const val DAYS_TOKEN = "<days>"
    const val HOURS_TOKEN = "<hours>"

    val TIME_UNIT =
      listOf(
        StaticDataSource(id = "MILLISECOND", requiredVersion = WFFVersion1),
        StaticDataSource(id = "SECOND_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "SECOND", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MINUTE_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MINUTE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "AMPM_STATE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_F", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_S", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH", requiredVersion = WFFVersion1),
        StaticDataSource(id = "YEAR_S", requiredVersion = WFFVersion1),
        StaticDataSource(id = "YEAR", requiredVersion = WFFVersion1),
      )
    val TIME_SOURCE =
      listOf(
        StaticDataSource(id = "UTC_TIMESTAMP", requiredVersion = WFFVersion1),
        StaticDataSource(id = "SECOND_MILLISECOND", requiredVersion = WFFVersion1),
        StaticDataSource(id = "SECONDS_IN_DAY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MINUTE_SECOND", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_11_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_11_MINUTE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_11", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_12_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_12_MINUTE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_12", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_23_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_23_MINUTE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_0_23", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_24_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_24_MINUTE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HOUR_1_24", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_HOUR", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_0_30_HOUR", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_0_30", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_OF_YEAR", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_OF_WEEK_F", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_OF_WEEK_S", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAY_OF_WEEK", requiredVersion = WFFVersion1),
        StaticDataSource(id = "DAYS_IN_MONTH", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_DAY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_0_11_DAY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MONTH_0_11", requiredVersion = WFFVersion1),
        StaticDataSource(id = "YEAR_MONTH_DAY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "YEAR_MONTH", requiredVersion = WFFVersion1),
        StaticDataSource(id = "WEEK_IN_MONTH", requiredVersion = WFFVersion1),
        StaticDataSource(id = "WEEK_IN_YEAR", requiredVersion = WFFVersion1),
        StaticDataSource(id = "IS_24_HOUR_MODE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "IS_DAYLIGHT_SAVING_TIME", requiredVersion = WFFVersion1),
        StaticDataSource(id = "TIMEZONE_ABB", requiredVersion = WFFVersion1),
        StaticDataSource(id = "TIMEZONE_ID", requiredVersion = WFFVersion1),
        StaticDataSource(id = "TIMEZONE_OFFSET_DST", requiredVersion = WFFVersion1),
        StaticDataSource(id = "TIMEZONE_OFFSET", requiredVersion = WFFVersion1),
        StaticDataSource(id = "TIMEZONE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "AMPM_POSITION", requiredVersion = WFFVersion1),
        StaticDataSource(id = "AMPM_STRING_ENG", requiredVersion = WFFVersion1),
        StaticDataSource(id = "AMPM_STRING_SHORT", requiredVersion = WFFVersion1),
        StaticDataSource(id = "AMPM_STRING", requiredVersion = WFFVersion1),
        StaticDataSource(id = "FIRST_DAY_OF_WEEK", requiredVersion = WFFVersion2),
        StaticDataSource(id = "SECOND_TENS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "SECOND_UNITS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "MINUTE_TENS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "MINUTE_UNITS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "HOUR_TENS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "HOUR_UNITS_DIGIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "SECONDS_SINCE_EPOCH", requiredVersion = WFFVersion3),
        StaticDataSource(id = "MINUTES_SINCE_EPOCH", requiredVersion = WFFVersion3),
        StaticDataSource(id = "HOURS_SINCE_EPOCH", requiredVersion = WFFVersion3),
        StaticDataSource(id = "TIMEZONE_OFFSET_MINUTES", requiredVersion = WFFVersion3),
        StaticDataSource(id = "TIMEZONE_OFFSET_MINUTES_DST", requiredVersion = WFFVersion3),
      )
    val LANGUAGE =
      listOf(
        StaticDataSource(id = "LANGUAGE_CODE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "LANGUAGE_COUNTRY_CODE", requiredVersion = WFFVersion1),
        StaticDataSource(id = "LANGUAGE_LOCALE_NAME", requiredVersion = WFFVersion1),
        StaticDataSource(id = "LANGUAGE_TEXT_DIRECTION", requiredVersion = WFFVersion1),
      )
    val BATTERY =
      listOf(
        StaticDataSource(id = "BATTERY_PERCENT", requiredVersion = WFFVersion1),
        StaticDataSource(id = "BATTERY_CHARGING_STATUS", requiredVersion = WFFVersion1),
        StaticDataSource(id = "BATTERY_IS_LOW", requiredVersion = WFFVersion1),
        StaticDataSource(id = "BATTERY_TEMPERATURE_CELSIUS", requiredVersion = WFFVersion1),
        StaticDataSource(id = "BATTERY_TEMPERATURE_FAHRENHEIT", requiredVersion = WFFVersion1),
      )
    val MOON_PHASE =
      listOf(
        StaticDataSource(id = "MOON_PHASE_POSITION", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MOON_PHASE_TYPE_STRING", requiredVersion = WFFVersion1),
        StaticDataSource(id = "MOON_PHASE_TYPE", requiredVersion = WFFVersion1),
      )
    val SENSOR =
      listOf(
        StaticDataSource(id = "ACCELEROMETER_IS_SUPPORTED", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_X", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_Y", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_ANGLE_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_ANGLE_XY", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_ANGLE_X", requiredVersion = WFFVersion1),
        StaticDataSource(id = "ACCELEROMETER_ANGLE_Y", requiredVersion = WFFVersion1),
      )
    val HEALTH =
      listOf(
        StaticDataSource(id = "STEP_COUNT", requiredVersion = WFFVersion1),
        StaticDataSource(id = "STEP_GOAL", requiredVersion = WFFVersion1),
        StaticDataSource(id = "STEP_PERCENT", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HEART_RATE_Z", requiredVersion = WFFVersion1),
        StaticDataSource(id = "HEART_RATE", requiredVersion = WFFVersion1),
      )

    val NOTIFICATION =
      listOf(StaticDataSource(id = "UNREAD_NOTIFICATION_COUNT", requiredVersion = WFFVersion1))

    val WEATHER =
      listOf(
        StaticDataSource(id = "WEATHER.IS_AVAILABLE", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.IS_ERROR", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.CONDITION", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.CONDITION_NAME", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.IS_DAY", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.TEMPERATURE", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.TEMPERATURE_UNIT", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.TEMPERATURE_LOW", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.TEMPERATURE_HIGH", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.CHANCE_OF_PRECIPITATION", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.UV_INDEX", requiredVersion = WFFVersion2),
        StaticDataSource(id = "WEATHER.LAST_UPDATED", requiredVersion = WFFVersion2),
      )

    val WEATHER_PATTERNS =
      listOf(
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.IS_AVAILABLE".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.IS_AVAILABLE",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.CONDITION".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.CONDITION",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.CONDITION_NAME".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.CONDITION_NAME",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.IS_DAY".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.IS_DAY",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.TEMPERATURE".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.TEMPERATURE",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_HOURS_PATTERN\\.UV_INDEX".toRegex(),
          lookupString = "WEATHER.HOURS.$HOURS_TOKEN.UV_INDEX",
          lookupCursorToken = HOURS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.IS_AVAILABLE".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.IS_AVAILABLE",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CONDITION_DAY".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CONDITION_DAY",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CONDITION_DAY_NAME".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CONDITION_DAY_NAME",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CONDITION_NIGHT".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CONDITION_NIGHT",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CONDITION_NIGHT_NAME".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CONDITION_NIGHT_NAME",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.TEMPERATURE_LOW".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.TEMPERATURE_LOW",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.TEMPERATURE_HIGH".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.TEMPERATURE_HIGH",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CHANCE_OF_PRECIPITATION".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CHANCE_OF_PRECIPITATION",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.CHANCE_OF_PRECIPITATION_NIGHT".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.CHANCE_OF_PRECIPITATION_NIGHT",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
        PatternedDataSource(
          pattern = "$WEATHER_DAYS_PATTERN\\.UV_INDEX".toRegex(),
          lookupString = "WEATHER.DAYS.$DAYS_TOKEN.UV_INDEX",
          lookupCursorToken = DAYS_TOKEN,
          requiredVersion = WFFVersion2,
        ),
      )

    /**
     * @see <a
     *   href="https://developer.android.com/reference/wear-os/wff/complication/complication">Complication</a>
     */
    val COMPLICATION_BY_TYPE =
      mapOf(
        "LONG_TEXT" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(id = "COMPLICATION.SMALL_IMAGE", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.TEXT", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.TITLE", requiredVersion = WFFVersion1),
          ),
        "MONOCHROMATIC_IMAGE" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE_AMBIENT",
              requiredVersion = WFFVersion1,
            ),
          ),
        "PHOTO_IMAGE" to
          setOf(StaticDataSource(id = "COMPLICATION.PHOTO_IMAGE", requiredVersion = WFFVersion1)),
        "RANGED_VALUE" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE_AMBIENT",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(id = "COMPLICATION.TEXT", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.TITLE", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.RANGED_VALUE_MIN", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.RANGED_VALUE_MAX", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.RANGED_VALUE_VALUE", requiredVersion = WFFVersion1),
            StaticDataSource(
              id = "COMPLICATION.RANGED_VALUE_COLORS",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(
              id = "COMPLICATION.RANGED_VALUE_COLORS_INTERPOLATE",
              requiredVersion = WFFVersion2,
            ),
          ),
        "SHORT_TEXT" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(
              id = "COMPLICATION.MONOCHROMATIC_IMAGE_AMBIENT",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(id = "COMPLICATION.TEXT", requiredVersion = WFFVersion1),
            StaticDataSource(id = "COMPLICATION.TITLE", requiredVersion = WFFVersion1),
          ),
        "SMALL_IMAGE" to
          setOf(
            StaticDataSource(id = "COMPLICATION.SMALL_IMAGE", requiredVersion = WFFVersion1),
            StaticDataSource(
              id = "COMPLICATION.SMALL_IMAGE_AMBIENT",
              requiredVersion = WFFVersion1,
            ),
            StaticDataSource(id = "COMPLICATION.IMAGE_STYLE", requiredVersion = WFFVersion1),
          ),
        "GOAL_PROGRESS" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.GOAL_PROGRESS_VALUE",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(
              id = "COMPLICATION.GOAL_PROGRESS_TARGET_VALUE",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(id = "COMPLICATION.TEXT", requiredVersion = WFFVersion2),
            StaticDataSource(id = "COMPLICATION.TITLE", requiredVersion = WFFVersion2),
            StaticDataSource(
              id = "COMPLICATION.GOAL_PROGRESS_COLORS",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(
              id = "COMPLICATION.GOAL_PROGRESS_COLORS_INTERPOLATE",
              requiredVersion = WFFVersion2,
            ),
          ),
        "WEIGHTED_ELEMENTS" to
          setOf(
            StaticDataSource(
              id = "COMPLICATION.WEIGHTED_ELEMENTS_COLORS",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(
              id = "COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(
              id = "COMPLICATION.WEIGHTED_ELEMENTS_BACKGROUND_COLOR",
              requiredVersion = WFFVersion2,
            ),
            StaticDataSource(id = "COMPLICATION.TITLE", requiredVersion = WFFVersion2),
            StaticDataSource(id = "COMPLICATION.TEXT", requiredVersion = WFFVersion2),
          ),
      )

    val COMPLICATION_ALL = COMPLICATION_BY_TYPE.values.flatten().toSet()

    /**
     * Contains all static data sources, such as `[STEP_COUNT]`, does not include patterned data
     * sources, such as `[WEATHER.DAYS.<days>.IS_AVAILABLE]`, and does not contain complication data
     * sources, such as `[COMPLICATION.TEXT]` which depend on the type of complication.
     */
    val ALL_STATIC =
      TIME_UNIT +
        TIME_SOURCE +
        LANGUAGE +
        BATTERY +
        MOON_PHASE +
        SENSOR +
        HEALTH +
        NOTIFICATION +
        WEATHER

    val ALL_STATIC_BY_ID = ALL_STATIC.associateBy { it.id }

    val ALL_AVAILABLE_STATIC_BY_VERSION =
      WFFVersion.entries.associateWith { version ->
        ALL_STATIC.filter { it.requiredVersion <= version }
      }

    val ALL_PATTERNS = WEATHER_PATTERNS

    val ALL_AVAILABLE_PATTERNS_BY_VERSION =
      WFFVersion.entries.associateWith { version ->
        ALL_PATTERNS.filter { it.requiredVersion <= version }
      }
  }

  /**
   * Functions that can be called in an expression.
   *
   * @see <a
   *   href="https://developer.android.com/reference/wear-os/wff/common/attributes/arithmetic-expression#functions">Functions</a>
   */
  object Functions {
    val ALL =
      listOf(
        Function(id = "round", requiredVersion = WFFVersion1),
        Function(id = "floor", requiredVersion = WFFVersion1),
        Function(id = "ceil", requiredVersion = WFFVersion1),
        Function(id = "fract", requiredVersion = WFFVersion1),
        Function(id = "sin", requiredVersion = WFFVersion1),
        Function(id = "cos", requiredVersion = WFFVersion1),
        Function(id = "tan", requiredVersion = WFFVersion1),
        Function(id = "asin", requiredVersion = WFFVersion1),
        Function(id = "acos", requiredVersion = WFFVersion1),
        Function(id = "atan", requiredVersion = WFFVersion1),
        Function(id = "abs", requiredVersion = WFFVersion1),
        Function(id = "clamp", requiredVersion = WFFVersion1),
        Function(id = "rand", requiredVersion = WFFVersion1),
        Function(id = "log2", requiredVersion = WFFVersion1),
        Function(id = "log10", requiredVersion = WFFVersion1),
        Function(id = "log", requiredVersion = WFFVersion1),
        Function(id = "sqrt", requiredVersion = WFFVersion1),
        Function(id = "cbrt", requiredVersion = WFFVersion1),
        Function(id = "expm1", requiredVersion = WFFVersion1),
        Function(id = "exp", requiredVersion = WFFVersion1),
        Function(id = "deg", requiredVersion = WFFVersion1),
        Function(id = "rad", requiredVersion = WFFVersion1),
        Function(id = "pow", requiredVersion = WFFVersion1),
        Function(id = "numberFormat", requiredVersion = WFFVersion1),
        Function(id = "subText", requiredVersion = WFFVersion1),
        Function(id = "textLength", requiredVersion = WFFVersion1),
        Function(id = "icuText", requiredVersion = WFFVersion2),
        Function(id = "icuBestText", requiredVersion = WFFVersion2),
        Function(id = "colorRgb", requiredVersion = WFFVersion4),
        Function(id = "colorArgb", requiredVersion = WFFVersion4),
        Function(id = "extractColorFromColors", requiredVersion = WFFVersion4),
        Function(id = "extractColorFromWeightedColors", requiredVersion = WFFVersion4),
      )

    val ALL_BY_ID = ALL.associateBy { it.id }

    val ALL_AVAILABLE_FUNCTIONS_BY_VERSION =
      WFFVersion.entries.associateWith { version -> ALL.filter { it.requiredVersion <= version } }
  }
}
