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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.api.AppInspectionIdeServices.Severity
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transformNullable
import com.android.tools.idea.sqlite.DatabaseInspectorAnalyticsTracker
import com.android.tools.idea.sqlite.DatabaseInspectorClientCommandsChannel
import com.android.tools.idea.sqlite.DatabaseInspectorProjectService
import com.android.tools.idea.sqlite.DatabaseInspectorTabProvider
import com.android.tools.idea.sqlite.settings.DatabaseInspectorSettings
import com.android.tools.idea.sqlite.FileDatabaseManager
import com.android.tools.idea.sqlite.OfflineModeManager
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadProgress
import com.android.tools.idea.sqlite.SchemaProvider
import com.android.tools.idea.sqlite.controllers.SqliteEvaluatorController.EvaluationParams
import com.android.tools.idea.sqlite.databaseConnection.jdbc.selectAllAndRowIdFromTable
import com.android.tools.idea.sqlite.databaseConnection.live.LiveInspectorException
import com.android.tools.idea.sqlite.localization.DatabaseInspectorBundle
import com.android.tools.idea.sqlite.model.DatabaseIdNotFoundException
import com.android.tools.idea.sqlite.model.DatabaseInspectorModel
import com.android.tools.idea.sqlite.model.ExportDialogParams
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.LiveSqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteSchema
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteTable
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.model.getAllDatabaseIds
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.android.tools.idea.sqlite.repository.DatabaseRepository
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
import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import icons.StudioIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

/**
 * Implementation of the application logic related to viewing/editing sqlite databases.
 */
