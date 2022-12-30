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

import static com.android.tools.idea.lang.androidSql.parser.AndroidSqlLexer.getValidName;
import static com.android.tools.idea.room.migrations.update.SchemaDiffUtil.ftsTableNeedsExternalContentSource;

import com.android.tools.idea.room.migrations.json.BundleUtil;
import com.android.tools.idea.room.migrations.json.DatabaseViewBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.json.ForeignKeyBundle;
import com.android.tools.idea.room.migrations.json.FtsEntityBundle;
import com.android.tools.idea.room.migrations.json.FtsOptionsBundle;
import com.android.tools.idea.room.migrations.json.IndexBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import com.android.tools.idea.room.migrations.update.DatabaseUpdate;
import com.android.tools.idea.room.migrations.update.EntityUpdate;
import com.intellij.openapi.util.InvalidDataException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for generating SQLite statements which perform updates between two versions of a database schema.
 */
public final class SqlStatementsGenerator {
  private static final String TMP_TABLE_NAME_TEMPLATE = "%s_data$android_studio_tmp";

  /**
   * Returns SQLite statements which produce the update from the older version of the database to the current one.
   *
   * @param databaseUpdate the representation of the updates which need to be performed
   */
  @NotNull
  public static List<String> getMigrationStatements(@NotNull DatabaseUpdate databaseUpdate) {
    List<String> updateStatements = new ArrayList<>();

    databaseUpdate.getRenamedEntities().forEach((oldName, newName) -> {updateStatements.add(getRenameTableStatement(oldName, newName));});

    for (EntityBundle entity : databaseUpdate.getNewEntities().values()) {
      updateStatements.add(getCreateTableStatement(entity.getTableName(), entity));
    }

    for (EntityUpdate entityUpdate : databaseUpdate.getModifiedEntities().values()) {
      updateStatements.addAll(getMigrationStatements(entityUpdate));
    }

    for (EntityBundle entity : databaseUpdate.getDeletedEntities().values()) {
      updateStatements.add(getDropTableStatement(entity.getTableName()));
    }

    for (DatabaseViewBundle view : databaseUpdate.getDeletedViews()) {
      updateStatements.add(getDropViewStatement(view));
    }

    for (DatabaseViewBundle view : databaseUpdate.getNewOrModifiedViews()) {
      updateStatements.add(getCreateViewStatement(view));
    }

    for (String tableName : databaseUpdate.getTablesToForeignKeyCheck()) {
      updateStatements.add(getForeignKeyConstraintCheck(tableName));
    }

    return updateStatements;
  }

  /**
   * Returns SQLite statements which produce the update form the older version of the table to the current one.
   *
   * @param entityUpdate the representation of the updates which need to be performed
   */
  @NotNull
  public static List<String> getMigrationStatements(@NotNull EntityUpdate entityUpdate) {
    String tableName = entityUpdate.getNewTableName();
    ArrayList<String> updateStatements = new ArrayList<>();

    if (entityUpdate.isComplexUpdate()) {
      // If the update produces an FTS table which requires data copied from an external source, we need to recreate the table and
      // copy the data from the external content table.
      if (entityUpdate.shouldCreateAnFtsEntity() && ftsTableNeedsExternalContentSource((FtsEntityBundle)entityUpdate.getNewState())) {
        updateStatements.addAll(getComplexUpdateForFtsTableWithExternalContent(entityUpdate));
      } else {
        updateStatements.addAll(getComplexTableUpdate(entityUpdate));
      }
    } else {
      if (entityUpdate.shouldRenameTable()) {
        updateStatements.add(getRenameTableStatement(entityUpdate.getOldTableName(), entityUpdate.getNewTableName()));
      }

      Map<String, FieldBundle> newFields = entityUpdate.getNewFields();
      for (FieldBundle field : newFields.values()) {
        updateStatements.add(getAddColumnStatement(tableName, field));
      }

      Map<FieldBundle, String> renamedFields = entityUpdate.getRenamedFields();
      for (Map.Entry<FieldBundle, String> newFieldToOldNameMapping : renamedFields.entrySet()) {
        updateStatements
          .add(getRenameColumnStatement(tableName, newFieldToOldNameMapping.getValue(), newFieldToOldNameMapping.getKey().getColumnName()));
      }

      Map<FieldBundle, String> valuesForUninitializedFields = entityUpdate.getValuesForUninitializedFields();
      if (!valuesForUninitializedFields.isEmpty()) {
        updateStatements.add(getUpdateColumnsValuesStatement(tableName, valuesForUninitializedFields));
      }
    }

    for (IndexBundle index : entityUpdate.getIndicesToBeDropped()) {
      updateStatements.add(getDropIndexStatement(index));
    }

    for (IndexBundle index : entityUpdate.getIndicesToBeCreated()) {
      updateStatements.add(getCreateIndexStatement(index, tableName));
    }

    return updateStatements;
  }

