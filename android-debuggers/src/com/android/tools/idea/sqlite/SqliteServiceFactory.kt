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
package com.android.tools.idea.sqlite

import com.android.tools.idea.sqlite.jdbc.SqliteJdbcService
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.ide.PooledThreadExecutor
import java.util.concurrent.Executor

interface SqliteServiceFactory {

  /**
   * Returns a [SqliteService] associated with the sqliteFile passed as argument.
   * @param sqliteFile The file containing the Sqlite database.
   * @param executor An executor for long-running and/or IO-bound tasks, such as [PooledThreadExecutor].
   */
  fun getSqliteService(sqliteFile: VirtualFile, executor: Executor): SqliteService
}

class SqliteServiceFactoryImpl : SqliteServiceFactory {
  override fun getSqliteService(sqliteFile: VirtualFile, executor: Executor): SqliteService {
    return SqliteJdbcService(sqliteFile, executor)
  }
}