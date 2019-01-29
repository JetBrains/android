/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.database

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement

/**
 * A connection class that returns {@link TestStatement} and {@TestPreparedStatement}'s in place
 * of a statement that writes to the database.
 */
class DatabaseTestConnection(val conn: Connection) : Connection by conn {
  override fun createStatement(): Statement {
    return DatabaseTestStatement(conn.createStatement())
  }

  override fun prepareStatement(sql: String?): PreparedStatement {
    return DatabaseTestPreparedStatement(conn.prepareStatement(sql))
  }

  override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement {
    return DatabaseTestPreparedStatement(conn.prepareStatement(sql, columnNames))
  }
}