  /**
   * Returns a collection of statements which perform complex updates on a SQLite table, i.e. deleting/renaming/modifying columns or
   * modifying an FTS table which does not have an external source of content.
   *
   * <p>As the SQLite ALTER TABLE command does not support these operations, the way to perform them is to create a new table with the desired
   * new format and then transfer to content from te original table to the new one. More information ca be found in the SQLite documentation
   * (https://www.sqlite.org/lang_altertable.html).</p>
   *
   * @param entityUpdate the object which describes the changes which need to be executed
   */
  @NotNull
  private static List<String> getComplexTableUpdate(@NotNull EntityUpdate entityUpdate) {
    List<String> updateStatements = new ArrayList<>();
    String dataSource = getDataSourceForComplexUpdate(entityUpdate);
    Map<String, String> columnNameToColumnValue = getColumnNameToColumnValueMapping(entityUpdate);

    if (columnNameToColumnValue.isEmpty()) {
      updateStatements.add(getDropTableStatement(entityUpdate.getOldTableName()));
      updateStatements.add(getCreateTableStatement(entityUpdate.getNewTableName(), entityUpdate.getNewState()));
    }
    else {
      String oldTableName = !entityUpdate.shouldRenameTable() ? entityUpdate.getNewTableName() : entityUpdate.getOldTableName();
      String newTableName =
        !entityUpdate.shouldRenameTable() ? String.format(TMP_TABLE_NAME_TEMPLATE, oldTableName) : entityUpdate.getNewTableName();

      updateStatements.add(getCreateTableStatement(newTableName, entityUpdate.getNewState()));
      updateStatements.add(getInsertIntoTableStatement(newTableName,
                                                       new ArrayList<>(columnNameToColumnValue.keySet()),
                                                       getSelectFromTableStatement(
                                                         dataSource,
                                                         new ArrayList<>(columnNameToColumnValue.values()))));
      updateStatements.add(getDropTableStatement(oldTableName));
      if (!entityUpdate.shouldRenameTable()) {
        updateStatements.add(getRenameTableStatement(newTableName, oldTableName));
      }
    }

    return updateStatements;
  }

  /**
   * Returns a collection of statements which perform the update of an FTS table with external content.
   *
   * <p>Because we have an external source of data, we only need to drop the old table, create a new one and copy the data form the external
   * content table. More information can be found in the SQLite documentation (https://www.sqlite.org/fts3.html#summary).</p>
   */
  private static List<String> getComplexUpdateForFtsTableWithExternalContent(@NotNull EntityUpdate entityUpdate) {
    List<String> updateStatements = new ArrayList<>();
    String oldTableName = entityUpdate.getOldTableName();
    String newTableName = entityUpdate.getNewTableName();
    String dataSource = getDataSourceForComplexUpdate(entityUpdate);
    Map<String, String> columnNameToColumnValue = getColumnNameToColumnValueMapping(entityUpdate);

    updateStatements.add(getDropTableStatement(oldTableName));
    updateStatements.add(getCreateTableStatement(newTableName, entityUpdate.getNewState()));
    updateStatements.add(getInsertIntoTableStatement(
      newTableName,
      new ArrayList<>(columnNameToColumnValue.keySet()),
      getSelectFromTableStatement(dataSource, new ArrayList<>(columnNameToColumnValue.values()))));

    return updateStatements;
  }

