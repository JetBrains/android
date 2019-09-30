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
package com.android.tools.idea.sqliteExplorer

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.SqliteServiceFactoryImpl
import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactoryImpl
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewImpl
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.concurrent.withLock

/**
 * Intellij Project Service that holds the reference to the [SqliteController]
 * and is the entry point for opening a Sqlite database in the Sqlite Explorer tool window.
 */
interface SqliteExplorerProjectService : SchemaProvider {
  companion object {
    @JvmStatic fun getInstance(project: Project): SqliteExplorerProjectService {
      return ServiceManager.getService(project, SqliteExplorerProjectService::class.java)
    }
  }

  /**
   * [JComponent] that contains the view of the Sqlite Explorer.
   */
  val sqliteInspectorComponent: JComponent

  /**
   * Opens the database contained in the file passed as argument in the Sqlite Inspector.
   */
  @AnyThread
  fun openSqliteDatabase(file: VirtualFile)

  /**
   * Runs the query passed as argument in the Sqlite Inspector.
   */
  @UiThread
  fun runQuery(database: SqliteDatabase, query: String)

  /**
   * Returns true if the Sqlite Inspector has an open database, false otherwise.
   */
  @AnyThread
  fun hasOpenDatabase(): Boolean

  /**
   * Returns a list of the currently open [SqliteDatabase].
   */
  @AnyThread
  fun getOpenDatabases(): Set<SqliteDatabase>

  /**
   * Returns the [SqliteSchema] corresponding to the [SqliteDatabase] passed as argument.
   */
  @AnyThread
  override fun getSchema(database: SqliteDatabase): SqliteSchema?

  /**
   * Returns the index of the [SqliteDatabase] passed as argument. Databases are sorted in alphabetical order by name.
   */
  @AnyThread
  fun getSortedIndexOf(database: SqliteDatabase): Int

  /**
   * Adds a [SqliteDatabase] and corresponding [SqliteSchema] to the open databases.
   */
  @AnyThread
  fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema)

  /**
   * Removes the [SqliteDatabase] passed as argument from the open databases.
   */
  @AnyThread
  fun remove(database: SqliteDatabase)
}

class SqliteExplorerProjectServiceImpl(
  private val project: Project,
  private val toolWindowManager: ToolWindowManager
) : SqliteExplorerProjectService {

  private val lock = ReentrantLock()

  /**
   * The model of Sqlite Explorer.
   *
   * Maps each open database to its [SqliteSchema].
   * The keys are sorted in alphabetical order on the name of the database.
   */
  @GuardedBy("lock")
  private val openDatabases: TreeMap<SqliteDatabase, SqliteSchema> = TreeMap(
    Comparator.comparing { database: SqliteDatabase -> database.name }
  )

  private val controller: SqliteController by lazy @UiThread {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val fileOpener = Consumer<VirtualFile> { OpenFileAction.openFile(it, project) }

    SqliteController(
      project,
      SqliteExplorerProjectService.getInstance(project),
      SqliteServiceFactoryImpl(),
      SqliteEditorViewFactoryImpl.getInstance(),
      SqliteViewImpl(project, project),
      fileOpener,
      EdtExecutorService.getInstance(),
      PooledThreadExecutor.INSTANCE
    ).also { it.setUp() }
  }

  override val sqliteInspectorComponent
    @UiThread get() = controller.sqliteView.component


  @AnyThread
  override fun openSqliteDatabase(file: VirtualFile) {
    toolWindowManager.getToolWindow(SqliteExplorerToolWindowFactory.TOOL_WINDOW_ID).show { controller.openSqliteDatabase(file) }
  }

  @UiThread
  override fun runQuery(database: SqliteDatabase, query: String) {
    controller.runSqlStatement(database, query)
  }

  @AnyThread
  override fun hasOpenDatabase() = lock.withLock { openDatabases.isNotEmpty() }

  @AnyThread
  override fun getOpenDatabases(): Set<SqliteDatabase> = lock.withLock { openDatabases.keys }

  @AnyThread
  override fun getSortedIndexOf(database: SqliteDatabase) = lock.withLock { openDatabases.headMap(database).size }

  @AnyThread
  override fun getSchema(database: SqliteDatabase) = lock.withLock { openDatabases[database] }

  @AnyThread
  override fun remove(database: SqliteDatabase): Unit = lock.withLock { openDatabases.remove(database) }

  @AnyThread
  override fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema) = lock.withLock { openDatabases[database] = sqliteSchema }
}