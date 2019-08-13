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
package com.android.tools.idea.room.migrations.generators;

import com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.json.ForeignKeyBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.android.tools.idea.room.migrations.update.EntityUpdate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for generating SQLite statements which perform updates between two versions of a database schema.
 */
public class SqlStatementsGenerator {
  private static final String TMP_TABLE_NAME_TEMPLATE = "%s_data$android_studio_tmp";

  /**
   * Returns SQLite statements which produce the update from the older version of the database to the current one.
   *
   * @param databaseUpdate the representation of the updates which need to be performed
   */
  @NotNull
  public static List<String> getUpdateStatements(@NotNull DatabaseUpdate databaseUpdate) {
    List<String> updateStatements = new ArrayList<>();
    boolean needsConstraintsCheck = false;

    for (EntityBundle entityBundle : databaseUpdate.getNewEntities().values()) {
      updateStatements.add(getCreateTableStatement(entityBundle.getTableName(),
                                                   entityBundle.getFields(),
                                                   entityBundle.getPrimaryKey(),
                                                   entityBundle.getForeignKeys()));
    }

    for (EntityUpdate entityUpdate : databaseUpdate.getModifiedEntities().values()) {
      updateStatements.addAll(getUpdateStatements(entityUpdate));
      needsConstraintsCheck |= entityUpdate.foreignKeysWereUpdated();
    }

    for (EntityBundle entityBundle : databaseUpdate.getDeletedEntities().values()) {
      updateStatements.add(getDropTableStatement(entityBundle.getTableName()));
    }

    if (needsConstraintsCheck) {
      updateStatements.add("PRAGMA foreign_key_check;");
    }

    return updateStatements;
  }

  /**
   * Returns SQLite statements which produce the update form the older version of the table to the current one.
   *
   * @param entityUpdate the representation of the updates which need to be performed
   */
  @NotNull
  public static List<String> getUpdateStatements(@NotNull EntityUpdate entityUpdate) {
    String tableName = entityUpdate.getTableName();
    List<FieldBundle> newFields = entityUpdate.getNewFields();
    List<FieldBundle> deletedFields = entityUpdate.getDeletedFields();
    List<FieldBundle> modifiedFields = entityUpdate.getModifiedFields();
    List<FieldBundle> unmodifiedFields = entityUpdate.getUnmodifiedFields();

    // The SQLite ALTER TABLE command supports only column addition.
    // Therefore, when deleting/renaming/modifying a column, we need to perform a more complex update.
    // More information ca be found here: https://www.sqlite.org/lang_altertable.html
    if (deletedFields.isEmpty() && modifiedFields.isEmpty() && !entityUpdate.keysWereUpdated()) {
      ArrayList<String> statements = new ArrayList<>();
      for (FieldBundle field : newFields) {
        statements.add(getAddColumnStatement(tableName, field));
      }

      return statements;
    }

    List<FieldBundle> initialFields = new ArrayList<>();
    Stream.of(unmodifiedFields, modifiedFields).forEach(initialFields::addAll);

    List<FieldBundle> currentFields = new ArrayList<>();
    Stream.of(unmodifiedFields, modifiedFields, newFields).forEach(currentFields::addAll);

    return getComplexTableUpdate(tableName,
                                 initialFields,
                                 currentFields,
                                 entityUpdate.getPrimaryKey(),
                                 entityUpdate.getForeignKeys());
  }

  /**
   * Returns a collection of statements which perform complex updates on a SQLite table, i.e. deleting/renaming/modifying columns.
   * As the SQLite ALTER TABLE command does not support these operations, the way to perform them is to create a new table with the desired
   * new format and then transfer to content from te original table to the new one. More information ca be found in the SQLite documentation
   * (https://www.sqlite.org/lang_altertable.html).
   *
   * @param tableName the table to be updated
   * @param initialFields the fields the table contained before the update
   * @param currentFields the fields the table should contain after the update
   */
  @NotNull
  private static List<String> getComplexTableUpdate(@NotNull String tableName,
                                                    @NotNull List<FieldBundle> initialFields,
                                                    @NotNull List<FieldBundle> currentFields,
                                                    @NotNull PrimaryKeyBundle primaryKey,
                                                    @Nullable List<ForeignKeyBundle> foreignKeys) {
    List<String> updateStatements = new ArrayList<>();
    String tempTableName = String.format(TMP_TABLE_NAME_TEMPLATE, tableName);
    updateStatements.add(getCreateTableStatement(tempTableName, currentFields, primaryKey, foreignKeys));

    updateStatements.add(getInsertIntoTableStatement(tempTableName,
                                                     initialFields,
                                                     getSelectFromTableStatement(tableName, initialFields)));
    updateStatements.add(getDropTableStatement(tableName));
    updateStatements.add(getRenameTableStatement(tempTableName, tableName));

    return updateStatements;
  }

  /**
   * Returns the statement which performs the query.
   *
   * @param tableName the name of the table to select from
   * @param fields the fields to be selected
   */
  @NotNull
  private static String getSelectFromTableStatement(@NotNull String tableName, @NotNull List<FieldBundle> fields) {
    tableName = AndroidSqlLexer.getValidName(tableName);
    StringBuilder statement = new StringBuilder("SELECT ");
    statement.append(fields.stream().map(f -> AndroidSqlLexer.getValidName(f.getColumnName())).collect(Collectors.joining(", ")));
    statement.append("\n");
    statement.append(String.format("\tFROM %s;", tableName));

    return statement.toString();
  }

