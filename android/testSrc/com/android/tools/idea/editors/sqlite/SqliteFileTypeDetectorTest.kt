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

import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

class SqliteFileTypeDetectorTest : AndroidTestCase() {
  private var mySqliteUtil: SqliteTestUtil? = null
  private var myPreviousEnabled: Boolean = false

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    mySqliteUtil = SqliteTestUtil(myFixture.tempDirFixture)
    myPreviousEnabled = SqliteViewer.enableFeature(true)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    try {
      SqliteViewer.enableFeature(myPreviousEnabled)
    }
    finally {
      super.tearDown()
    }
  }

  @Throws(Exception::class)
  fun testSqliteFileDetection() {
    // Prepare
    val file = mySqliteUtil!!.createTempSqliteDatabase()
    val detector = SqliteFileTypeDetector()
    val byteSequence = mySqliteUtil!!.createByteSequence(file, 4096)

    // Act
    val fileType = detector.detect(file, byteSequence, null)

    // Assert
    assertThat(fileType).isNotNull()
  }

  @Throws(Exception::class)
  fun testSqliteFileDetectionShortSequence() {
    // Prepare
    val file = mySqliteUtil!!.createTempSqliteDatabase()
    val detector = SqliteFileTypeDetector()
    // Note: 10 bytes is smaller than the Sqlite header
    val byteSequence = mySqliteUtil!!.createByteSequence(file, 10)

    // Act
    val fileType = detector.detect(file, byteSequence, null)

    // Assert
    assertThat(fileType).isNull()
  }

  @Throws(Exception::class)
  fun testSqliteFileDetectionEmptyDatabase() {
    // Prepare
    val file = mySqliteUtil!!.createEmptyTempSqliteDatabase()
    val detector = SqliteFileTypeDetector()
    // Note: 10 bytes is smaller than the Sqlite header
    val byteSequence = mySqliteUtil!!.createByteSequence(file, 10)

    // Act
    val fileType = detector.detect(file, byteSequence, null)

    // Assert
    assertThat(fileType).isNull()
  }

  @Throws(Exception::class)
  fun testRandomBinaryFileDetection() {
    // Prepare
    val file = mySqliteUtil!!.createTempBinaryFile(30000)
    val detector = SqliteFileTypeDetector()
    val byteSequence = mySqliteUtil!!.createByteSequence(file, 4096)

    // Act
    val fileType = detector.detect(file, byteSequence, null)

    // Assert
    assertThat(fileType).isNull()
  }
}
