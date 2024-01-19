/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.messages

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.VisibleForTesting

/** Global formatting options. */
@State(
  name = "AndroidLogcatFormattingOptions",
  storages = [Storage("androidLogcatFormattingOptions.xml")],
)
internal class AndroidLogcatFormattingOptions @VisibleForTesting constructor() :
  PersistentStateComponent<AndroidLogcatFormattingOptions> {
  @OptionTag(converter = FormattingOptionsStyleConverter::class)
  var defaultFormatting: FormattingOptions.Style = FormattingOptions.Style.STANDARD

  @OptionTag(converter = FormattingOptionsConverter::class)
  var standardFormattingOptions: FormattingOptions = DEFAULT_STANDARD.copy()

  @OptionTag(converter = FormattingOptionsConverter::class)
  var compactFormattingOptions: FormattingOptions = DEFAULT_COMPACT.copy()

  override fun getState(): AndroidLogcatFormattingOptions = this

  override fun loadState(state: AndroidLogcatFormattingOptions) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(): AndroidLogcatFormattingOptions =
      ApplicationManager.getApplication().getService(AndroidLogcatFormattingOptions::class.java)

    fun getDefaultOptions() = getInstance().defaultFormatting.formattingOptions

    val DEFAULT_STANDARD =
      FormattingOptions(
        TimestampFormat(TimestampFormat.Style.DATETIME, enabled = true),
        ProcessThreadFormat(ProcessThreadFormat.Style.BOTH, enabled = true),
        TagFormat(maxLength = 23, hideDuplicates = false, enabled = true, colorize = true),
        AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = true),
        ProcessNameFormat(maxLength = 35, hideDuplicates = false, enabled = false),
      )

    val DEFAULT_COMPACT =
      FormattingOptions(
        TimestampFormat(TimestampFormat.Style.TIME, enabled = true),
        ProcessThreadFormat(ProcessThreadFormat.Style.BOTH, enabled = false),
        TagFormat(maxLength = 23, hideDuplicates = false, enabled = false, colorize = true),
        AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false),
        ProcessNameFormat(maxLength = 35, hideDuplicates = false, enabled = false),
      )
  }

  private class FormattingOptionsStyleConverter : Converter<FormattingOptions.Style>() {
    override fun toString(value: FormattingOptions.Style): String = value.name

    override fun fromString(value: String): FormattingOptions.Style =
      FormattingOptions.Style.valueOf(value)
  }

  private class FormattingOptionsConverter : Converter<FormattingOptions>() {
    private val gson = Gson()

    override fun toString(value: FormattingOptions): String =
      gson.toJson(value, FormattingOptions::class.java).replace(Regex("(?<!\\\\)\""), "'")

    override fun fromString(value: String): FormattingOptions? =
      gson.fromJson(value, FormattingOptions::class.java)
  }
}
