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
package com.android.tools.idea.lang.androidSql

import com.intellij.openapi.fileTypes.LanguageFileType
import icons.StudioIcons
import javax.swing.Icon

const val ANDROID_SQL_DESCRIPTION = "Android Room SQL"

class AndroidSqlFileType private constructor() : LanguageFileType(AndroidSqlLanguage.INSTANCE) {
  companion object {
    /** Static field used in XML to register the instance. */
    @JvmStatic val INSTANCE = AndroidSqlFileType()
  }

  override fun getName(): String = ANDROID_SQL_DESCRIPTION
  override fun getDescription(): String = ANDROID_SQL_DESCRIPTION
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon = ANDROID_SQL_ICON
}

/**
 * Icon used for all things related to our SQL support.
 */
val ANDROID_SQL_ICON: Icon = StudioIcons.DeviceExplorer.DATABASE_FOLDER
