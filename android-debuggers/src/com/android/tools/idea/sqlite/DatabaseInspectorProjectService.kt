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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.api.AppInspectorClient
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorControllerImpl
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnectionFactory
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnectionFactoryImpl
import com.android.tools.idea.sqlite.model.FileSqliteDatabase
import com.android.tools.idea.sqlite.model.LiveSqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactoryImpl
import com.google.common.collect.Sets
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import org.jetbrains.annotations.TestOnly
import org.jetbrains.ide.PooledThreadExecutor
import java.util.TreeMap
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.concurrent.withLock

/**
 * Intellij Project Service that holds the reference to the [DatabaseInspectorControllerImpl]
 * and is the entry point for opening a Sqlite database in the Database Inspector tool window.
 */
interface DatabaseInspectorProjectService {
  companion object {
    @JvmStatic fun getInstance(project: Project): DatabaseInspectorProjectService {
      return ServiceManager.getService(project, DatabaseInspectorProjectService::class.java)
    }
  }

  /**
   * [JComponent] that contains the view of the Database Inspector.
   */
  val sqliteInspectorComponent: JComponent

  /**
   * Opens a connection to the database contained in the file passed as argument. The database is then shown in the Database Inspector.
   */
  @AnyThread
  fun openSqliteDatabase(file: VirtualFile): ListenableFuture<SqliteDatabase>

  /**
   * Creates a [com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection]
   * and shows the database content in the Database Inspector.
   *
   * @param id Unique identifier of a connection to a database.
   * @param name The name of the database. Could be the path of the database for an on-disk database,
   * or a different string for other types of database. (eg :memory: for an in-memory database)
   * @param messenger The [AppInspectorClient.CommandMessenger] used to send messages between studio and an on-device inspector.
   */
  @AnyThread
  fun openSqliteDatabase(messenger: AppInspectorClient.CommandMessenger, id: Int, name: String): ListenableFuture<SqliteDatabase>

  /**
   * Runs the query passed as argument in the Sqlite Inspector.
   */
  @UiThread
  fun runSqliteStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement)

  /**
   * Re-downloads and opens the file associated with the [FileSqliteDatabase] passed as argument.
   */
  @AnyThread
  fun reDownloadAndOpenFile(database: FileSqliteDatabase, progress: DownloadProgress): ListenableFuture<Unit>

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
}

