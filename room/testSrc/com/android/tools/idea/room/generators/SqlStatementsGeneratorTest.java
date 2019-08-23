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

import com.android.tools.idea.room.migrations.json.DatabaseBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.generators.SqlStatementsGenerator;
import com.android.tools.idea.room.migrations.json.ForeignKeyBundle;
import com.android.tools.idea.room.migrations.json.IndexBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.android.tools.idea.room.migrations.update.EntityUpdate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SqlStatementsGeneratorTest {
  private FieldBundle field1;
  private FieldBundle field2;
  private FieldBundle field3;
  private FieldBundle field4;

  @Before
  public void setUp() {
    field1 = createFieldBundle("column1", "TEXT", null);
    field2 = createFieldBundle("column2", "TEXT", null);
    field3 = createFieldBundle("column3", "TEXT", null);
    field4 = createFieldBundle("column1", "CHAR", null);
  }

  @Test
  public void testAddColumnUpdateStatement() {


    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table", Arrays.asList(field1, field2, field3));

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly("ALTER TABLE `table` ADD COLUMN column3 TEXT;");
  }

  @Test
  public void testDeleteColumnUpdateStatements() {
    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table", Arrays.asList(field1, field2, field3));

    EntityUpdate entityUpdate = new EntityUpdate(entity2, entity1);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      "CREATE TABLE table_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn1 TEXT,\n" +
      "\tcolumn2 TEXT,\n" +
      "\tPRIMARY KEY (column1)\n" +
      ");",
      "INSERT INTO table_data$android_studio_tmp (column1, column2)\n" +
      "\tSELECT column1, column2\n" +
      "\tFROM `table`;",
      "DROP TABLE `table`;",
      "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;").inOrder();
  }

  @Test
  public void testAddAndDeleteColumnUpdateStatements() {
    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table", Arrays.asList(field1, field3));

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      "CREATE TABLE table_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn1 TEXT,\n" +
      "\tcolumn3 TEXT,\n" +
      "\tPRIMARY KEY (column1)\n" +
      ");",
      "INSERT INTO table_data$android_studio_tmp (column1)\n" +
      "\tSELECT column1\n" +
      "\tFROM `table`;",
      "DROP TABLE `table`;",
      "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;").inOrder();
  }

  @Test
  public void testModifyColumnUpdateStatements() {
    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table", Arrays.asList(field4, field2));

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      "CREATE TABLE table_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn2 TEXT,\n" +
      "\tcolumn1 CHAR,\n" +
      "\tPRIMARY KEY (column1)\n" +
      ");",
      "INSERT INTO table_data$android_studio_tmp (column2, column1)\n" +
      "\tSELECT column2, column1\n" +
      "\tFROM `table`;",
      "DROP TABLE `table`;",
      "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;").inOrder();
  }

  @Test
  public void testAddEntityUpdateStatement() {
    EntityBundle entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table2", Arrays.asList(field1, field2, field3));
    EntityBundle entity3 = createEntityBundle("table3", Arrays.asList(field1, field3));

    DatabaseBundle db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2));
    DatabaseBundle db2 = createDatabaseBundle(2, Arrays.asList(entity1, entity2, entity3));

    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(
      "CREATE TABLE table3\n" +
      "(\n" +
      "\tcolumn1 TEXT,\n" +
      "\tcolumn3 TEXT,\n" +
      "\tPRIMARY KEY (column1)\n" +
      ");");
  }

  @Test
  public void testDeleteEntityUpdateStatement() {
    EntityBundle entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table2", Arrays.asList(field1, field2, field3));
    EntityBundle entity3 = createEntityBundle("table3", Arrays.asList(field1, field3));

    DatabaseBundle db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2, entity3));
    DatabaseBundle db2 = createDatabaseBundle(2, Arrays.asList(entity1, entity3));

    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly("DROP TABLE table2;");
  }

  @Test
  public void testModifyEntityUpdateStatement() {
    EntityBundle entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table2", Arrays.asList(field1, field2, field3));
    EntityBundle entity4 = createEntityBundle("table1", Arrays.asList(field4, field2));

    DatabaseBundle db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2));
    DatabaseBundle db2 = createDatabaseBundle(2, Arrays.asList(entity4, entity2));

    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(
      "CREATE TABLE table1_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn2 TEXT,\n" +
      "\tcolumn1 CHAR,\n" +
      "\tPRIMARY KEY (column1)\n" +
      ");",
      "INSERT INTO table1_data$android_studio_tmp (column2, column1)\n" +
      "\tSELECT column2, column1\n" +
      "\tFROM table1;",
      "DROP TABLE table1;",
      "ALTER TABLE table1_data$android_studio_tmp RENAME TO table1;").inOrder();
  }

  @Test
  public void testNotNull() {
    EntityBundle idOnly = createEntityBundle(
      "my_table",
      Collections.singletonList(
        new FieldBundle("id",
                        "id",
                        "INTEGER",
                        true,
                        null)));

    EntityBundle idAndName = createEntityBundle(
      "my_table",
      Arrays.asList(
        new FieldBundle("id",
                        "id",
                        "INTEGER",
                        true,
                        null),
        new FieldBundle("name",
                        "name",
                        "TEXT",
                        true,
                        null)));

    DatabaseUpdate databaseUpdate = new DatabaseUpdate(
      new DatabaseBundle(1, "hash", Collections.singletonList(idOnly), null, null),
      new DatabaseBundle(2, "hash", Collections.singletonList(idAndName), null, null));

    assertThat(SqlStatementsGenerator.getUpdateStatements(databaseUpdate))
      .containsExactly("ALTER TABLE my_table ADD COLUMN name TEXT NOT NULL;");
  }

  @Test
  public void testPrimaryKeyUpdate() {
    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2, field3));
    EntityBundle entity2 = new EntityBundle("table",
                                            "",
                                            Arrays.asList(field1, field2, field3),
                                            new PrimaryKeyBundle(false, Arrays.asList(field1.getColumnName(), field2.getColumnName())),
                                            null,
                                            null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      "CREATE TABLE table_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn1 TEXT,\n" +
      "\tcolumn2 TEXT,\n" +
      "\tcolumn3 TEXT,\n" +
      "\tPRIMARY KEY (column1, column2)\n" +
      ");",
      "INSERT INTO table_data$android_studio_tmp (column1, column2, column3)\n" +
      "\tSELECT column1, column2, column3\n" +
      "\tFROM `table`;",
      "DROP TABLE `table`;",
      "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;").inOrder();
  }

  @Test
  public void testPrimaryKeyAutoIncrement() {
    FieldBundle autoIncrementField = createFieldBundle("column1", "INTEGER", null);
    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(autoIncrementField, field2, field3));
    EntityBundle entity2 = new EntityBundle("table",
                                            "",
                                            Arrays.asList(autoIncrementField, field2, field3),
                                            new PrimaryKeyBundle(true, Collections.singletonList(field1.getColumnName())),
                                            null,
                                            null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly(
      "CREATE TABLE table_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn1 INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
      "\tcolumn2 TEXT,\n" +
      "\tcolumn3 TEXT\n" +
      ");",
      "INSERT INTO table_data$android_studio_tmp (column1, column2, column3)\n" +
      "\tSELECT column1, column2, column3\n" +
      "\tFROM `table`;",
      "DROP TABLE `table`;",
      "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;").inOrder();
  }

  @Test
  public void testForeignKeysUpdate() {
    List<ForeignKeyBundle> foreignKeys =
      Collections.singletonList(new ForeignKeyBundle("table1", "", "", Collections.singletonList(field1.getColumnName()),
                                                     Collections.singletonList(field1.getColumnName())));
    EntityBundle entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table2", Arrays.asList(field1, field2, field3));
    EntityBundle entity3 = new EntityBundle("table2",
                                            "",
                                            Arrays.asList(field1, field2, field3),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            null,
                                            foreignKeys);

    DatabaseBundle db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2));
    DatabaseBundle db2 = createDatabaseBundle(2, Arrays.asList(entity1, entity3));

    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(databaseUpdate)).containsExactly(
      "CREATE TABLE table2_data$android_studio_tmp\n" +
      "(\n" +
      "\tcolumn1 TEXT,\n" +
      "\tcolumn2 TEXT,\n" +
      "\tcolumn3 TEXT,\n" +
      "\tPRIMARY KEY (column1),\n" +
      "\tFOREIGN KEY (column1) REFERENCES table1 (column1)\n" +
      ");",
      "INSERT INTO table2_data$android_studio_tmp (column1, column2, column3)\n" +
      "\tSELECT column1, column2, column3\n" +
      "\tFROM table2;",
      "DROP TABLE table2;",
      "ALTER TABLE table2_data$android_studio_tmp RENAME TO table2;",
      "PRAGMA foreign_key_check;").inOrder();
  }

  @Test
  public void testDropIndexUpdate() {
    IndexBundle indexBundle = new IndexBundle("index_table_column1", false, Collections.singletonList("column1"), "");

    EntityBundle entity1 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(indexBundle), null);
    EntityBundle entity2 = createEntityBundle("table", Arrays.asList(field1, field2));

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate)).containsExactly("DROP INDEX index_table_column1;").inOrder();
  }

  @Test
  public void testAddIndexUpdate() {
    IndexBundle indexBundle = new IndexBundle("index_table_column1", false, Collections.singletonList("column1"), "");

    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(indexBundle), null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate))
      .containsExactly("CREATE INDEX index_table_column1 ON `table` (column1);").inOrder();
  }

  @Test
  public void testAddUniqueIndexUpdate() {
    IndexBundle indexBundle = new IndexBundle("index_table_column1", true, Collections.singletonList("column1"), "");

    EntityBundle entity1 = createEntityBundle("table", Arrays.asList(field1, field2));
    EntityBundle entity2 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(indexBundle), null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate))
      .containsExactly("CREATE UNIQUE INDEX index_table_column1 ON `table` (column1);").inOrder();
  }

  @Test
  public void testRenameIndexUpdate() {
    IndexBundle index1 = new IndexBundle("index_table_column1", false, Collections.singletonList("column1"), "");
    IndexBundle index2 = new IndexBundle("index_column1", false, Collections.singletonList("column1"), "");

    EntityBundle entity1 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(index1), null);
    EntityBundle entity2 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(index2), null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate))
      .containsExactly("DROP INDEX index_table_column1;", "CREATE INDEX index_column1 ON `table` (column1);").inOrder();
  }

  @Test
  public void testUpdateIndexOnTableChange() {
    IndexBundle indexBundle = new IndexBundle("index_table_column1", false, Collections.singletonList("column1"), "");

    EntityBundle entity1 = new EntityBundle("table", "", Arrays.asList(field1, field2, field3),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(indexBundle), null);
    EntityBundle entity2 = new EntityBundle("table", "", Arrays.asList(field1, field2),
                                            new PrimaryKeyBundle(false, Collections.singletonList(field1.getColumnName())),
                                            Collections.singletonList(indexBundle), null);

    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);

    assertThat(SqlStatementsGenerator.getUpdateStatements(entityUpdate))
      .containsExactly(
        "CREATE TABLE table_data$android_studio_tmp\n" +
        "(\n" +
        "\tcolumn1 TEXT,\n" +
        "\tcolumn2 TEXT,\n" +
        "\tPRIMARY KEY (column1)\n" +
        ");",
        "INSERT INTO table_data$android_studio_tmp (column1, column2)\n" +
        "\tSELECT column1, column2\n" +
        "\tFROM `table`;",
        "DROP TABLE `table`;",
        "ALTER TABLE table_data$android_studio_tmp RENAME TO `table`;",
        "CREATE INDEX index_table_column1 ON `table` (column1);").inOrder();
  }
}
