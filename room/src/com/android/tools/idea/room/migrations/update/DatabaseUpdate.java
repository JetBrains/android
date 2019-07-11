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

import com.android.tools.idea.room.migrations.json.DatabaseBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the differences between two version of a Room database.
 */
public class DatabaseUpdate {
  private Map<String, EntityBundle> newEntities;
  private Map<String, EntityBundle> deletedEntities;
  private Map<String, EntityUpdate> modifiedEntities;
  private int currentVersion;
  private int previousVersion;

  /**
   * @param oldDatabase older version of the database
   * @param newDatabase current version of the database
   */
  public DatabaseUpdate(@NotNull DatabaseBundle oldDatabase, @NotNull DatabaseBundle newDatabase) {
    previousVersion = oldDatabase.getVersion();
    currentVersion = newDatabase.getVersion();
    deletedEntities = new HashMap<>(oldDatabase.getEntitiesByTableName());
    newEntities = new HashMap<>();
    modifiedEntities = new HashMap<>();

    for (EntityBundle newEntity : newDatabase.getEntities()) {
      if (deletedEntities.containsKey(newEntity.getTableName())) {
        EntityBundle oldEntity = deletedEntities.remove(newEntity.getTableName());
        if (!oldEntity.isSchemaEqual(newEntity)) {
          modifiedEntities.put(oldEntity.getTableName(),  new EntityUpdate(oldEntity, newEntity));
        }
      } else {
        newEntities.put(newEntity.getTableName(), newEntity);
      }
    }
  }

  @NotNull
  public Map<String, EntityUpdate> getModifiedEntities() {
    return modifiedEntities;
  }

  @NotNull
  public Map<String, EntityBundle> getNewEntities() {
    return newEntities;
  }

  @NotNull
  public Map<String, EntityBundle> getDeletedEntities() {
    return deletedEntities;
  }

  public int getCurrentVersion() {
    return currentVersion;
  }

  public int getPreviousVersion() {
    return previousVersion;
  }
}
