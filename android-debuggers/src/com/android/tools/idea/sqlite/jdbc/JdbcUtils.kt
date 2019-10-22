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
package com.android.tools.idea.sqlite.jdbc

import com.android.tools.idea.sqlite.model.SqliteStatement
import java.sql.Connection
import java.sql.PreparedStatement

/**
 * Takes a [SqliteStatement] and returns a [PreparedStatement] by assigning values to parameters in the statement.
 */
fun Connection.resolvePreparedStatement(sqliteStatement: SqliteStatement): PreparedStatement {
  val preparedStatement = prepareStatement(sqliteStatement.sqliteStatementText)
  sqliteStatement.parametersValues.forEachIndexed { index, value -> preparedStatement.setString(index+1, value.toString()) }
  return preparedStatement
}