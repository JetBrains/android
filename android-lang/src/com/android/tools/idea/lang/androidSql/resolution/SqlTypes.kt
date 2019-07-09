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
package com.android.tools.idea.lang.androidSql.resolution

/**
 * Type of a [AndroidSqlColumn].
 *
 * SQLite is not strictly typed and we don't check if queries are type-safe. This information is only known in certain cases and used for
 * displaying to the user in code completion etc.
 */
interface SqlType {
  /** To be used in completion. */
  val typeName: String?
}

/**
 * A [SqlType] that just uses the name of the underlying java field.
 *
 * TODO: stop using this and track the effective SQL types instead.
 */
class JavaFieldSqlType(override val typeName: String) : SqlType

/**
 * Type used by the special columns added by FTS (full-text search) extensions.
 */
object FtsSqlType : SqlType {
  override val typeName = "(full-text search only)"
}