  /**
   * Returns the statement which performs the insertion.
   *
   * @param tableName the name of the table to insert into
   * @param fields the fields to insert values into
   * @param values the values to be inserted (could be either the result of another SQL statement or just an enumeration of values)
   */
  @NotNull
  private static String getInsertIntoTableStatement(@NotNull String tableName, @NotNull List<FieldBundle> fields, @NotNull String values) {
    tableName = AndroidSqlLexer.getValidName(tableName);
    StringBuilder statement = new StringBuilder(String.format("INSERT INTO %s (", tableName));
    statement.append(fields.stream().map(f -> AndroidSqlLexer.getValidName(f.getColumnName())).collect(Collectors.joining(", ")));
    statement.append(")\n\t");
    statement.append(values);

    if (!statement.toString().endsWith(";")) {
      statement.append(";");
    }

    return statement.toString();
  }

  /**
   * Returns a statement for which performs the renaming.
   *
   * @param oldName the original name of the table to be renamed
   * @param newName the new name of the table
   */
  @NotNull
  private static String getRenameTableStatement(@NotNull String oldName, @NotNull String newName) {
    return String.format("ALTER TABLE %s RENAME TO %s;",
                         AndroidSqlLexer.getValidName(oldName),
                         AndroidSqlLexer.getValidName(newName));
  }

  /**
   * Returns a statement which creates the table.
   *
   * @param tableName the name of the new table
   * @param fields the FiledBundles which describe the columns of the table
   */
  @NotNull
  private static String getCreateTableStatement(@NotNull String tableName,
                                                @NotNull List<FieldBundle> fields,
                                                @NotNull PrimaryKeyBundle primaryKey,
                                                @Nullable List<ForeignKeyBundle> foreignKeys) {
    tableName = AndroidSqlLexer.getValidName(tableName);
    StringBuilder statement = new StringBuilder(String.format("CREATE TABLE %s\n(\n", tableName));

    for (FieldBundle field : fields) {
      statement.append(String.format("\t%s,\n", getColumnDescription(field)));
      if (shouldAddAutoIncrementToColumn(field, primaryKey)) {
        statement.replace(statement.length() - 2, statement.length(), "");
        statement.append(" PRIMARY KEY AUTOINCREMENT,\n");
      }
    }

    if (!primaryKey.isAutoGenerate()) {
      statement.append(String.format("\t%s,\n", getPrimaryKeyConstraint(primaryKey)));
    }

    if (foreignKeys != null) {
      for (ForeignKeyBundle foreignKey : foreignKeys) {
        statement.append(String.format("\t%s,\n", getForeignKeyConstraint(foreignKey)));
      }
    }

    statement.replace(statement.length() - 2, statement.length() - 1, "");
    statement.append(");");

    return statement.toString();
  }

  /**
   * Returns a statement which destroys the table.
   *
   * @param tableName the name of the table to be deleted
   */
  @NotNull
  private static String getDropTableStatement(@NotNull String tableName) {
    tableName = AndroidSqlLexer.getValidName(tableName);
    return String.format("DROP TABLE %s;", tableName);
  }

  /**
   * Returns a statement which adds the column to the table.
   *
   * @param tableName the name of the table to be modified
   * @param field the FieldBundle which describes the column to be added
   */
  @NotNull
  private static String getAddColumnStatement(@NotNull String tableName, @NotNull FieldBundle field) {
    tableName = AndroidSqlLexer.getValidName(tableName);
    return String.format("ALTER TABLE %s ADD COLUMN %s;", tableName, getColumnDescription(field));
  }

  /**
   * Returns a String containing the full description of the column.
   *
   * @param field the FieldBundle which describes the column
   */
  @NotNull
  private static String getColumnDescription(@NotNull FieldBundle field) {
    StringBuilder fieldDescription =
      new StringBuilder(String.format("%s %s", AndroidSqlLexer.getValidName(field.getColumnName()), field.getAffinity()));

    if (field.getDefaultValue() != null) {
      fieldDescription.append(String.format(" DEFAULT %s", field.getDefaultValue()));
    }

    if (field.isNonNull()) {
      fieldDescription.append(" NOT NULL");
    }

    return fieldDescription.toString();
  }

  @NotNull
  private static String getPrimaryKeyConstraint(@NotNull PrimaryKeyBundle primaryKey) {
    return String.format("PRIMARY KEY (%s)",
                         primaryKey.getColumnNames().stream().map(c -> AndroidSqlLexer.getValidName(c))
                           .collect(Collectors.joining(", ")));
  }

  @NotNull
  private static String getForeignKeyConstraint(@NotNull ForeignKeyBundle foreignKey) {
    String onUpdate = foreignKey.getOnUpdate() != null && !foreignKey.getOnUpdate().isEmpty()
                      ? String.format(" ON UPDATE %s", foreignKey.getOnUpdate())
                      : "";
    String onDelete = foreignKey.getOnDelete() != null && !foreignKey.getOnDelete().isEmpty()
                      ? String.format(" ON DELETE %s", foreignKey.getOnDelete())
                      : "";
    return String.format(
      "FOREIGN KEY (%s) REFERENCES %s (%s)%s%s",
      foreignKey.getColumns().stream().map(c -> AndroidSqlLexer.getValidName(c)).collect(Collectors.joining(", ")),
      foreignKey.getTable(),
      foreignKey.getReferencedColumns().stream().map(c -> AndroidSqlLexer.getValidName(c)).collect(Collectors.joining(", ")),
      onUpdate,
      onDelete);
  }

  private static boolean shouldAddAutoIncrementToColumn(@NotNull FieldBundle field, @NotNull PrimaryKeyBundle primaryKey) {
    return primaryKey.isAutoGenerate() &&
           field.getAffinity().toLowerCase(Locale.ENGLISH).equals("integer") &&
           (primaryKey.getColumnNames().size() == 1 && primaryKey.getColumnNames().get(0).equals(field.getColumnName()));
  }
}
