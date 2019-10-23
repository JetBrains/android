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

import java.sql.SQLException
import java.sql.Statement

/**
 * This version of statement throws an exception anytime execute is called.
 * This is used to validate all queries to the database handle errors properly.
 */
class DatabaseTestStatement(stmt: Statement) : Statement by stmt {
  private val exceptionToThrow = SQLException("Test Statement throws exception on execute.")
  override fun execute(sql: String?): Boolean {
    throw exceptionToThrow
  }
}