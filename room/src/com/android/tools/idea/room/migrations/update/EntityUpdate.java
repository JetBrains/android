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
package com.android.tools.idea.room.migrations.update;

import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.json.ForeignKeyBundle;
import com.android.tools.idea.room.migrations.json.IndexBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the differences between two version of Room database Entity.
 */
public class EntityUpdate {
  private String tableName;

  private List<FieldBundle> allFields;
  private Map<String, FieldBundle> unmodifiedFields;
  private Map<String, FieldBundle> modifiedFields;
  private Map<String, FieldBundle> deletedFields;
  private Map<String, FieldBundle> newFields;
  private Map<FieldBundle, String> valuesForUninitializedFields;
  private Map<FieldBundle, String> renamedFields;
  private boolean containsUninitializedNotNullFields;
  private boolean containsRenamedAndModifiedFields;


  private List<IndexBundle> unmodifiedIndices;
  private List<IndexBundle> deletedIndices;
  private List<IndexBundle> newOrModifiedIndices;

  private PrimaryKeyBundle primaryKey;
  private List<ForeignKeyBundle> foreignKeys;
  private boolean primaryKeyUpdate;
  private boolean foreignKeysUpdate;

  /**
   * @param oldEntity entity description from an older version of the database
   * @param newEntity entity description from the current version of the database
   */
  public EntityUpdate(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    tableName = oldEntity.getTableName();

    allFields = new ArrayList<>(newEntity.getFields());
    unmodifiedFields = new HashMap<>();
    deletedFields = new HashMap<>();
    modifiedFields = new HashMap<>();
    newFields = new HashMap<>();
    renamedFields = new HashMap<>();
    valuesForUninitializedFields = new HashMap<>();
    containsUninitializedNotNullFields = false;

    Map<String, FieldBundle> oldEntityFields = new HashMap<>(oldEntity.getFieldsByColumnName());
    for (FieldBundle newField : newEntity.getFields()) {
      if (oldEntityFields.containsKey(newField.getColumnName())) {
        FieldBundle oldField = oldEntityFields.remove(newField.getColumnName());
        if (!oldField.isSchemaEqual(newField)) {
          modifiedFields.put(newField.getColumnName(), newField);
        }
        else {
          unmodifiedFields.put(newField.getColumnName(), newField);
        }
      }
      else {
        newFields.put(newField.getColumnName(), newField);
      }

      if (newField.isNonNull() && (newField.getDefaultValue() == null || newField.getDefaultValue().isEmpty())) {
        containsUninitializedNotNullFields = true;
      }
    }
    deletedFields = oldEntityFields;

    unmodifiedIndices = new ArrayList<>();
    deletedIndices = new ArrayList<>();
    newOrModifiedIndices = new ArrayList<>();

    if (oldEntity.getIndices() == null) {
      if (newEntity.getIndices() != null) {
        newOrModifiedIndices.addAll(newEntity.getIndices());
      }
    } else {
      Map<String, IndexBundle> oldIndices = oldEntity.getIndices().stream().collect(Collectors.toMap(IndexBundle::getName, index -> index));
      if (newEntity.getIndices() != null) {
        for (IndexBundle newIndex : newEntity.getIndices()) {
          if (oldIndices.containsKey(newIndex.getName())) {
            if (!oldIndices.get(newIndex.getName()).isSchemaEqual(newIndex)) {
              newOrModifiedIndices.add(newIndex);
            }
            else {
              unmodifiedIndices.add(newIndex);
              oldIndices.remove(newIndex.getName());
            }
          }
          else {
            newOrModifiedIndices.add(newIndex);
          }
        }
      }
      deletedIndices.addAll(oldIndices.values());
    }

    primaryKey = newEntity.getPrimaryKey();
    foreignKeys = newEntity.getForeignKeys();
    primaryKeyUpdate = !oldEntity.getPrimaryKey().isSchemaEqual(newEntity.getPrimaryKey());
    foreignKeysUpdate = false;

    if (oldEntity.getForeignKeys() != null && newEntity.getForeignKeys() != null) {
      if (oldEntity.getForeignKeys().size() != newEntity.getForeignKeys().size()) {
        foreignKeysUpdate = true;
      } else {
        for (int i = 0; i < oldEntity.getForeignKeys().size(); i++) {
          if (!oldEntity.getForeignKeys().get(i).isSchemaEqual(newEntity.getForeignKeys().get(i))) {
            foreignKeysUpdate = true;
          }
        }
      }
    } else if (oldEntity.getForeignKeys() == null && newEntity.getForeignKeys() != null) {
      foreignKeysUpdate = true;
    }

    containsRenamedAndModifiedFields = false;
  }

