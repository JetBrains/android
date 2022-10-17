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
package com.android.tools.idea.lang.androidSql.room

import com.android.support.AndroidxName

object RoomAnnotations {
  val PACKAGE_NAME = AndroidxName("android.arch.persistence.room.", "androidx.room.")
  val ENTITY = AndroidxName.of(PACKAGE_NAME, "Entity")
  val DATABASE = AndroidxName.of(PACKAGE_NAME, "Database")
  val DAO = AndroidxName.of(PACKAGE_NAME, "Dao")
  val COLUMN_INFO = AndroidxName.of(PACKAGE_NAME, "ColumnInfo")
  val PRIMARY_KEY = AndroidxName.of(PACKAGE_NAME, "PrimaryKey")
  val IGNORE = AndroidxName.of(PACKAGE_NAME, "Ignore")
  val QUERY = AndroidxName.of(PACKAGE_NAME, "Query")
  val DATABASE_VIEW = AndroidxName.of(PACKAGE_NAME, "DatabaseView")
  val EMBEDDED = AndroidxName.of(PACKAGE_NAME, "Embedded")
  val FTS3 = AndroidxName.of(PACKAGE_NAME, "Fts3")
  val FTS4 = AndroidxName.of(PACKAGE_NAME, "Fts4")
}

const val AUTO_VALUE_ANNOTATION = "com.google.auto.value.AutoValue"

