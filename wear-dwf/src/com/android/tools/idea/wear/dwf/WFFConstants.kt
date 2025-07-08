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

object WFFConstants {
  const val CONFIGURATION_PREFIX = "CONFIGURATION."

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

  /**
   * Attributes that can reference drawable resources.
   *
   * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
   *   Face Format reference</a>
   */
  val DRAWABLE_RESOURCE_ATTRIBUTES = setOf("resource", "icon")

  /**
   * Attributes that can reference colors.
   *
   * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
   *   Face Format reference</a>
   */
  val COLOR_ATTRIBUTES = setOf("color", "backgroundColor", "tintColor")

  object DataSources {
    val TIME_UNIT =
      setOf(
        "MILLISECOND",
        "SECOND_Z",
        "SECOND",
        "MINUTE_Z",
        "MINUTE",
        "AMPM_STATE",
        "DAY_Z",
        "DAY",
        "MONTH_Z",
        "MONTH_F",
        "MONTH_S",
        "MONTH",
        "YEAR_S",
        "YEAR",
      )
    val TIME_SOURCE =
      setOf(
        "UTC_TIMESTAMP",
        "SECOND_MILLISECOND",
        "SECONDS_IN_DAY",
        "SECONDS_SINCE_EPOCH",
        "MINUTE_SECOND",
        "MINUTES_SINCE_EPOCH",
        "HOUR_0_11_Z",
        "HOUR_0_11_MINUTE",
        "HOUR_0_11",
        "HOUR_1_12_Z",
        "HOUR_1_12_MINUTE",
        "HOUR_1_12",
        "HOUR_0_23_Z",
        "HOUR_0_23_MINUTE",
        "HOUR_0_23",
        "HOUR_1_24_Z",
        "HOUR_1_24_MINUTE",
        "HOUR_1_24",
        "HOURS_SINCE_EPOCH",
        "DAY_HOUR",
        "DAY_0_30_HOUR",
        "DAY_0_30",
        "DAY_OF_YEAR",
        "DAY_OF_WEEK_F",
        "DAY_OF_WEEK_S",
        "DAY_OF_WEEK",
        "DAYS_IN_MONTH",
        "MONTH_DAY",
        "MONTH_0_11_DAY",
        "MONTH_0_11",
        "YEAR_MONTH_DAY",
        "YEAR_MONTH",
        "WEEK_IN_MONTH",
        "WEEK_IN_YEAR",
        "FIRST_DAY_OF_WEEK",
        "IS_24_HOUR_MODE",
        "IS_DAYLIGHT_SAVING_TIME",
        "TIMEZONE_ABB",
        "TIMEZONE_ID",
        "TIMEZONE_OFFSET_DST",
        "TIMEZONE_OFFSET",
        "TIMEZONE_OFFSET_MINUTES",
        "TIMEZONE_OFFSET_MINUTES_DST",
        "TIMEZONE",
        "AMPM_POSITION",
        "AMPM_STRING_ENG",
        "AMPM_STRING_SHORT",
        "AMPM_STRING",
      )
    val LANGUAGE =
      setOf(
        "LANGUAGE_CODE",
        "LANGUAGE_COUNTRY_CODE",
        "LANGUAGE_LOCALE_NAME",
        "LANGUAGE_TEXT_DIRECTION",
      )
    val BATTERY =
      setOf(
        "BATTERY_PERCENT",
        "BATTERY_CHARGING_STATUS",
        "BATTERY_IS_LOW",
        "BATTERY_TEMPERATURE_CELSIUS",
        "BATTERY_TEMPERATURE_FAHRENHEIT",
      )
    val MOON_PHASE = setOf("MOON_PHASE_POSITION", "MOON_PHASE_TYPE_STRING", "MOON_PHASE_TYPE")
    val SENSOR =
      setOf(
        "ACCELEROMETER_IS_SUPPORTED",
        "ACCELEROMETER_X",
        "ACCELEROMETER_Y",
        "ACCELEROMETER_Z",
        "ACCELEROMETER_ANGLE_Z",
        "ACCELEROMETER_ANGLE_XY",
        "ACCELEROMETER_ANGLE_X",
        "ACCELEROMETER_ANGLE_Y",
      )
    val HEALTH = setOf("STEP_COUNT", "STEP_GOAL", "STEP_PERCENT", "HEART_RATE_Z", "HEART_RATE")

    val NOTIFICATION = setOf("UNREAD_NOTIFICATION_COUNT")

    val WEATHER =
      setOf(
        "WEATHER.IS_AVAILABLE",
        "WEATHER.IS_ERROR",
        "WEATHER.CONDITION",
        "WEATHER.CONDITION_NAME",
        "WEATHER.IS_DAY",
        "WEATHER.TEMPERATURE",
        "WEATHER.TEMPERATURE_UNIT",
        "WEATHER.TEMPERATURE_LOW",
        "WEATHER.TEMPERATURE_HIGH",
        "WEATHER.CHANCE_OF_PRECIPITATION",
        "WEATHER.UV_INDEX",
        "WEATHER.LAST_UPDATED",
      )

    val WEATHER_PATTERNS =
      setOf(
        "WEATHER\\.HOURS\\.\\d+\\.IS_AVAILABLE".toRegex(),
        "WEATHER\\.HOURS\\.\\d+\\.CONDITION".toRegex(),
        "WEATHER\\.HOURS\\.\\d+\\.CONDITION_NAME".toRegex(),
        "WEATHER\\.HOURS\\.\\d+\\.IS_DAY".toRegex(),
        "WEATHER\\.HOURS\\.\\d+\\.TEMPERATURE".toRegex(),
        "WEATHER\\.HOURS\\.\\d+\\.UV_INDEX".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.IS_AVAILABLE".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CONDITION_DAY".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CONDITION_DAY_NAME".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CONDITION_NIGHT".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CONDITION_NIGHT_NAME".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.TEMPERATURE_LOW".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.TEMPERATURE_HIGH".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CHANCE_OF_PRECIPITATION".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.CHANCE_OF_PRECIPITATION_NIGHT".toRegex(),
        "WEATHER\\.DAYS\\.\\d+\\.UV_INDEX".toRegex(),
      )

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

    val ALL_PATTERNS = WEATHER_PATTERNS
  }

  object Functions {
    val ALL =
      setOf(
        "round",
        "floor",
        "ceil",
        "fract",
        "sin",
        "cos",
        "tan",
        "asin",
        "acos",
        "atan",
        "abs",
        "clamp",
        "rand",
        "log2",
        "log10",
        "log",
        "sqrt",
        "cbrt",
        "expm1",
        "exp",
        "deg",
        "rad",
        "pow",
        "numberFormat",
        "icuText",
        "icuBestText",
        "subText",
        "textLength",
      )
  }
}
