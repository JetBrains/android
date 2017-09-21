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

import com.android.tools.adtui.workbench.DetachedToolWindowManager
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.adtui.workbench.WorkBenchManager
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.android.ComponentStack
import org.mockito.Mock
import org.mockito.MockitoAnnotations.initMocks

class SqliteEditorProviderTest : LightPlatformTestCase() {
  private lateinit var sqliteUtil: SqliteTestUtil
  private var previouslyEnabled: Boolean = false
  @Mock
  private lateinit var myWorkBenchManager: WorkBenchManager
  @Mock
  private lateinit var myFloatingToolWindowManager: DetachedToolWindowManager
  private lateinit var myApplicationComponentStack: ComponentStack
  private lateinit var myProjectComponentStack: ComponentStack

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    initMocks(this)
    myApplicationComponentStack = ComponentStack(ApplicationManager.getApplication())
    myProjectComponentStack = ComponentStack(getProject())
    sqliteUtil = SqliteTestUtil(IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture())
    sqliteUtil.setUp()
    previouslyEnabled = SqliteViewer.enableFeature(true)

    registerApplicationComponent(WorkBenchManager::class.java, myWorkBenchManager)
    registerApplicationComponent(PropertiesComponent::class.java, PropertiesComponentMock())
    registerProjectComponentImplementation(DetachedToolWindowManager::class.java, myFloatingToolWindowManager)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      sqliteUtil.tearDown()
      SqliteViewer.enableFeature(previouslyEnabled)
      myApplicationComponentStack.restoreComponents()
      myProjectComponentStack.restoreComponents()
    }
    finally {
      super.tearDown()
    }
  }

  private fun <T> registerApplicationComponent(key: Class<T>, instance: T) {
    myApplicationComponentStack.registerComponentInstance(key, instance)
  }

  private fun <T> registerProjectComponentImplementation(key: Class<T>, instance: T) {
    (getProject() as ComponentManagerImpl).registerComponentImplementation(key, key)
    myProjectComponentStack.registerComponentInstance(key, instance)
  }

  @Throws(Exception::class)
  fun testShouldAcceptSqliteFiles() {
    // Prepare
    val file = sqliteUtil.createTempSqliteDatabase()
    val provider = SqliteEditorProvider()

    // Act
    // Note: This relies on the sqlite file detector being correctly registered as a
    // global file type detector.
    val accepts = provider.accept(getProject(), file)

    // Assert
    assertThat(accepts).isTrue()
  }

  fun testShouldAcceptSqliteEmptyDatabase() {
    // Prepare
    val file = sqliteUtil.createEmptyTempSqliteDatabase()
    val provider = SqliteEditorProvider()

    // Act
    // Note: This relies on the sqlite file detector being correctly registered as a
    // global file type detector.
    val accepts = provider.accept(getProject(), file)

    // Assert
    assertThat(accepts).isTrue()
  }

  @Throws(Exception::class)
  fun testShouldNotAcceptFilesIfFeatureDisabled() {
    // Prepare
    SqliteViewer.enableFeature(false)
    val file = sqliteUtil.createTempSqliteDatabase()
    val provider = SqliteEditorProvider()

    // Act
    val accepts = provider.accept(getProject(), file)

    // Assert
    assertThat(accepts).isFalse()
  }

  @Throws(Exception::class)
  fun testShouldNotAcceptBinaryFiles() {
    // Prepare
    SqliteViewer.enableFeature(false)
    val file = sqliteUtil.createTempBinaryFile(1000)
    val provider = SqliteEditorProvider()

    // Act
    val accepts = provider.accept(getProject(), file)

    // Assert
    assertThat(accepts).isFalse()
  }

  @Throws(Exception::class)
  fun testShouldOpenSqliteEditor() {
    // Prepare
    val file = sqliteUtil.createTempSqliteDatabase()
    val provider = SqliteEditorProvider()

    // Act
    val editor = provider.createEditor(getProject(), file)
    Disposer.register(testRootDisposable, editor)

    // Assert
    assertThat(editor).isNotNull()
    assertThat(editor).isInstanceOf(SqliteEditor::class.java)
    assertThat(editor.isValid).isTrue()
    assertThat(editor.isModified).isFalse()
    assertThat(editor.component).isInstanceOf(WorkBench::class.java)
  }
}