  /**
   * Returns the statement which performs the query.
   *
   * @param tableName the name of the table to select from
   * @param columnNames the names of the columns to be selected
   */
  @NotNull
  private static String getSelectFromTableStatement(@NotNull String tableName, @NotNull List<String> columnNames) {
    tableName = getValidName(tableName);
    StringBuilder statement = new StringBuilder(String.format("SELECT %s\n", getColumnEnumeration(columnNames)));
    statement.append(String.format("\tFROM %s;", tableName));

    return statement.toString();
  }

  /**
   * Returns the statement which performs the insertion.
   *
   * @param tableName the name of the table to insert into
   * @param columnNames the names of the columns to insert values into
   * @param values the values to be inserted (could be either the result of another SQL statement or just an enumeration of values)
   */
  @NotNull
  private static String getInsertIntoTableStatement(@NotNull String tableName, @NotNull List<String> columnNames, @NotNull String values) {
    tableName = getValidName(tableName);
    StringBuilder statement =
      new StringBuilder(String.format("INSERT INTO %s (%s)\n\t", tableName, getColumnEnumeration(columnNames)));
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
                         getValidName(oldName),
                         getValidName(newName));
  }

  /**
   * Returns a statement which creates the table.
   *
   * @param tableName the name of the new table
   * @param entity the EntityBundleObject which describes the table to be created and contains a template for the creation statement
   */
  @NotNull
  private static String getCreateTableStatement(@NotNull String tableName,
                                                @NotNull EntityBundle entity) {
    // If the entity describes an FTS table, we use the create statement stored inside the entity as the information from the FtsOptionBundle
    // class is not accessible from this scope.
    if (entity instanceof FtsEntityBundle) {
      return getCreateTableStatementFromEntityBundle(tableName, entity);
    }

    tableName = getValidName(tableName);
    StringBuilder statement = new StringBuilder(String.format("CREATE TABLE %s\n(\n", tableName));

    for (FieldBundle field : entity.getFields()) {
      statement.append(String.format("\t%s,\n", getColumnDescription(field)));
      if (shouldAddAutoIncrementToColumn(field, entity.getPrimaryKey())) {
        statement.replace(statement.length() - 2, statement.length(), "");
        statement.append(" PRIMARY KEY AUTOINCREMENT,\n");
      }
    }

    if (!entity.getPrimaryKey().isAutoGenerate()) {
      statement.append(String.format("\t%s,\n", getPrimaryKeyConstraint(entity.getPrimaryKey())));
    }

    if (entity.getForeignKeys() != null) {
      for (ForeignKeyBundle foreignKey : entity.getForeignKeys()) {
        statement.append(String.format("\t%s,\n", getForeignKeyConstraint(foreignKey)));
      }
    }

    statement.replace(statement.length() - 2, statement.length() - 1, "");
    statement.append(");");

    return statement.toString();
  }

  /**
   * Returns a statement which creates the table.
   * @param tableName the name of the table
   * @param entity the EntityBundleObject which describes the table to be created and contains a template for the creation statement
   */
  @NotNull
  private static String getCreateTableStatementFromEntityBundle(@NotNull String tableName, @NotNull EntityBundle entity) {
    String statement = entity.getCreateSql().replace(BundleUtil.TABLE_NAME_PLACEHOLDER, tableName);
    if (!statement.trim().endsWith(";")) {
      statement += ";";
    }
    return statement;
  }

  /**
   * Returns a statement which destroys the table.
   *
   * @param tableName the name of the table to be deleted
   */
  @NotNull
  private static String getDropTableStatement(@NotNull String tableName) {
    tableName = getValidName(tableName);
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
    tableName = getValidName(tableName);
    return String.format("ALTER TABLE %s ADD COLUMN %s;", tableName, getColumnDescription(field));
  }

  /**
   * Returns a statement which renames a column of a table.
   *
   * @param tableName the name of the table
   * @param oldColumnName the name of the column to be renamed
   * @param newColumnName the new name of the column to be renamed
   */
  private static String getRenameColumnStatement(@NotNull String tableName, @NotNull String oldColumnName, @NotNull String newColumnName) {
    tableName = getValidName(tableName);
    oldColumnName = getValidName(oldColumnName);
    newColumnName = getValidName(newColumnName);

    return String.format("ALTER TABLE %s RENAME COLUMN %s TO %s;", tableName, oldColumnName, newColumnName);
  }

  /**
   * Returns a String containing the full description of the column.
   *
   * @param field the FieldBundle which describes the column
   */
  @NotNull
  private static String getColumnDescription(@NotNull FieldBundle field) {
    StringBuilder fieldDescription =
      new StringBuilder(String.format("%s %s", getValidName(field.getColumnName()), field.getAffinity()));

    if (field.getDefaultValue() != null) {
      fieldDescription.append(String.format(" DEFAULT %s", toSqlStringLiteral(field.getDefaultValue())));
    }

    if (field.isNonNull()) {
      fieldDescription.append(" NOT NULL");
    }

    return fieldDescription.toString();
  }

  @NotNull
  private static String getPrimaryKeyConstraint(@NotNull PrimaryKeyBundle primaryKey) {
    return String.format("PRIMARY KEY (%s)", getColumnEnumeration(primaryKey.getColumnNames()));
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
      getColumnEnumeration(foreignKey.getColumns()),
      getValidName(foreignKey.getTable()),
      getColumnEnumeration(foreignKey.getReferencedColumns()),
      onUpdate,
      onDelete);
  }

  private static String getForeignKeyConstraintCheck(@NotNull String tableName) {
    return String.format("PRAGMA foreign_key_check(%s);", getValidName(tableName));
  }

  @NotNull
  private static String getCreateIndexStatement(@NotNull IndexBundle index,
                                                @NotNull String tableName) {
    tableName = getValidName(tableName);
    StringBuilder statement = new StringBuilder("CREATE ");

    if (index.isUnique()) {
      statement.append("UNIQUE ");
    }

    statement.append(
      String.format("INDEX %s ON %s (%s);", getValidName(index.getName()), tableName, getColumnEnumeration(index.getColumnNames())));

    return statement.toString();
  }

  @NotNull
  private static String getDropIndexStatement(@NotNull IndexBundle index) {
    return String.format("DROP INDEX %s;", getValidName(index.getName()));
  }

  @NotNull
  private static String getUpdateColumnsValuesStatement(@NotNull String tableName, @NotNull Map<FieldBundle, String> newFieldsValues) {
    tableName = getValidName(tableName);
    StringBuilder statement = new StringBuilder(String.format("UPDATE %s\nSET", tableName));

    String valueAssignments =
      newFieldsValues.keySet().stream()
        .map(fieldBundle -> String.format("\t%s = %s", fieldBundle.getColumnName(), toSqlStringLiteral(newFieldsValues.get(fieldBundle))))
        .collect(Collectors.joining(",\n"));

    statement.append(valueAssignments);
    statement.append(";");

    return statement.toString();
  }

  @NotNull
  private static String getCreateViewStatement(@NotNull DatabaseViewBundle view) {
    String statement = view.getCreateSql().replace(BundleUtil.VIEW_NAME_PLACEHOLDER, getValidName(view.getViewName()));
    if (!statement.trim().endsWith(";")) {
      statement += ";";
    }
    return statement;
  }

  @NotNull
  private static String getDropViewStatement(@NotNull DatabaseViewBundle view) {
    return String.format("DROP VIEW %s;", getValidName(view.getViewName()));
  }

  @NotNull
  private static String getColumnEnumeration(@NotNull List<String> columnNames) {
    return columnNames.stream().map(c -> getValidName(c)).collect(Collectors.joining(", "));
  }

  /**
   * Formats a string into a valid SQLite string literal by quoting it with simple quotes and escaping any already existing quotes.
   */
  @NotNull
  private static String toSqlStringLiteral(@NotNull String value) {
    if (value.contains("'")) {
       value = value.replaceAll("'", "''");
    }

    return String.format("'%s'", value);
  }

  private static boolean shouldAddAutoIncrementToColumn(@NotNull FieldBundle field, @NotNull PrimaryKeyBundle primaryKey) {
    return primaryKey.isAutoGenerate() &&
           field.getAffinity().toLowerCase(Locale.US).equals("integer") &&
           (primaryKey.getColumnNames().size() == 1 && primaryKey.getColumnNames().get(0).equals(field.getColumnName()));
  }

  /**
   * Returns the name of the table to copy data from in case of an complex update.
   * In case of an FTS table with external content, it always returns the name of the external content table.
   * In case of a renamed table (which is not an FTS table with external content), it will return the old name of the table.
   * Otherwise, it returns the name of the table to be updated.
   */
  @NotNull
  private static String getDataSourceForComplexUpdate(@NotNull EntityUpdate entityUpdate) {
    EntityBundle newState = entityUpdate.getNewState();
    if (entityUpdate.shouldCreateAnFtsEntity() && ftsTableNeedsExternalContentSource((FtsEntityBundle)newState)) {
      return ((FtsEntityBundle)newState).getFtsOptions().getContentTable();
    }

    if (entityUpdate.shouldRenameTable()) {
      return entityUpdate.getOldTableName();
    }

    return entityUpdate.getNewTableName();
  }

  /**
   * Returns a value to initialize the given column with.
   *
   * <p>First we look for a user defined value. If there is none, we check whether the column is already present in the
   * table and therefore already has values to be populated with.</p>
   */
  @Nullable
  private static String getValueForField(@NotNull FieldBundle field, @NotNull EntityUpdate entityUpdate) {
    // Check for user specified value
    String userSpecifiedValue = entityUpdate.getValuesForUninitializedFields().get(field);
    if (userSpecifiedValue != null) {
      return toSqlStringLiteral(userSpecifiedValue);
    }

    // If the table to be updated is an FTS table and has an external content source, we take the values for the current
    // column from its correspondent column in the external content table
    if (entityUpdate.shouldCreateAnFtsEntity()) {
      FtsOptionsBundle ftsOptions = ((FtsEntityBundle)entityUpdate.getNewState()).getFtsOptions();
      if (ftsOptions != null && (ftsOptions.getContentTable() != null && !ftsOptions.getContentTable().isEmpty())) {
        return getValidName(field.getColumnName());
      }
    }

    // If the column was renamed, we use the values under the old name
    String oldColumnName = entityUpdate.getRenamedFields().get(field);
    if (oldColumnName != null) {
      return getValidName(oldColumnName);
    }

    // If the column is present in the table as is, we use the values under its name
    String columnName = field.getColumnName();
    if (entityUpdate.getUnmodifiedFields().get(columnName) != null || entityUpdate.getModifiedFields().get(columnName) != null) {
      return getValidName(columnName);
    }

    return null;
  }

  /**
   * Provides a correlation between the name of the columns from newly created table and the values they should be initialized with.
   */
  @NotNull
  private static Map<String, String> getColumnNameToColumnValueMapping(@NotNull EntityUpdate entityUpdate) {
    Map<String, String> columnNameToColumnValue = new LinkedHashMap<>();

    for (FieldBundle field : entityUpdate.getAllFields()) {
      String value = getValueForField(field, entityUpdate);
      if (value == null) {
        if (columnNeedsUserSpecifiedValue(field)) {
          throw new InvalidDataException("NOT NULL column without default value or user specified value.");
        }
      } else {
        columnNameToColumnValue.put(getValidName(field.getColumnName()), value);
      }
    }

    return columnNameToColumnValue;
  }

  private static boolean columnNeedsUserSpecifiedValue(@NotNull FieldBundle field) {
    return field.isNonNull() && (field.getDefaultValue() == null || field.getDefaultValue().isEmpty());
  }
}
