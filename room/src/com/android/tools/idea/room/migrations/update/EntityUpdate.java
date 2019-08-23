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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the differences between two version of Room database Entity.
 */
public class EntityUpdate {
  private String tableName;
  private List<FieldBundle> deletedFields;
  private List<FieldBundle> newFields;
  private List<FieldBundle> modifiedFields;
  private List<FieldBundle> unmodifiedFields;
  private List<IndexBundle> deletedIndices;
  private List<IndexBundle> newOrModifiedIndices;
  private List<IndexBundle> unmodifiedIndices;
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
    deletedFields = new ArrayList<>();
    newFields = new ArrayList<>();
    modifiedFields = new ArrayList<>();
    unmodifiedFields = new ArrayList<>();
    deletedIndices = new ArrayList<>();
    newOrModifiedIndices = new ArrayList<>();
    unmodifiedIndices = new ArrayList<>();

    Map<String, FieldBundle> oldEntityFields = new HashMap<>(oldEntity.getFieldsByColumnName());
    for (FieldBundle newField : newEntity.getFields()) {
      if (oldEntityFields.containsKey(newField.getColumnName())) {
        FieldBundle oldField = oldEntityFields.remove(newField.getColumnName());
        if (!oldField.isSchemaEqual(newField)) {
          modifiedFields.add(newField);
        }
        else {
          unmodifiedFields.add(newField);
        }
      }
      else {
        newFields.add(newField);
      }
    }
    deletedFields.addAll(oldEntityFields.values());

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
  }

  @NotNull
  public List<FieldBundle> getNewFields() {
    return newFields;
  }

  @NotNull
  public List<FieldBundle> getModifiedFields() {
    return modifiedFields;
  }

  @NotNull
  public List<FieldBundle> getUnmodifiedFields() {
    return unmodifiedFields;
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
    return !deletedFields.isEmpty() || !modifiedFields.isEmpty() || keysWereUpdated();
  }
}
