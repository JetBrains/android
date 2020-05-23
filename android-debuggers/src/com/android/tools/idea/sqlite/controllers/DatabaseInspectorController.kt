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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorClientCommandsChannel
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController.SavedUiState
import com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController.EvaluationParams
import com.android.tools.idea.sqlite.databaseConnection.DatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.model.getAllDatabaseIds
import com.android.tools.idea.sqlite.ui.DatabaseInspectorViewsFactory
import com.android.tools.idea.sqlite.ui.mainView.AddColumns
import com.android.tools.idea.sqlite.ui.mainView.AddTable
import com.android.tools.idea.sqlite.ui.mainView.DatabaseDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.DatabaseInspectorView
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteColumn
import com.android.tools.idea.sqlite.ui.mainView.IndexedSqliteTable
import com.android.tools.idea.sqlite.ui.mainView.RemoveColumns
import com.android.tools.idea.sqlite.ui.mainView.RemoveTable
import com.android.tools.idea.sqlite.ui.mainView.SchemaDiffOperation
import com.android.tools.idea.sqlite.ui.mainView.ViewDatabase
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.swing.JComponent

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 *
 * All methods are assumed to run on the UI (EDT) thread.
 */
class DatabaseInspectorControllerImpl(
  private val project: Project,
  private val model: DatabaseInspectorModel,
  private val viewFactory: DatabaseInspectorViewsFactory,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController {

  private val uiThread = edtExecutor.asCoroutineDispatcher()
  private val workerThread = taskExecutor.asCoroutineDispatcher()
  private val view = viewFactory.createDatabaseInspectorView(project)
  private val tabsToRestore = mutableListOf<TabDescription>()

  /**
   * Controllers for all open tabs, keyed by id.
   *
   * <p>Multiple tables can be open at the same time in different tabs.
   * This map keeps track of corresponding controllers.
   */
  private val resultSetControllers = mutableMapOf<TabId, DatabaseInspectorController.TabController>()

  private val sqliteViewListener = SqliteViewListenerImpl()

  private var databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel? = null

  private var evaluatorTabCount = 0
  private var keepConnectionsOpen = false
  set(value) {
    databaseInspectorClientCommandsChannel?.keepConnectionsOpen(value)?.transform(edtExecutor) {
        if (it != null) {
          field = it
          view.updateKeepConnectionOpenButton(value)
        }
      }
  }

  private val databaseInspectorAnalyticsTracker = DatabaseInspectorAnalyticsTracker.getInstance(project)

  private val modelListener = object : DatabaseInspectorModel.Listener {
    private var currentOpenDatabaseIds = listOf<SqliteDatabaseId>()
    private var currentCloseDatabaseIds = listOf<SqliteDatabaseId>()

    override fun onDatabasesChanged(openDatabaseIds: List<SqliteDatabaseId>, closeDatabaseIds: List<SqliteDatabaseId>) {
      val currentState = currentOpenDatabaseIds.map { ViewDatabase(it, true) } + currentCloseDatabaseIds.map { ViewDatabase(it, false) }
      val newState = openDatabaseIds.map { ViewDatabase(it, true) } + closeDatabaseIds.map { ViewDatabase(it, false) }

      val diffOperations = performDiff(currentState, newState)

      closeTabsBelongingToClosedDatabases(currentOpenDatabaseIds, openDatabaseIds)
      view.updateDatabases(diffOperations)

      currentOpenDatabaseIds = openDatabaseIds
      currentCloseDatabaseIds = closeDatabaseIds
    }

    override fun onSchemaChanged(databaseId: SqliteDatabaseId, oldSchema: SqliteSchema, newSchema: SqliteSchema) {
      updateExistingDatabaseSchemaView(databaseId, oldSchema, newSchema)
    }

    private fun closeTabsBelongingToClosedDatabases(currentlyOpenDbs: List<SqliteDatabaseId>, newOpenDbs: List<SqliteDatabaseId>) {
      val closedDbs = currentlyOpenDbs.filter { !newOpenDbs.contains(it) }
      val tabsToClose = resultSetControllers.keys
        .filterIsInstance<TabId.TableTab>()
        .filter { closedDbs.contains(it.databaseId) }

      tabsToClose.forEach { closeTab(it) }
    }

    private fun performDiff(currentState: List<ViewDatabase>, newState: List<ViewDatabase>): List<DatabaseDiffOperation> {
      val sortedNewState = newState.sortedBy { it.databaseId.name }

      val toAdd = newState
        .filter { !currentState.contains(it) }
        .map { DatabaseDiffOperation.AddDatabase(it, model.getDatabaseSchema(it.databaseId), sortedNewState.indexOf(it)) }
      val toRemove = currentState.filter { !newState.contains(it) }.map { DatabaseDiffOperation.RemoveDatabase(it) }

      return toAdd + toRemove
    }
  }

  override val component: JComponent
    get() = view.component

  @UiThread
  override fun setUp() {
    view.addListener(sqliteViewListener)
    model.addListener(modelListener)

    view.updateKeepConnectionOpenButton(keepConnectionsOpen)
  }

  override suspend fun addSqliteDatabase(deferredDatabase: Deferred<SqliteDatabase>) = withContext(uiThread) {
    view.startLoading("Getting database...")

    val database = try {
      deferredDatabase.await()
    }
    catch (e: Exception) {
      ensureActive()
      view.reportError("Error getting database", e)
      throw e
    }
    addSqliteDatabase(database)
  }

  override suspend fun addSqliteDatabase(database: SqliteDatabase) = withContext(uiThread) {
    Disposer.register(this@DatabaseInspectorControllerImpl, database.databaseConnection)
    val schema = readDatabaseSchema(database.databaseConnection) ?: return@withContext
    addNewDatabase(database, schema)
  }

  override suspend fun runSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement) = withContext(uiThread) {
    openNewEvaluatorTab().showAndExecuteSqlStatement(databaseId, sqliteStatement).await()
  }

  override suspend fun closeDatabase(databaseId: SqliteDatabaseId) = withContext(uiThread) {
    val openDatabases = model.getOpenDatabaseIds()
    val tabsToClose = if (openDatabases.size == 1 && openDatabases.first() == databaseId) {
      // close all tabs
      resultSetControllers.keys.toList()
    }
    else {
      // only close tabs associated with this database
      resultSetControllers.keys
        .filterIsInstance<TabId.TableTab>()
        .filter { it.databaseId == databaseId }
    }

    tabsToClose.forEach { closeTab(it) }

    val databaseConnection = model.removeDatabaseSchema(databaseId)
    checkNotNull(databaseConnection)

    withContext(workerThread) {
      Disposer.dispose(databaseConnection)
    }
  }

  @UiThread
  override fun showError(message: String, throwable: Throwable?) {
    view.reportError(message, throwable)
  }

  override fun restoreSavedState(previousState: SavedUiState?) {
    val savedState = previousState as? SavedUiStateImpl
    tabsToRestore.clear()
    savedState?.let {
      val (tabsNotRequiringDb, tabsRequiringDb) = savedState.tabs.partition { it is TabDescription.AdHocQuery && it.databasePath == null }

      // tabs associated with a database will be opened when the database is opened
      tabsToRestore.addAll(tabsRequiringDb)

      // tabs not associated with a db can be opened immediately
      tabsNotRequiringDb
        .map { (it as TabDescription.AdHocQuery).query }
        .forEach {
          openNewEvaluatorTab(EvaluationParams(null, it))
        }
    }
  }

  override fun saveState(): SavedUiState {
    val tabs = resultSetControllers.mapNotNull {
      when (val tabId = it.key) {
        is TabId.TableTab -> TabDescription.Table(tabId.databaseId.path, tabId.tableName)
        is TabId.AdHocQueryTab -> {
          val params = (it.value as SqliteEvaluatorController).saveEvaluationParams()
          TabDescription.AdHocQuery(params.databaseId?.path, params.statementText)
        }
      }
    }
    return SavedUiStateImpl(tabs)
  }

  override fun setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel?) {
    this.databaseInspectorClientCommandsChannel = databaseInspectorClientCommandsChannel
    databaseInspectorClientCommandsChannel?.keepConnectionsOpen(keepConnectionsOpen)
  }

  override suspend fun databasePossiblyChanged() = withContext(uiThread) {
    // update schemas
    model.getOpenDatabaseIds().forEach { updateDatabaseSchema(it) }
    // update tabs
    resultSetControllers.values.forEach { it.notifyDataMightBeStale() }
  }

  override fun dispose() = invokeAndWaitIfNeeded {
    view.removeListener(sqliteViewListener)
    model.removeListener(modelListener)

    resultSetControllers.values
      .asSequence()
      .filterIsInstance<SqliteEvaluatorController>()
      .forEach { it.removeListeners() }
  }

  private suspend fun readDatabaseSchema(databaseConnection: DatabaseConnection): SqliteSchema? = withContext(workerThread) {
    try {
      val schema = databaseConnection.readSchema().await()
      withContext(uiThread) { view.stopLoading() }
      schema
    }
    catch (e: LiveInspectorException) {
      null
    }
    catch (e: Exception) {
      ensureActive()
      withContext(uiThread) {
        view.reportError("Error reading Sqlite database", e)
      }
      throw e
    }
  }

  private fun addNewDatabase(database: SqliteDatabase, sqliteSchema: SqliteSchema) {
    model.addDatabaseSchema(database.id, database.databaseConnection, sqliteSchema)
    restoreTabs(database.id, sqliteSchema)
  }

  private fun restoreTabs(databaseId: SqliteDatabaseId, schema: SqliteSchema) {
    tabsToRestore.filter { it.databasePath == databaseId.path }
      .also { tabsToRestore.removeAll(it) }
      .forEach { tabDescription ->
        when (tabDescription) {
          is TabDescription.Table ->
            schema.tables.find { tabDescription.tableName == it.name }?.let { openTableTab(databaseId, it) }
          is TabDescription.AdHocQuery -> {
            openNewEvaluatorTab(EvaluationParams(databaseId, tabDescription.query))
          }
        }
      }
  }

  private fun closeTab(tabId: TabId) {
    view.closeTab(tabId)
    val controller = resultSetControllers.remove(tabId)
    controller?.let(Disposer::dispose)
  }

  private suspend fun updateDatabaseSchema(databaseId: SqliteDatabaseId) {
    if (model.getCloseDatabaseIds().contains(databaseId)) return

    val databaseConnection = model.getDatabaseConnection(databaseId)!!
    // TODO(b/154733971) this only works because the suspending function is called first, otherwise we have concurrency issues
    val newSchema = readDatabaseSchema(databaseConnection) ?: return
    val oldSchema = model.getDatabaseSchema(databaseId) ?: return
    withContext(uiThread) {
      if (oldSchema != newSchema) {
        model.updateSchema(databaseId, newSchema)
      }
    }
  }

  private fun updateExistingDatabaseSchemaView(databaseId: SqliteDatabaseId, oldSchema: SqliteSchema, newSchema: SqliteSchema) {
    val diffOperations = mutableListOf<SchemaDiffOperation>()

    oldSchema.tables.forEach { oldTable ->
      val newTable = newSchema.tables.find { it.name == oldTable.name }
      if (newTable == null) {
        diffOperations.add(RemoveTable(oldTable.name))
      }
      else {
        val columnsToRemove = oldTable.columns - newTable.columns
        if (columnsToRemove.isNotEmpty()) {
          diffOperations.add(RemoveColumns(oldTable.name, columnsToRemove, newTable))
        }
      }
    }

    newSchema.tables.sortedBy { it.name }.forEachIndexed { tableIndex, newTable ->
      val indexedSqliteTable = IndexedSqliteTable(newTable, tableIndex)
      val oldTable = oldSchema.tables.firstOrNull { it.name == newTable.name }
      if (oldTable == null) {
        val indexedColumnsToAdd = newTable.columns
          .sortedBy { it.name }
          .mapIndexed { colIndex, sqliteColumn -> IndexedSqliteColumn(sqliteColumn, colIndex) }

        diffOperations.add(AddTable(indexedSqliteTable, indexedColumnsToAdd))
      }
      else if (oldTable != newTable) {
        val indexedColumnsToAdd = newTable.columns
          .sortedBy { it.name }
          .mapIndexed { colIndex, sqliteColumn -> IndexedSqliteColumn(sqliteColumn, colIndex) }
          .filterNot { oldTable.columns.contains(it.sqliteColumn) }

        diffOperations.add(AddColumns(newTable.name, indexedColumnsToAdd, newTable))
      }
    }

    try {
      view.updateDatabaseSchema(ViewDatabase(databaseId, true), diffOperations)
    } catch (e: Exception) {
      // this UI change does not correspond to a change in the model, therefore it has to be done manually
      view.updateDatabases(listOf(DatabaseDiffOperation.RemoveDatabase(ViewDatabase(databaseId, true))))
      val index = model.getAllDatabaseIds().sortedBy { it.name }.indexOf(databaseId)
      view.updateDatabases(listOf(DatabaseDiffOperation.AddDatabase(ViewDatabase(databaseId, true), newSchema, index)))
    }
  }

  private fun openNewEvaluatorTab(evaluationParams: EvaluationParams? = null): SqliteEvaluatorController {
    evaluatorTabCount += 1

    val tabId = TabId.AdHocQueryTab(evaluatorTabCount)

    val sqliteEvaluatorView = viewFactory.createEvaluatorView(
      project,
      object : SchemaProvider { override fun getSchema(databaseId: SqliteDatabaseId) = model.getDatabaseSchema(databaseId) },
      viewFactory.createTableView()
    )

    view.openTab(tabId, "New Query [$evaluatorTabCount]", StudioIcons.DatabaseInspector.TABLE, sqliteEvaluatorView.component)

    val sqliteEvaluatorController = SqliteEvaluatorController(
      project,
      model,
      sqliteEvaluatorView,
      { closeTab(tabId) },
      edtExecutor,
      taskExecutor
    )
    Disposer.register(project, sqliteEvaluatorController)
    sqliteEvaluatorController.setUp(evaluationParams)

    sqliteEvaluatorController.addListener(SqliteEvaluatorControllerListenerImpl())

    resultSetControllers[tabId] = sqliteEvaluatorController

    return sqliteEvaluatorController
  }

  @UiThread
  private fun openTableTab(databaseId: SqliteDatabaseId, table: SqliteTable) {
    val tabId = TabId.TableTab(databaseId, table.name)
    if (tabId in resultSetControllers) {
      view.focusTab(tabId)
      return
    }

    val tableView = viewFactory.createTableView()
    val icon = if (table.isView) StudioIcons.DatabaseInspector.VIEW else StudioIcons.DatabaseInspector.TABLE
    view.openTab(tabId, table.name, icon, tableView.component)

    val tableController = TableController(
      closeTabInvoked = { closeTab(tabId) },
      project = project,
      view = tableView,
      tableSupplier = { model.getDatabaseSchema(databaseId)?.tables?.firstOrNull{ it.name == table.name } },
      databaseConnection = model.getDatabaseConnection(databaseId)!!,
      sqliteStatement = createSqliteStatement(project, selectAllAndRowIdFromTable(table)),
      edtExecutor = edtExecutor,
      taskExecutor = taskExecutor
    )
    Disposer.register(project, tableController)
    resultSetControllers[tabId] = tableController

    tableController.setUp().addCallback(edtExecutor, object : FutureCallback<Unit> {
      override fun onSuccess(result: Unit?) {
      }

      override fun onFailure(t: Throwable) {
        view.reportError("Error reading Sqlite table \"${table.name}\"", t)
        closeTab(tabId)
      }
    })
  }

  private inner class SqliteViewListenerImpl : DatabaseInspectorView.Listener {

    /** [CoroutineScope] used for scheduling asynchronous tasks in response to UI events. */
    private val scope = AndroidCoroutineScope(this@DatabaseInspectorControllerImpl)

    override fun tableNodeActionInvoked(databaseId: SqliteDatabaseId, table: SqliteTable) {
      databaseInspectorAnalyticsTracker.trackStatementExecuted(
        AppInspectionEvent.DatabaseInspectorEvent.StatementContext.SCHEMA_STATEMENT_CONTEXT
      )
      openTableTab(databaseId, table)
    }

    override fun openSqliteEvaluatorTabActionInvoked() {
      openNewEvaluatorTab()
    }

    override fun closeTabActionInvoked(tabId: TabId) {
      closeTab(tabId)
    }

    override fun refreshAllOpenDatabasesSchemaActionInvoked() {
      databaseInspectorAnalyticsTracker.trackTargetRefreshed(AppInspectionEvent.DatabaseInspectorEvent.TargetType.SCHEMA_TARGET)
      scope.launch(uiThread) {
        model.getOpenDatabaseIds().forEach { updateDatabaseSchema(it) }
      }
    }

    override fun toggleKeepConnectionOpenActionInvoked() {
      keepConnectionsOpen = !keepConnectionsOpen
    }
  }

  inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorController.Listener {
    private val scope = AndroidCoroutineScope(this@DatabaseInspectorControllerImpl)

    override fun onSqliteStatementExecuted(databaseId: SqliteDatabaseId) {
      scope.launch(uiThread) {
        updateDatabaseSchema(databaseId)
      }
    }
  }

  private class SavedUiStateImpl(val tabs: List<TabDescription>) : SavedUiState

  @UiThread
  private sealed class TabDescription {
    abstract val databasePath: String?

    class Table(override val databasePath: String, val tableName: String) : TabDescription()
    class AdHocQuery(override val databasePath: String?, val query: String) : TabDescription()
  }
}

