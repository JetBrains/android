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
import com.android.tools.idea.room.migrations.json.FtsEntityBundle;
import com.android.tools.idea.room.migrations.json.FtsOptionsBundle;
import com.android.tools.idea.room.migrations.json.IndexBundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class which offers the possibility to compare the structure of fields or entities.
 */
public final class SchemaDiffUtil {
  /**
   * Checks whether two versions of the same field have the same attributes (i.e. affinity, not null property, default value).
   */
  public static boolean isFieldStructureTheSame(@NotNull FieldBundle oldField, @NotNull FieldBundle newField) {
    return oldField.isNonNull() == newField.isNonNull() &&
           Objects.equals(oldField.getAffinity(), newField.getAffinity()) &&
           Objects.equals(oldField.getDefaultValue(), newField.getDefaultValue());
  }

  /**
   * Checks whether two versions of the same entity have the same structure (i.e. same columns, key constraints, indices).
   */
  public static boolean isTableStructureTheSame(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    if (!isTableTypeTheSame(oldEntity, newEntity)) {
      return false;
    }

    if (!oldEntity.getPrimaryKey().isSchemaEqual(newEntity.getPrimaryKey())) {
      return false;
    }

    return tablesHaveSameColumns(oldEntity, newEntity) &&
           tablesHaveSameForeignKeyConstraints(oldEntity, newEntity) &&
           tablesHaveSameIndices(oldEntity, newEntity);
  }

  public static boolean isTableTypeTheSame(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    return ((oldEntity instanceof FtsEntityBundle) == (newEntity instanceof FtsEntityBundle));
  }

  public static boolean tablesHaveSameColumns(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    Map<String, FieldBundle> oldEntityFields = oldEntity.getFieldsByColumnName();
    Map<String, FieldBundle> newEntityFields = newEntity.getFieldsByColumnName();
    List<FieldBundle> matchedFields = new ArrayList<>();

    if (oldEntityFields.size() != newEntityFields.size()) {
      return false;
    }

    for (Map.Entry<String, FieldBundle> oldFieldEntry : oldEntityFields.entrySet()) {
      FieldBundle newField = newEntityFields.get(oldFieldEntry.getKey());
      if (newField == null || !oldFieldEntry.getValue().isSchemaEqual(newField)) {
        return false;
      } else {
        matchedFields.add(newField);
      }
    }

    return newEntityFields.values().size() == matchedFields.size();
  }

  public static boolean tablesHaveSameForeignKeyConstraints(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    List<ForeignKeyBundle> oldEntityForeignKeys = oldEntity.getForeignKeys();
    List<ForeignKeyBundle> newEntityForeignKeys = newEntity.getForeignKeys();
    List<ForeignKeyBundle> matchedKeys = new ArrayList<>();

    if (oldEntityForeignKeys.size() != newEntityForeignKeys.size()) {
      return false;
    }

    for (ForeignKeyBundle oldKey : oldEntityForeignKeys) {
      boolean foundMatchingNewKey = false;
      for (ForeignKeyBundle newKey : newEntityForeignKeys) {
        if (oldKey.isSchemaEqual(newKey)) {
          foundMatchingNewKey = true;
          matchedKeys.add(newKey);
          break;
        }
      }

      if (!foundMatchingNewKey) {
        return false;
      }
    }

    return newEntityForeignKeys.size() == matchedKeys.size();
  }

  public static boolean tablesHaveSameIndices(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    List<IndexBundle> oldEntityIndices = oldEntity.getIndices();
    Map<String, IndexBundle> newEntityIndies = newEntity.getIndices().stream()
      .collect(Collectors.toMap(IndexBundle::getName, index -> index));
    List<IndexBundle> matchedIndices = new ArrayList<>();

    if (oldEntityIndices.size() != newEntityIndies.size()) {
      return false;
    }

    for (IndexBundle oldIndex : oldEntityIndices) {
      IndexBundle newIndex = newEntityIndies.get(oldIndex.getName());
      if (newIndex == null || !oldIndex.isSchemaEqual(newIndex)) {
        return false;
      } else {
        matchedIndices.add(newIndex);
      }
    }

    return newEntityIndies.values().size() == matchedIndices.size();
  }

  public static boolean ftsTableNeedsExternalContentSource(@NotNull FtsEntityBundle ftsEntity) {
    FtsOptionsBundle ftsOptions = ftsEntity.getFtsOptions();
    if (ftsOptions != null) {
      String contentTable = ftsOptions.getContentTable();
      if (contentTable != null && !contentTable.isEmpty()) {
        return true;
      }
    }

    return false;
  }
}
