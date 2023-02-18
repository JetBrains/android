/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.mocks

import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.repository.DatabaseRepositoryImpl
import com.intellij.openapi.project.Project
import java.util.concurrent.Executor

/** A [DatabaseRepository] identical to [DatabaseRepositoryImpl] but open to extension. */
open class OpenDatabaseRepository(
  project: Project,
  executor: Executor,
  private val databaseRepository: DatabaseRepository = DatabaseRepositoryImpl(project, executor)
) : DatabaseRepository by databaseRepository {

  val openDatabases = mutableListOf<SqliteDatabaseId>()

  override suspend fun addDatabaseConnection(
    databaseId: SqliteDatabaseId,
    databaseConnection: DatabaseConnection
  ) {
    databaseRepository.addDatabaseConnection(databaseId, databaseConnection)
    openDatabases.add(databaseId)
  }

  override suspend fun closeDatabase(databaseId: SqliteDatabaseId) {
    databaseRepository.closeDatabase(databaseId)
    openDatabases.remove(databaseId)
  }

  override suspend fun clear() {
    databaseRepository.clear()
    openDatabases.clear()
  }
}
