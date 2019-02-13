// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.datastore.database

import com.android.tools.datastore.DataStoreDatabase
import com.android.tools.datastore.FakeLogService
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class DataStoreDatabaseTest {

  private val myDatabaseFile = File.createTempFile("fakedbfile", "sql")

  @Test
  fun testDatabaseCreatesDBFile() {
    myDatabaseFile.delete()
    val db = DataStoreDatabase(myDatabaseFile.absolutePath, DataStoreDatabase.Characteristic.DURABLE, FakeLogService())
    db.disconnect()
    assertThat(myDatabaseFile.exists()).isTrue()
  }

  @Test
  fun testDatabaseDeletesExisitingFileOnLoad() {
    val outputStream = BufferedOutputStream(FileOutputStream(myDatabaseFile))
    outputStream.write(ByteArray(1024))
    outputStream.close()
    assertThat(myDatabaseFile.length()).isEqualTo(1024)
    val db = DataStoreDatabase(myDatabaseFile.absolutePath, DataStoreDatabase.Characteristic.DURABLE, FakeLogService())
    assertThat(myDatabaseFile.length()).isEqualTo(0)
    db.disconnect()
    assertThat(myDatabaseFile.exists()).isTrue()
  }

  @Test
  fun testConnectionIsOpen() {
    // Verify persistent database
    var db = DataStoreDatabase(myDatabaseFile.absolutePath, DataStoreDatabase.Characteristic.DURABLE, FakeLogService())
    assertThat(db.connection.isClosed).isFalse()
    assertThat(db.connection.autoCommit).isFalse()
    assertThat(db.connection.metaData.url).matches("jdbc:sqlite:${myDatabaseFile.absolutePath}")
    db.disconnect()
    assertThat(db.connection.isClosed).isTrue()

    // Verify memory database.
    db = DataStoreDatabase(myDatabaseFile.absolutePath, DataStoreDatabase.Characteristic.PERFORMANT, FakeLogService())
    assertThat(db.connection.isClosed).isFalse()
    assertThat(db.connection.autoCommit).isFalse()
    assertThat(db.connection.metaData.url).matches("jdbc:sqlite::memory:")
    db.disconnect()
    assertThat(db.connection.isClosed).isTrue()
  }
}