class DatabaseInspectorProjectServiceImpl @NonInjectable @TestOnly constructor(
  private val project: Project,
  private val toolWindowManager: ToolWindowManager = ToolWindowManager.getInstance(project),
  edtExecutor: Executor = EdtExecutorService.getInstance(),
  taskExecutor: Executor = PooledThreadExecutor.INSTANCE,
  private val databaseConnectionFactory: DatabaseConnectionFactory = DatabaseConnectionFactoryImpl(),
  private val fileOpener: Consumer<VirtualFile> = Consumer { OpenFileAction.openFile(it, project) },
  private val viewFactory: DatabaseInspectorViewsFactory = DatabaseInspectorViewsFactoryImpl(),
  private val model: DatabaseInspectorController.Model = ModelImpl(),
  private val createController: (DatabaseInspectorController.Model) -> DatabaseInspectorController = { myModel ->
    DatabaseInspectorControllerImpl(
      project,
      myModel,
      viewFactory,
      edtExecutor,
      taskExecutor
    ).also {
      it.setUp()
      Disposer.register(project, it)
    }
  }) : DatabaseInspectorProjectService {

  @NonInjectable
  @TestOnly
  constructor(project: Project, edtExecutor: Executor, taskExecutor: Executor, viewFactory: DatabaseInspectorViewsFactory) : this (
    project,
    ToolWindowManager.getInstance(project),
    edtExecutor,
    taskExecutor,
    DatabaseConnectionFactoryImpl(),
    Consumer { OpenFileAction.openFile(it, project) },
    viewFactory,
    ModelImpl(),
    { myModel ->
      DatabaseInspectorControllerImpl(
        project,
        myModel,
        viewFactory,
        edtExecutor,
        taskExecutor
      ).also {
        it.setUp()
        Disposer.register(project, it)
      }
    }
  )

  constructor(project: Project) : this (
    project,
    EdtExecutorService.getInstance(),
    PooledThreadExecutor.INSTANCE, DatabaseInspectorViewsFactoryImpl()
  )

  private val edtExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(taskExecutor)

  private val openFileSqliteDatabases = Sets.newConcurrentHashSet<FileSqliteDatabase>()

  private val controller: DatabaseInspectorController by lazy @UiThread {
    ApplicationManager.getApplication().assertIsDispatchThread()
    createController(model)
  }

  override val sqliteInspectorComponent
    @UiThread get() = controller.component

  init {
    // TODO(b/145341040) investigate performance impact.
    model.addListener(object : DatabaseInspectorController.Model.Listener {
      override fun onDatabaseAdded(database: SqliteDatabase) {
        if (database is FileSqliteDatabase) {
          openFileSqliteDatabases.add(database)
        }
      }

      override fun onDatabaseRemoved(database: SqliteDatabase) {
        if (database is FileSqliteDatabase) {
          openFileSqliteDatabases.remove(database)
        }
      }
    })

    val virtualFileListener = object : BulkFileListener {
      override fun before(events: MutableList<out VFileEvent>) {
        if (openFileSqliteDatabases.isEmpty()) return

        val toClose = mutableListOf<SqliteDatabase>()
        for (event in events) {
          if (event !is VFileDeleteEvent) continue

          for (database in openFileSqliteDatabases) {
            if (VfsUtil.isAncestor(event.file, database.virtualFile, false)) {
              toClose.add(database)
            }
          }
        }

        toClose.forEach { controller.closeDatabase(it) }
      }
    }

    val messageBusConnection = project.messageBus.connect(project)
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, virtualFileListener)
  }

  @AnyThread
  override fun openSqliteDatabase(file: VirtualFile): ListenableFuture<SqliteDatabase> {
    val databaseConnectionFuture = databaseConnectionFactory.getDatabaseConnection(file, taskExecutor)

    // TODO(b/139525976)
    val name = file.path.split("data/data/").getOrNull(1)?.replace("databases/", "") ?: file.path

    val databaseFuture: ListenableFuture<SqliteDatabase> = taskExecutor.transform(databaseConnectionFuture) { databaseConnection ->
      FileSqliteDatabase(name, databaseConnection, file)
    }

    return openSqliteDatabaseInInspector(databaseFuture)
  }

  @AnyThread
  override fun openSqliteDatabase(messenger: AppInspectorClient.CommandMessenger, id: Int, name: String): ListenableFuture<SqliteDatabase> {
    val databaseConnectionFuture = databaseConnectionFactory.getLiveDatabaseConnection(messenger, id, taskExecutor)

    val databaseFuture: ListenableFuture<SqliteDatabase> = taskExecutor.transform(databaseConnectionFuture) { databaseConnection ->
      LiveSqliteDatabase(name, databaseConnection)
    }

    return openSqliteDatabaseInInspector(databaseFuture)
  }

  private fun openSqliteDatabaseInInspector(databaseFuture: ListenableFuture<SqliteDatabase>): ListenableFuture<SqliteDatabase> {
    invokeLaterIfNeeded {
      toolWindowManager.getToolWindow(DatabaseInspectorToolWindowFactory.TOOL_WINDOW_ID)?.show {
        controller.addSqliteDatabase(databaseFuture)
      }
    }

    return databaseFuture
  }

  @UiThread
  override fun runSqliteStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement) {
    controller.runSqlStatement(database, sqliteStatement)
  }

  @AnyThread
  override fun reDownloadAndOpenFile(database: FileSqliteDatabase, progress: DownloadProgress): ListenableFuture<Unit> {
    val deviceFileId = DeviceFileId.fromVirtualFile(database.virtualFile)
                       ?: return Futures.immediateFailedFuture(IllegalStateException("DeviceFileId not found"))
    val downloadFuture = DeviceFileDownloaderService.getInstance(project).downloadFile(deviceFileId, progress)

    return edtExecutor.transform(downloadFuture) { downloadedFileData ->
      fileOpener.accept(downloadedFileData.virtualFile)
      return@transform
    }
  }

  @AnyThread
  override fun hasOpenDatabase() = model.openDatabases.isNotEmpty()

  @AnyThread
  override fun getOpenDatabases(): Set<SqliteDatabase> = model.openDatabases.keys

  private class ModelImpl : DatabaseInspectorController.Model {

    private val lock = ReentrantLock()

    @GuardedBy("lock")
    private val listeners = mutableListOf<DatabaseInspectorController.Model.Listener>()

    @GuardedBy("lock")
    override val openDatabases: TreeMap<SqliteDatabase, SqliteSchema> = TreeMap(
      Comparator.comparing { database: SqliteDatabase -> database.name }
    )

    @AnyThread
    override fun getSortedIndexOf(database: SqliteDatabase) = lock.withLock { openDatabases.headMap(database).size }

    @AnyThread
    override fun add(database: SqliteDatabase, sqliteSchema: SqliteSchema): Unit = lock.withLock {
      openDatabases[database] = sqliteSchema
      listeners.forEach { it.onDatabaseAdded(database) }
    }

    @AnyThread
    override fun remove(database: SqliteDatabase): Unit = lock.withLock {
      openDatabases.remove(database)
      listeners.forEach { it.onDatabaseRemoved(database) }
    }

    override fun addListener(modelListener: DatabaseInspectorController.Model.Listener): Unit = lock.withLock {
      listeners.add(modelListener)
    }

    override fun removeListener(modelListener: DatabaseInspectorController.Model.Listener): Unit = lock.withLock {
      listeners.remove(modelListener)
    }
  }
}