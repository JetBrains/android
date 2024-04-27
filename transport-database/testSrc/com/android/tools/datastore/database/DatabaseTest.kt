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

import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.FakeLogService
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.sql.SQLException
import java.util.*
import java.util.function.Consumer

/**
 * This is a base datastore test class. This class setups a table for each of the test as well as creates
 * two default test to check each table for errors.
 *
 * Note: This class is marked ignore so it is not picked up by JUnit Runner. Abstract classes can not be initialized.
 */
@Ignore
abstract class DatabaseTest<T : DataStoreTable<*>> {
  protected lateinit var table: T
  private lateinit var dbFile: File
  private lateinit var database: DataStoreDatabase

  @Before
  @Throws(Exception::class)
  open fun before() {
    dbFile = File.createTempFile("DatabaseTableTest", "mysql")
    dbFile.deleteOnExit()
    database = DataStoreDatabase(dbFile.absolutePath, DataStoreDatabase.Characteristic.DURABLE, FakeLogService())
    table = createTable()
    table.initialize(database.connection)
  }

  @After
  fun after() {
    database.disconnect()
  }

  @Test
  @Throws(SQLException::class)
  @Suppress("MemberVisibilityCanBePrivate") // Needs to be public for JUnit
  fun closedConnectionIsHandled() {
    var errorThrown = false
    val db = DataStoreDatabase(dbFile.absolutePath, DataStoreDatabase.Characteristic.DURABLE, FakeLogService())
    DataStoreTable.addDataStoreErrorCallback { _ -> errorThrown = true }
    db.connection.close()
    // Throws an exception due to the connection being closed on initialization
    val table = createTable()
    table.initialize(db.connection)
    assertThat(errorThrown).isTrue()

    // Should return with no error because of connection closed check.
    errorThrown = false
    val methodCalls = getTableQueryMethodsForVerification()
    for (method in methodCalls) {
      method.accept(table)
      assertThat(errorThrown).isFalse()
    }
    // We validate that the number of public methods for a given table have a handler for testing
    // the closed. If a new public method is added for access to a specific table
    // this new method should be added to the list returned by {@link ::getTableQueryMethodsForVerification}
    // If this function fails and no new methods have been added it is because one of the functions does not
    // properly handle the database being closed when that method is called.
    assertThat(methodCalls).hasSize(getMethodCount(table))
  }

  @Test
  @Throws(SQLException::class)
  @Suppress("MemberVisibilityCanBePrivate") // Needs to be public for JUnit
  fun errorIsHandled() {
    var throwsError = false
    DataStoreTable.addDataStoreErrorCallback { _ -> throwsError = true }

    val table = createTable()
    table.initialize(DatabaseTestConnection(database.connection))
    // Should return with error because the statement is throwing an error.
    val methodCalls = getTableQueryMethodsForVerification()
    for (i in methodCalls.indices) {
      methodCalls[i].accept(table)
      assertWithMessage("Failed to handle error on call #$i").that(throwsError).isTrue()
      throwsError = false
    }
    // We validate that the number of public methods for a given table have a handler for testing
    // error cases. If a new public method is added for access to a specific table
    // this new method should be added to the list returned by {@link ::getTableQueryMethodsForVerification}
    // If this function fails and no new methods have been added it is because one of the functions does not
    // properly handle sql exceptions.
    assertThat(methodCalls).hasSize(getMethodCount(table))
  }

  /**
   * Gets the method count we expect to test for each table. This allows the test to validate
   * that we are not missing any calls when checking for sql connection closed, or an exception being thrown.
   */
  protected fun getMethodCount(table: T): Int {
    val baseClassMethods = HashSet<String>()
    var count = 0
    for (method in table.javaClass.superclass.methods) {
      baseClassMethods.add(method.name)
    }
    return table.javaClass.methods.count { Modifier.isPublic(it.modifiers) && !baseClassMethods.contains(it.name) }
  }

  /**
   * Create a table used by the [errorIsHandled] and [closedConnectionIsHandled] test.
   */
  protected abstract fun createTable(): T

  /**
   * This method should return a list of calls into a specified table.
   * Each call will be called 2 times. In one test the sql connection will be closed before calling
   * each of the consumers. In the other test an exception will be thrown while the function is calling
   * execute on the prepared statement.
   * The number of consumers returned should match all public functions that make sql queries for a specific
   * table.
   */
  protected abstract fun getTableQueryMethodsForVerification(): List<Consumer<T>>
}