  @NotNull
  public List<FieldBundle> getAllFields() {
    return allFields;
  }

  @NotNull
  public Map<String, FieldBundle> getUnmodifiedFields() {
    return unmodifiedFields;
  }

  @NotNull
  public Map<String, FieldBundle> getModifiedFields() {
    return modifiedFields;
  }

  @NotNull
  public Map<String, FieldBundle> getDeletedFields() {
    return deletedFields;
  }

  @NotNull
  public Map<String, FieldBundle> getNewFields() {
    return newFields;
  }

  @NotNull
  public List<IndexBundle> getIndicesToBeDropped() {
    return deletedIndices;
  }

  @NotNull
  public List<IndexBundle> getIndicesToBeCreated() {
    if (isComplexUpdate()) {
      List<IndexBundle> indicesToCreate = new ArrayList<>();
      Stream.of(unmodifiedIndices, newOrModifiedIndices).forEach(indicesToCreate::addAll);
      return indicesToCreate;
    }

    return newOrModifiedIndices;
  }

  @NotNull
  public PrimaryKeyBundle getPrimaryKey() {
    return primaryKey;
  }

  @Nullable
  public List<ForeignKeyBundle> getForeignKeys() {
    return foreignKeys;
  }

  @NotNull
  public String getTableName() {
    return tableName;
  }

  /**
   * Takes a mapping between names of new columns and user specified default values for them.
   * This mapping is used in other to populate the pre-existent rows in a table with new NOT NULL columns without default values
   * specified at creation.
   */
  public void setValuesForUninitializedFields(@NotNull Map<FieldBundle, String> valuesForUninitializedFields) {
    this.valuesForUninitializedFields = valuesForUninitializedFields;
  }

  @NotNull
  public Map<FieldBundle, String> getValuesForUninitializedFields() {
    return valuesForUninitializedFields;
  }

  /**
   * Separates the renamed columns from the deleted/newly added columns based on user input.
   * @param oldToNewNameMapping mapping from the old name of a field to the actual name
   */
  public void applyRenameMapping(@NotNull Map<String, String> oldToNewNameMapping) {
    for (Map.Entry<String, String> columnNames : oldToNewNameMapping.entrySet()) {
      FieldBundle oldField = deletedFields.remove(columnNames.getKey());
      FieldBundle newField = newFields.remove(columnNames.getValue());

      if (oldField == null || newField == null) {
        throw new IllegalArgumentException("Invalid old column name to new column name mapping");
      }

      if (!isFieldStructureTheSame(oldField, newField)) {
        containsRenamedAndModifiedFields = true;
      }
      renamedFields.put(newField, oldField.getColumnName());
    }
  }

  @NotNull
  public Map<FieldBundle, String> getRenamedFields() {
    return renamedFields;
  }

  /**
   * Specifies whether any primary/foreign key constraints were updated.
   */
  public boolean keysWereUpdated() {
    return primaryKeyUpdate | foreignKeysUpdate;
  }

  /**
   * Specifies whether any foreign key constraints were updated.
   */
  public boolean foreignKeysWereUpdated() {
    return foreignKeysUpdate;
  }

  /**
   * Specifies whether this update requires recreating the table and copying data over.
   * The SQLite ALTER TABLE command supports only column addition.
   * Therefore, when deleting/renaming/modifying a column or modifying primary/foreign key constraints, we need to perform a more complex
   * update. More information ca be found here: https://www.sqlite.org/lang_altertable.html
   */
  public boolean isComplexUpdate() {
    return !deletedFields.isEmpty() ||
           !modifiedFields.isEmpty() ||
           keysWereUpdated() ||
           containsUninitializedNotNullFields ||
           containsRenamedAndModifiedFields;
  }

  /**
   * Compares whether two fields have the same attributes (i.e. affinity, not null property, default value).
   */
  private static boolean isFieldStructureTheSame(@NotNull FieldBundle oldField, @NotNull FieldBundle newField) {
    return oldField.isNonNull() == newField.isNonNull() &&
           Objects.equals(oldField.getAffinity(), newField.getAffinity()) &&
           Objects.equals(oldField.getDefaultValue(), newField.getDefaultValue());
  }
}