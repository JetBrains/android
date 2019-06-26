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
package com.android.tools.idea.room.generators;

import static com.android.tools.idea.room.generators.TestUtils.createDatabaseBundle;
import static com.android.tools.idea.room.generators.TestUtils.createEntityBundle;
import static com.android.tools.idea.room.generators.TestUtils.createFieldBundle;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.room.bundle.DatabaseBundle;
import com.android.tools.idea.room.bundle.EntityBundle;
import com.android.tools.idea.room.bundle.FieldBundle;
import com.android.tools.idea.room.update.DatabaseUpdate;
import com.android.tools.idea.room.update.EntityUpdate;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class SQLStatementsGeneratorTest {
  private EntityBundle entity1;
  private EntityBundle entity2;
  private EntityBundle entity3;
  private EntityBundle entity4;
  private DatabaseBundle db1;
  private DatabaseBundle db2;
  private DatabaseBundle db3;
  private DatabaseBundle db4;

  @Before
  public void setUp() {
    FieldBundle field1 =  createFieldBundle("column1", "VARCHAR", null);
    FieldBundle field2 = createFieldBundle("column2", "VARCHAR", null);
    FieldBundle field3 = createFieldBundle("column3", "VARCHAR", null);
    FieldBundle field4 = createFieldBundle("column1", "CHAR", null);

    entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    entity2 = createEntityBundle("table2",  Arrays.asList(field1, field2, field3));
    entity3 = createEntityBundle("table3", Arrays.asList(field1, field3));
    entity4 = createEntityBundle("table1", Arrays.asList(field4, field2));

    db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2));
    db2 = createDatabaseBundle(2, Arrays.asList(entity1, entity2, entity3));
    db3 = createDatabaseBundle(3, Arrays.asList(entity1, entity3));
    db4 = createDatabaseBundle(4, Arrays.asList(entity4, entity2));
  }

  @Test
  public void testAddColumnUpdateStatement() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);
    String alterStatement = "ALTER TABLE table1 ADD COLUMN column3 VARCHAR;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(alterStatement);
  }

  @Test
  public void testDeleteColumnUpdateStatements() {
    EntityUpdate entityUpdate = new EntityUpdate(entity2, entity1);
    String createStatement = "CREATE TABLE table2_data$android.studio.tmp\n" +
                             "(\n" +
                             "\tcolumn1 VARCHAR,\n" +
                             "\tcolumn2 VARCHAR\n" +
                             ");";
    String insertStatement = "INSERT INTO table2_data$android.studio.tmp (column1, column2)\n" +
                             "\tSELECT column1, column2\n" +
                             "\tFROM table2;";
    String dropStatement = "DROP TABLE table2;";
    String renameStatement = "ALTER TABLE table2_data$android.studio.tmp RENAME TO table2;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      createStatement, insertStatement, dropStatement, renameStatement).inOrder();
  }

  @Test
  public void testAddAndDeleteColumnUpdateStatements() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity3);
    String createStatement = "CREATE TABLE table1_data$android.studio.tmp\n" +
                             "(\n" +
                             "\tcolumn1 VARCHAR,\n" +
                             "\tcolumn3 VARCHAR\n" +
                             ");";
    String insertStatement = "INSERT INTO table1_data$android.studio.tmp (column1)\n" +
                             "\tSELECT column1\n" +
                             "\tFROM table1;";
    String dropStatement = "DROP TABLE table1;";
    String renameStatement = "ALTER TABLE table1_data$android.studio.tmp RENAME TO table1;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      createStatement, insertStatement, dropStatement, renameStatement).inOrder();
  }

  @Test
  public void testModifyColumnUpdateStatements() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity4);
    String createStatement = "CREATE TABLE table1_data$android.studio.tmp\n" +
                             "(\n" +
                             "\tcolumn2 VARCHAR,\n" +
                             "\tcolumn1 CHAR\n" +
                             ");";
    String insertStatement = "INSERT INTO table1_data$android.studio.tmp (column2, column1)\n" +
                             "\tSELECT column2, column1\n" +
                             "\tFROM table1;";
    String dropStatement = "DROP TABLE table1;";
    String renameStatement = "ALTER TABLE table1_data$android.studio.tmp RENAME TO table1;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      createStatement, insertStatement, dropStatement, renameStatement).inOrder();
  }

  @Test
  public void testAddEntityUpdateStatement() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);
    String createStatement = "CREATE TABLE table3\n" +
                             "(\n" +
                             "\tcolumn1 VARCHAR,\n" +
                             "\tcolumn3 VARCHAR\n" +
                             ");";

    assertThat(SQLStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(createStatement);
  }

  @Test
  public void testDeleteEntityUpdateStatement() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db2, db3);
    String dropStatement = "DROP TABLE table2;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(dropStatement);
  }

  @Test
  public void testModifyEntityUpdateStatement() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db4);
    String createStatement = "CREATE TABLE table1_data$android.studio.tmp\n" +
                             "(\n" +
                             "\tcolumn2 VARCHAR,\n" +
                             "\tcolumn1 CHAR\n" +
                             ");";
    String insertStatement = "INSERT INTO table1_data$android.studio.tmp (column2, column1)\n" +
                             "\tSELECT column2, column1\n" +
                             "\tFROM table1;";
    String dropStatement = "DROP TABLE table1;";
    String renameStatement = "ALTER TABLE table1_data$android.studio.tmp RENAME TO table1;";

    assertThat(SQLStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(
      createStatement, insertStatement, dropStatement, renameStatement).inOrder();
  }
}
