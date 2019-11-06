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
@file:Suppress("NonDefaultConstructor")

package com.android.tools.idea.sqlite

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.device.fs.DeviceFileDownloaderService
import com.android.tools.idea.device.fs.DeviceFileId
import com.android.tools.idea.device.fs.DownloadProgress
import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.controllers.SqliteControllerImpl
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnectionFactoryImpl
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactoryImpl
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
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.util.TreeMap
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.concurrent.withLock

/**
 * Intellij Project Service that holds the reference to the [SqliteControllerImpl]
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
   * Runs the query passed as argument in the Sqlite Inspector.
   */
  @UiThread
  fun runSqliteStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement)

  /**
   * Syncs the local database with the one on the device.
   * Downloads and re-opens the database.
   */
  @AnyThread
  fun sync(database: SqliteDatabase, progress: DownloadProgress): ListenableFuture<Unit>

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

class DatabaseInspectorProjectServiceImpl @JvmOverloads constructor(
  private val project: Project,
  private val toolWindowManager: ToolWindowManager,
  edtExecutor: Executor = EdtExecutorService.getInstance(),
  taskExecutor: Executor = PooledThreadExecutor.INSTANCE,
  private val fileOpener: Consumer<VirtualFile> = Consumer { OpenFileAction.openFile(it, project) },
  private val viewFactory: DatabaseInspectorViewsFactory = DatabaseInspectorViewsFactoryImpl(),
  private val model: SqliteController.Model = Model(),
  private val createController: (SqliteController.Model) -> SqliteController = { myModel ->
    SqliteControllerImpl(
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

  private val edtExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(edtExecutor)
  private val taskExecutor: FutureCallbackExecutor = FutureCallbackExecutor.wrap(taskExecutor)

  private val lock = ReentrantLock()

  @GuardedBy("lock")
  private val databaseToFile = mutableMapOf<SqliteDatabase, VirtualFile>()

  private val controller: SqliteController by lazy @UiThread {
    ApplicationManager.getApplication().assertIsDispatchThread()
    createController(model)
  }

  override val sqliteInspectorComponent
    @UiThread get() = controller.component

  init {
    model.addListener(object : SqliteController.Model.Listener {
      override fun onDatabaseAdded(database: SqliteDatabase) { }
      override fun onDatabaseRemoved(database: SqliteDatabase) {
        lock.withLock { databaseToFile.remove(database) }
      }
    })

    val virtualFileListener = object : BulkFileListener {
      override fun before(events: MutableList<out VFileEvent>) {
        if (databaseToFile.keys.isEmpty()) return

        val toClose = mutableListOf<SqliteDatabase>()
        for (event in events) {
          if (event !is VFileDeleteEvent) continue

          for ((database, virtualFile) in databaseToFile) {
            if (VfsUtil.isAncestor(event.file, virtualFile, false)) {
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
    val databaseConnectionFuture = DatabaseConnectionFactoryImpl().getDatabaseConnection(file, taskExecutor)

    val openSqliteServiceFuture = taskExecutor.transform(databaseConnectionFuture) { databaseConnection ->
      Disposer.register(project, databaseConnection)
      // TODO(b/139525976)
      val name = file.path.split("data/data/").getOrNull(1)?.replace("databases/", "") ?: file.path
      val database = SqliteDatabase(name, databaseConnection)
      Disposer.register(project, database)

      lock.withLock { databaseToFile[database] = file }

      return@transform database
    }

    toolWindowManager.getToolWindow(DatabaseInspectorToolWindowFactory.TOOL_WINDOW_ID).show { controller.addSqliteDatabase(openSqliteServiceFuture) }

    return openSqliteServiceFuture
  }

  @UiThread
  override fun runSqliteStatement(database: SqliteDatabase, sqliteStatement: SqliteStatement) {
    controller.runSqlStatement(database, sqliteStatement)
  }

  @AnyThread
  override fun sync(database: SqliteDatabase, progress: DownloadProgress): ListenableFuture<Unit> {
    val virtualFile = lock.withLock {
      databaseToFile[database] ?: return Futures.immediateFailedFuture(IllegalStateException("DB not found"))
    }

    val deviceFileId: DeviceFileId = virtualFile.getUserData(DeviceFileId.KEY)
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

  private class Model : SqliteController.Model {

    private val lock = ReentrantLock()

    @GuardedBy("lock")
    private val listeners = mutableListOf<SqliteController.Model.Listener>()

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

    override fun addListener(modelListener: SqliteController.Model.Listener): Unit = lock.withLock {
      listeners.add(modelListener)
    }

    override fun removeListener(modelListener: SqliteController.Model.Listener): Unit = lock.withLock {
      listeners.remove(modelListener)
    }
  }
}