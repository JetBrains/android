/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.sqlite

import com.android.tools.idea.sqlite.controllers.SqliteController
import com.android.tools.idea.sqlite.jdbc.SqliteJdbcService
import com.android.tools.idea.sqlite.model.SqliteModel
import com.android.tools.idea.sqlite.ui.SqliteEditorViewFactoryImpl
import com.android.tools.idea.sqlite.ui.mainView.SqliteViewImpl
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Implementation of [FileEditor] for Sqlite files. The custom editor is GUI based, i.e. shows the list of tables, allows querying
 * the database, etc.
 *
 * @see SqliteEditorProvider
 */
class SqliteEditor(private val project: Project, private val sqliteFile: VirtualFile) : UserDataHolderBase(), FileEditor {
  private val model: SqliteModel = SqliteModel(sqliteFile)
  private val sqliteView: SqliteViewImpl = SqliteViewImpl(
    project, model, this)
  private val controller: SqliteController

  init {
    val service = SqliteJdbcService(sqliteFile, this, PooledThreadExecutor.INSTANCE)
    controller = SqliteController(
      this, SqliteEditorViewFactoryImpl.getInstance(), model,
      sqliteView, service,
      EdtExecutorService.getInstance(), PooledThreadExecutor.INSTANCE
    )
    controller.setUp()
  }

  override fun dispose() {}

  override fun getComponent(): JComponent = sqliteView.component

  override fun getPreferredFocusedComponent(): JComponent? = null

  override fun getName(): String = sqliteFile.name

  override fun setState(state: FileEditorState) {
    if (state is SqliteEditorState) {
      model.sqliteFileId = state.deviceFileId
    }
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState = SqliteEditorState(model.sqliteFileId)

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = sqliteFile.isValid && !project.isDisposed

  override fun selectNotify() {}

  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null
}