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
package com.android.tools.idea.room.update;

import com.android.tools.idea.room.bundle.EntityBundle;
import com.android.tools.idea.room.bundle.FieldBundle;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the differences between two version of Room database Entity.
 */
public class EntityUpdate {
  private String tableName;
  private Map<String, FieldBundle> deletedFields;
  private Map<String, FieldBundle> newFields;

  /**
   * @param oldEntity entity description from an older version of the database
   * @param newEntity entity description from the current version of the database
   */
  public EntityUpdate(@NotNull EntityBundle oldEntity, @NotNull EntityBundle newEntity) {
    tableName = oldEntity.getTableName();
    deletedFields = new HashMap<>(oldEntity.getFieldsByColumnName());
    newFields = new HashMap<>();

    for (FieldBundle newField : newEntity.getFields()) {
      if (deletedFields.containsKey(newField.getColumnName())) {
        deletedFields.remove(newField.getColumnName());
      } else {
        newFields.put(newField.getColumnName(), newField);
      }
    }
  }

  /**
   * Returns a human readable format of the changes
   */
  @NotNull
  public String getDiff() {
    if (deletedFields.isEmpty() && newFields.isEmpty()) {
      return "";
    }
    StringBuilder diff = new StringBuilder(String.format("Table %s was modified:\n", tableName));

    for (String columnName : deletedFields.keySet()) {
      diff.append(String.format("\tDeleted column %s\n", columnName));
    }

    for (String columnName : newFields.keySet()) {
      diff.append(String.format("\tAdded column %s\n", columnName));
    }

    return diff.toString();
  }
}