class DatabaseInspectorControllerImpl(
  private val project: Project,
  private val model: DatabaseInspectorModel,
  private val databaseRepository: DatabaseRepository,
  private val viewFactory: DatabaseInspectorViewsFactory,
  private val fileDatabaseManager: FileDatabaseManager,
  private val offlineModeManager: OfflineModeManager,
  private val edtExecutor: Executor,
  private val taskExecutor: Executor
) : DatabaseInspectorController {

  private val uiDispatcher = edtExecutor.asCoroutineDispatcher()
  private val workDispatcher = taskExecutor.asCoroutineDispatcher()
  private val projectScope = AndroidCoroutineScope(project, uiDispatcher)

  private val view = viewFactory.createDatabaseInspectorView(project)
  private val tabsToRestore = mutableListOf<TabDescription>()

  // Job used to keep track of entering offline mode coroutine. Canceled this job to cancel offline mode.
  var downloadAndOpenOfflineDatabasesJob: Job? = null
    private set
    @TestOnly get

  /**
   * Controllers for all open tabs, keyed by id.
   *
   * <p>Multiple tables can be open at the same time in different tabs.
   * This map keeps track of corresponding controllers.
   */
  private val resultSetControllers = mutableMapOf<TabId, DatabaseInspectorController.TabController>()

  private val sqliteViewListener = SqliteViewListenerImpl()

  private var databaseInspectorClientCommandsChannel: DatabaseInspectorClientCommandsChannel? = null

  private var appInspectionIdeServices: AppInspectionIdeServices? = null
  private var processDescriptor: ProcessDescriptor? = null
  private var appPackageName: String? = null

  private var evaluatorTabCount = 0
  private var keepConnectionsOpen = false
  set(value) {
    databaseInspectorClientCommandsChannel?.keepConnectionsOpen(value)?.transformNullable(edtExecutor) {
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

      // enable refresh button if at least one live db is open
      val hasLiveDb = openDatabaseIds.any { it is SqliteDatabaseId.LiveSqliteDatabaseId }
      view.setRefreshButtonState(hasLiveDb)

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

  override suspend fun addSqliteDatabase(databaseId: SqliteDatabaseId) = withContext(uiDispatcher) {
    val schema = readDatabaseSchema(databaseId) ?: return@withContext
    addNewDatabase(databaseId, schema)
  }

  override suspend fun runSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement) = withContext(uiDispatcher) {
    openNewEvaluatorTab().showAndExecuteSqlStatement(databaseId, sqliteStatement).await()
  }

  override suspend fun closeDatabase(databaseId: SqliteDatabaseId): Unit = withContext(uiDispatcher) {
    val openDatabases = model.getOpenDatabaseIds()
    val tabsToClose = if (openDatabases.size == 1 && openDatabases.first() == databaseId) {
      // close all tabs (AdHoc and Table tabs) if we're closing the last open database
      // the call to `toMap` is to pass a copy of this collection, to avoid concurrent modification exceptions later on
      resultSetControllers.toMap()
    }
    else {
      // only close tabs associated with this database
      resultSetControllers.filterKeys { it is TabId.TableTab && it.databaseId == databaseId }
    }

    tabsToClose.forEach { closeTab(it.key) }
    saveTabsToRestore(tabsToClose)

    model.removeDatabaseSchema(databaseId)
    databaseRepository.closeDatabase(databaseId)

    // if the db is file-based we need to delete the files from the user's machine
    if (databaseId is SqliteDatabaseId.FileSqliteDatabaseId) {
      fileDatabaseManager.cleanUp(databaseId.databaseFileData)
    }

    return@withContext
  }

  @UiThread
  override fun showError(message: String, throwable: Throwable?) {
    view.reportError(message, throwable)
  }

  override suspend fun startAppInspectionSession(
    clientCommandsChannel: DatabaseInspectorClientCommandsChannel,
    appInspectionIdeServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    appPackageName: String?,
  ) {
    // cancel offline mode
    withContext(workDispatcher) { downloadAndOpenOfflineDatabasesJob?.cancelAndJoin() }

    this.databaseInspectorClientCommandsChannel = clientCommandsChannel
    clientCommandsChannel.keepConnectionsOpen(keepConnectionsOpen)

    this.appInspectionIdeServices = appInspectionIdeServices
    this.processDescriptor = processDescriptor
    this.appPackageName = appPackageName
  }

  // TODO(161081452): move appPackageName and processDescriptor to OfflineModeManager
  override fun stopAppInspectionSession(appPackageName: String?, processDescriptor: ProcessDescriptor) {
    databaseInspectorClientCommandsChannel = null
    this.processDescriptor = null
    this.appPackageName = null

    if (DatabaseInspectorSettings.getInstance().isOfflineModeEnabled) {
      enterOfflineMode(model.getAllDatabaseIds(), appPackageName, processDescriptor)
    }
  }

  /**
   * Download files for live dbs and open relative database in database inspector.
   * To cancel this operation users should cancel [downloadAndOpenOfflineDatabasesJob].
   */
  private fun enterOfflineMode(databasesToDownload: List<SqliteDatabaseId>, appPackageName: String?, processDescriptor: ProcessDescriptor) {
    val isDatabaseInspectorSelected = appInspectionIdeServices?.isTabSelected(DatabaseInspectorTabProvider.DATABASE_INSPECTOR_ID) ?: false
    // Don't enter offline mode if DBI is not currently being used.
    // This prevents downloading files for no reason but also prevents the offline mode permissions
    // dialog to show up when the user does not expect it.
    if (!isDatabaseInspectorSelected) {
      return
    }

    downloadAndOpenOfflineDatabasesJob = projectScope.launch {
      // metrics
      val stopwatch = Stopwatch.createStarted()
      var totalSizeDownloaded = 0L

      val flow = offlineModeManager.downloadFiles(
        databasesToDownload,
        processDescriptor,
        appPackageName,
      ) { message, throwable ->
        view.reportError(message, throwable)
      }

      val openDatabaseFlow = flow.onEach {
        when (it.downloadState) {
          OfflineModeManager.DownloadState.IN_PROGRESS -> { view.showEnterOfflineModePanel(it.filesDownloaded.size, it.totalFiles) }
          OfflineModeManager.DownloadState.COMPLETED -> {
            if (it.filesDownloaded.isEmpty()) {
              view.showOfflineModeUnavailablePanel()
            }
            else {
              it.filesDownloaded.forEach { databaseFileData ->
                totalSizeDownloaded += databaseFileData.mainFile.length + databaseFileData.walFiles.map { file -> file.length }.sum()

                // we open dbs only after all downloads are completed because if the user opens a tab before all downloads are done,
                // they would hide the download progress
                // TODO(b/168969287)
                // TODO(b/169319781) we shouldn't call DatabaseInspectorProjectService from here.
                DatabaseInspectorProjectService.getInstance(project).openSqliteDatabase(databaseFileData).await()
              }
            }
          }
        }
      }

      try {
        openDatabaseFlow.collect()
      } catch (e: CancellationException) {
        view.showOfflineModeUnavailablePanel()
      } finally {
        // metrics
        stopwatch.stop()
        val offlineMetadata = AppInspectionEvent.DatabaseInspectorEvent.OfflineModeMetadata
          .newBuilder()
          .setTotalDownloadSizeBytes(totalSizeDownloaded)
          .setTotalDownloadTimeMs(stopwatch.elapsed(TimeUnit.MILLISECONDS).toInt())
          .build()
        databaseInspectorAnalyticsTracker.trackOfflineModeEntered(offlineMetadata)
      }
    }
  }

  override suspend fun databasePossiblyChanged() = withContext(uiDispatcher) {
    // update schemas
    model.getOpenDatabaseIds().forEach { updateDatabaseSchema(it) }
    // update tabs
    resultSetControllers.values.forEach { it.notifyDataMightBeStale() }
  }

  override fun dispose(): Unit = invokeAndWaitIfNeeded {
    view.removeListener(sqliteViewListener)
    model.removeListener(modelListener)
    projectScope.launch { databaseRepository.clear() }

    // create a new list to avoid concurrent modification from `closeTab`
    resultSetControllers.keys.toList().forEach { closeTab(it) }
  }

  private suspend fun readDatabaseSchema(databaseId: SqliteDatabaseId): SqliteSchema? {
    return try {
      val schema = databaseRepository.fetchSchema(databaseId)
      filterSqliteSchema(schema)
    }
    catch (e: LiveInspectorException) {
      null
    }
    catch (e: AppInspectionConnectionException) {
      null
    }
    catch (e: DatabaseIdNotFoundException) {
      null
    }
    catch (e: Exception) {
      withContext(uiDispatcher) { view.reportError("Error reading Sqlite database", e) }
      throw e
    }
  }

  private fun addNewDatabase(databaseId: SqliteDatabaseId, sqliteSchema: SqliteSchema) {
    model.addDatabaseSchema(databaseId, sqliteSchema)
    restoreTabs(databaseId, sqliteSchema)
  }

  /** Called each time a database is closed */
  private fun saveTabsToRestore(tabsToSave: Map<TabId, DatabaseInspectorController.TabController>) {
    val tabs = tabsToSave
      // filter out in-memory dbs
      .filter {
        when (val tabId = it.key) {
          is TabId.TableTab -> !tabId.databaseId.isInMemoryDatabase()
          is TabId.AdHocQueryTab -> {
            val params = (it.value as SqliteEvaluatorController).saveEvaluationParams()
            !(params.databaseId?.isInMemoryDatabase() ?: false)
          }
        }
      }
      .mapNotNull {
        when (val tabId = it.key) {
          is TabId.TableTab -> TabDescription.Table(tabId.databaseId.path, tabId.tableName)
          is TabId.AdHocQueryTab -> {
            val params = (it.value as SqliteEvaluatorController).saveEvaluationParams()
            TabDescription.AdHocQuery(params.databaseId?.path, params.statementText)
          }
        }
      }

    tabsToRestore.addAll(tabs)
  }

  private fun restoreTabs(databaseId: SqliteDatabaseId, schema: SqliteSchema) {
    // open tabs associated with this database
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

    // open all tabs not associated with any database
    tabsToRestore.filter { it.databasePath == null }
      .also { tabsToRestore.removeAll(it) }
      .forEach { tabDescription ->
        val adHocTabDescription = tabDescription as TabDescription.AdHocQuery
        openNewEvaluatorTab(EvaluationParams(null, adHocTabDescription.query))
      }
  }

  private fun closeTab(tabId: TabId) {
    view.closeTab(tabId)
    val controller = resultSetControllers.remove(tabId)
    controller?.let(Disposer::dispose)
  }

  private suspend fun updateDatabaseSchema(databaseId: SqliteDatabaseId) {
    if (model.getCloseDatabaseIds().contains(databaseId)) return

    // TODO(b/154733971) this only works because the suspending function is called first, otherwise we have concurrency issues
    val newSchema = readDatabaseSchema(databaseId)
    if (newSchema == null) {
      closeDatabase(databaseId)
      return
    }

    val oldSchema = model.getDatabaseSchema(databaseId) ?: return
    withContext(uiDispatcher) {
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
          .mapIndexed { colIndex, sqliteColumn -> IndexedSqliteColumn(sqliteColumn, colIndex) }

        diffOperations.add(AddTable(indexedSqliteTable, indexedColumnsToAdd))
      }
      else if (oldTable != newTable) {
        val indexedColumnsToAdd = newTable.columns
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
      databaseRepository,
      sqliteEvaluatorView,
      { appInspectionIdeServices?.showNotification(it) },
      { closeTab(tabId) },
      ::showExportDialog,
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
      databaseId = databaseId,
      databaseRepository = databaseRepository,
      sqliteStatement = createSqliteStatement(project, selectAllAndRowIdFromTable(table)),
      showExportDialog = ::showExportDialog,
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

  /** Removes tables we don't care about from the schema. */
  private fun filterSqliteSchema(schema: SqliteSchema): SqliteSchema {
    val filteredTables = schema.tables.filter { it.name != "android_metadata" && it.name != "sqlite_sequence" }
    return SqliteSchema(filteredTables)
  }

  private fun showExportDialog(exportDialogParams: ExportDialogParams) {
    val view = viewFactory.createExportToFileView(project, exportDialogParams, databaseInspectorAnalyticsTracker)
    val controller = ExportToFileController(
      project,
      projectScope,
      view,
      databaseRepository,
      downloadDatabase = { id, onError -> processDescriptor?.let { procDesc -> downloadDatabase(id, onError, procDesc) } ?: emptyFlow() },
      deleteDatabase = { fileDatabaseManager.cleanUp(it) },
      acquireDatabaseLock = { databaseInspectorClientCommandsChannel?.acquireDatabaseLock(it)?.await() },
      releaseDatabaseLock = { databaseInspectorClientCommandsChannel?.releaseDatabaseLock(it)?.await() },
      taskExecutor = taskExecutor,
      edtExecutor = edtExecutor,
      notifyExportInProgress = { job -> viewFactory.createExportInProgressView(project, job, taskExecutor.asCoroutineDispatcher()).show() },
      notifyExportComplete = { request ->
        appInspectionIdeServices?.showNotification( // TODO(161081452):  replace with a Toast
          title = DatabaseInspectorBundle.message("export.notification.success.title"),
          content = when (RevealFileAction.isSupported()) {
            true -> DatabaseInspectorBundle.message("export.notification.success.message.reveal", RevealFileAction.getActionName())
            else -> ""
          },
          hyperlinkClicked = { RevealFileAction.openFile(request.dstPath) }
        )
      },
      notifyExportError = { _, throwable ->
        if (throwable is CancellationException) return@ExportToFileController // normal cancellation of a coroutine as per Kotlin spec
        appInspectionIdeServices?.showNotification(
          title = DatabaseInspectorBundle.message("export.notification.error.title"),
          content = throwable?.message ?: throwable?.toString() ?: "",
          severity = Severity.ERROR
        )
      }
    )
    controller.setUp()
    controller.showView()
    Disposer.register(project, controller)
  }

  private fun downloadDatabase(
    databaseId: LiveSqliteDatabaseId,
    onError: (String, Throwable?) -> Unit,
    processDescriptor: ProcessDescriptor
  ): Flow<DownloadProgress> {
    return offlineModeManager.downloadFiles(
      listOf(databaseId),
      processDescriptor,
      appPackageName,
      onError
    )
  }

  private inner class SqliteViewListenerImpl : DatabaseInspectorView.Listener {
    override fun tableNodeActionInvoked(databaseId: SqliteDatabaseId, table: SqliteTable) {
      val connectivityState = when (databaseId) {
        is SqliteDatabaseId.FileSqliteDatabaseId -> AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_OFFLINE
        is SqliteDatabaseId.LiveSqliteDatabaseId -> AppInspectionEvent.DatabaseInspectorEvent.ConnectivityState.CONNECTIVITY_ONLINE
      }

      databaseInspectorAnalyticsTracker.trackStatementExecuted(
        connectivityState,
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
      projectScope.launch {
        model.getOpenDatabaseIds().forEach { updateDatabaseSchema(it) }
      }
    }

    override fun toggleKeepConnectionOpenActionInvoked() {
      keepConnectionsOpen = !keepConnectionsOpen
    }

    override fun cancelOfflineModeInvoked() {
      databaseInspectorAnalyticsTracker.trackEnterOfflineModeUserCanceled()
      projectScope.launch { downloadAndOpenOfflineDatabasesJob?.cancelAndJoin() }
    }

    override fun showExportToFileDialogInvoked(exportDialogParams: ExportDialogParams) = showExportDialog(exportDialogParams)
  }

  inner class SqliteEvaluatorControllerListenerImpl : SqliteEvaluatorController.Listener {
    override fun onSqliteStatementExecuted(databaseId: SqliteDatabaseId) {
      projectScope.launch {
        updateDatabaseSchema(databaseId)
      }
    }
  }

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
   * Adds a database that is immediately ready
   */
  @AnyThread
  suspend fun addSqliteDatabase(databaseId: SqliteDatabaseId)

  @AnyThread
  suspend fun runSqlStatement(databaseId: SqliteDatabaseId, sqliteStatement: SqliteStatement)

  @AnyThread
  suspend fun closeDatabase(databaseId: SqliteDatabaseId)

  /**
   * Updates schema of all open databases and notifies each tab that its data might be stale.
   *
   * This method is called when a `DatabasePossiblyChanged` event is received from the the on-device inspector
   * which tells us that the data in a database might have changed (schema, tables or both).
   */
  @AnyThread
  suspend fun databasePossiblyChanged()

  /**
   * Shows the error in the view.
   */
  @UiThread
  fun showError(message: String, throwable: Throwable?)

  @UiThread
  suspend fun startAppInspectionSession(
    clientCommandsChannel: DatabaseInspectorClientCommandsChannel,
    appInspectionIdeServices: AppInspectionIdeServices,
    processDescriptor: ProcessDescriptor,
    appPackageName: String?,
    )

  @UiThread
  fun stopAppInspectionSession(appPackageName: String?, processDescriptor: ProcessDescriptor)

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
}

sealed class TabId {
  data class TableTab(val databaseId: SqliteDatabaseId, val tableName: String) : TabId()
  data class AdHocQueryTab(val tabId: Int) : TabId()
}