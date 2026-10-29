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

object RoomAnnotations {
  val PACKAGE_NAME = RoomAndroidxName("android.arch.persistence.room.", "androidx.room.")
  val ENTITY = RoomAndroidxName.of(PACKAGE_NAME, "Entity")
  val DATABASE = RoomAndroidxName.of(PACKAGE_NAME, "Database")
  val DAO = RoomAndroidxName.of(PACKAGE_NAME, "Dao")
  val COLUMN_INFO = RoomAndroidxName.of(PACKAGE_NAME, "ColumnInfo")
  val PRIMARY_KEY = RoomAndroidxName.of(PACKAGE_NAME, "PrimaryKey")
  val IGNORE = RoomAndroidxName.of(PACKAGE_NAME, "Ignore")
  val QUERY = RoomAndroidxName.of(PACKAGE_NAME, "Query")
  val DATABASE_VIEW = RoomAndroidxName.of(PACKAGE_NAME, "DatabaseView")
  val EMBEDDED = RoomAndroidxName.of(PACKAGE_NAME, "Embedded")
  val FTS3 = RoomAndroidxName.of(PACKAGE_NAME, "Fts3")
  val FTS4 = RoomAndroidxName.of(PACKAGE_NAME, "Fts4")
}

const val AUTO_VALUE_ANNOTATION = "com.google.auto.value.AutoValue"
