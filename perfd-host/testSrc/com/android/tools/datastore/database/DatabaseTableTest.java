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
package com.android.tools.datastore.database;

import com.android.tools.datastore.DataStoreDatabase;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatabaseTableTest {
  private static final int TEST_THREAD_COUNT = 100;
  private static final int TEST_DATA_COUNT = 100;
  private File myDbFile;
  private ThreadTestTable myTable;
  private DataStoreDatabase myDatabase;

  public enum ThreadTableStatement {
    INSERT_DATA,
    READ_DATA
  }

  @Before
  public void setUp() throws Exception {
    myDbFile = File.createTempFile("DatabaseTableTest", "sql");
    myDatabase = new DataStoreDatabase(myDbFile.getAbsolutePath(), DataStoreDatabase.Characteristic.DURABLE);
    myTable = new ThreadTestTable();
    myTable.initialize(myDatabase.getConnection());
  }

  @After
  public void tearDown() throws Exception {
    if (!myDatabase.getConnection().isClosed()) {
      myDatabase.disconnect();
    }
    myDbFile.delete();
  }

  @Test
  public void testConnectionClosed() throws Exception {
    myDatabase.disconnect();
    assertTrue(myTable.isClosed());
  }

  @Test
  public void testEmptyResultSetOnClosedConnection() throws Exception {
    myDatabase.getConnection().close();
    ResultSet resultSet = myTable.readDataRaw();
    assertThat(resultSet).isInstanceOf(EmptyResultSet.class);
  }

  @Test
  public void testThreadMultiThreadExecute() throws Exception {
    // Insert some fake data
    for (int i = 0; i < TEST_DATA_COUNT; i++) {
      myTable.insertData(i);
    }

    // Spin up a bunch of threads to create a contention
    TableTest[] tableRunnable = new TableTest[TEST_THREAD_COUNT];
    CountDownLatch ensureThreadsTickOnce = new CountDownLatch(TEST_THREAD_COUNT);
    Thread[] threads = new Thread[TEST_THREAD_COUNT];
    for (int i = 0; i < TEST_THREAD_COUNT; i++) {
      tableRunnable[i] = new TableTest(ensureThreadsTickOnce);
      threads[i] = new Thread(tableRunnable[i]);
      threads[i].start();
    }

    while (ensureThreadsTickOnce.getCount() > 0) {
      Thread.yield();
    }

    // Verify we did not hit any exceptions
    for (TableTest test : tableRunnable) {
      assertFalse(test.hasException());
      test.stop();
    }

    // Wait for each thread to finish before exiting the test.
    for (Thread thread : threads) {
      thread.join();
    }
  }

  /**
   * Runnable class that handles querying the database as fast as possible.
   */
  private class TableTest implements Runnable {
    private boolean myHasException = false;
    private boolean myStop = false;
    private CountDownLatch myLatch;

    public boolean hasException() {
      return myHasException;
    }

    public void stop() {
      myStop = true;
    }

    public TableTest(CountDownLatch latch) {
      myLatch = latch;
    }

    @Override
    public void run() {
      boolean ticked = false;
      try {
        do {
          ResultSet rs = myTable.readDataRaw();
          while (rs.next()) ;

          //Capture that this thread has captured the data.
          if (!ticked) {
            myLatch.countDown();
            ticked = true;
          }
        }
        while (!myStop);
      }
      catch (SQLException ex) {
        if (!ticked) {
          myLatch.countDown();
        }
        myHasException = true;
      }
    }
  }

  /**
   * Setup a simple Datastore table to validate operations on.
   */
  private class ThreadTestTable extends DataStoreTable<ThreadTableStatement> {

    @Override
    public void initialize(@NotNull Connection connection) {
      super.initialize(connection);
      try {
        createTable("Thread_Table", "DataColumn INTEGER");
      }
      catch (SQLException ex) {
        // Failed to create table.
      }
    }

    @Override
    public void prepareStatements() {
      try {
        createStatement(ThreadTableStatement.INSERT_DATA, "INSERT INTO Thread_Table (DataColumn) VALUES (?)");
        createStatement(ThreadTableStatement.READ_DATA, "SELECT DataColumn FROM Thread_Table");
      }
      catch (SQLException ex) {
        // Failed to create statement
      }
    }

    public void insertData(int... someData) {
      for (int i = 0; i < someData.length; i++) {
        execute(ThreadTableStatement.INSERT_DATA, someData[i]);
      }
    }

    public ResultSet readDataRaw() throws SQLException {
      return executeQuery(ThreadTableStatement.READ_DATA);
    }
  }
}