/**
 * Interface that defines the contract of a SqliteController.
 */
interface DatabaseInspectorController : Disposable {
  val component: JComponent

  @UiThread
  fun setUp()

  /**
   * Waits for [deferredDatabase] to be completed and adds it to the inspector UI.
   *
   * A loading UI is displayed while waiting for [deferredDatabase] to be ready.
   */
  suspend fun addSqliteDatabase(deferredDatabase: Deferred<SqliteDatabase>)

  /**
   * Adds a database that is immediately ready
   */
  suspend fun addSqliteDatabase(database: SqliteDatabase)

  suspend fun runSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement)
  suspend fun closeDatabase(databaseId: SqliteDatabaseId)

  /**
   * Updates schema of all open databases and notifies each tab that its data might be stale.
   *
   * This method is called when a `DatabasePossiblyChanged` event is received from the the on-device inspector
   * which tells us that the data in a database might have changed (schema, tables or both).
   */
  suspend fun databasePossiblyChanged()

  /**
   * Shows the error in the view.
   */
  @UiThread
  fun showError(message: String, throwable: Throwable?)

  @UiThread
  fun restoreSavedState(previousState: SavedUiState?)

  @UiThread
  fun saveState(): SavedUiState

  @UiThread
  fun setDatabaseInspectorClientCommandsChannel(databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel?)

  interface TabController : Disposable {
    val closeTabInvoked: () -> Unit
    /**
     * Triggers a refresh operation in this tab.
     * If called multiple times in sequence, this method is re-executed only once the future from the first invocation completes.
     * While the future of the first invocation is not completed, the future from the first invocation is returned to following invocations.
     */
    fun refreshData(): ListenableFuture<Unit>

    /**
     * Notify this tab that its data might be stale.
     */
    fun notifyDataMightBeStale()
  }

  /**
   * Marker interface for opaque object that has UI state that should be restored once DatabaseInspector is reconnected.
   */
  interface SavedUiState
}

sealed class TabId {
  data class TableTab(val databaseId: SqliteDatabaseId, val tableName: String) : TabId()
  data class AdHocQueryTab(val tabId: Int) : TabId()
}