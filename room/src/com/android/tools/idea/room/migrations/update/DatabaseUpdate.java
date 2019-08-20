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

import static com.android.tools.idea.room.migrations.update.SchemaDiffUtil.*;

import com.android.tools.idea.room.migrations.json.DatabaseBundle;
import com.android.tools.idea.room.migrations.json.DatabaseViewBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FtsEntityBundle;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * Holds the differences between two version of a Room database.
 */
public class DatabaseUpdate {
  private Map<String, EntityBundle> newEntities;
  private Map<String, EntityBundle> deletedEntities;
  private Map<String, EntityUpdate> modifiedEntities;

  private Map<String, String> renamedEntities;

  private List<DatabaseViewBundle> deletedViews;
  private List<DatabaseViewBundle> newOrModifiedViews;

  private int currentVersion;
  private int previousVersion;

  /**
   * @param oldDatabase older version of the database
   * @param newDatabase current version of the database
   */
  public DatabaseUpdate(@NotNull DatabaseBundle oldDatabase, @NotNull DatabaseBundle newDatabase) {
    checkDatabase(oldDatabase);
    checkDatabase(newDatabase);

    previousVersion = oldDatabase.getVersion();
    currentVersion = newDatabase.getVersion();

    deletedEntities = new HashMap<>(oldDatabase.getEntitiesByTableName());
    newEntities = new HashMap<>();
    modifiedEntities = new HashMap<>();
    renamedEntities = new HashMap<>();

    for (EntityBundle newEntity : newDatabase.getEntities()) {
      EntityBundle oldEntity = deletedEntities.remove(newEntity.getTableName());
        if (oldEntity != null) {
          if (!isTableTypeTheSame(oldEntity, newEntity)) {
            deletedEntities.put(oldEntity.getTableName(), oldEntity);
            newEntities.put(newEntity.getTableName(), newEntity);
          } else {
            if (!oldEntity.isSchemaEqual(newEntity)) {
              modifiedEntities.put(newEntity.getTableName(), new EntityUpdate(oldEntity, newEntity));
            }
          }
        }
        else {
          newEntities.put(newEntity.getTableName(), newEntity);
        }
    }

    deletedViews = new ArrayList<>();
    newOrModifiedViews = new ArrayList<>();

    if (oldDatabase.getViews().isEmpty()) {
      if (!newDatabase.getViews().isEmpty()) {
        newOrModifiedViews.addAll(newDatabase.getViews());
      }
    } else if (newDatabase.getViews().isEmpty()) {
      deletedViews.addAll(oldDatabase.getViews());
    } else {
      Map<String, DatabaseViewBundle> oldViews =
        oldDatabase.getViews().stream().collect(Collectors.toMap(DatabaseViewBundle::getViewName, view -> view));
      for (DatabaseViewBundle newView : newDatabase.getViews()) {
        DatabaseViewBundle oldView = oldViews.get(newView.getViewName());
        if (oldView != null) {
          if (!oldView.isSchemaEqual(newView)) {
            newOrModifiedViews.add(newView);
          } else {
            oldViews.remove(newView.getViewName());
          }
        } else {
          newOrModifiedViews.add(newView);
        }
      }
      deletedViews.addAll(oldViews.values());
    }
  }

  /**
   * Returns a mapping between the name of a modified table and the EntityUpdate object which describes the changes which were
   * performed on that table. If the update renames the table, the new name is used as key.
   */
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

  /**
   * Returns a mapping form the old name of the table to the new one.
   *
   * <p>It only provides information regarding the tables which only need to be renamed and feature no other changes.</p>
   */
  @NotNull
  public Map<String, String> getRenamedEntities() {
    return renamedEntities;
  }

  @NotNull
  public List<DatabaseViewBundle> getNewOrModifiedViews() {
    return newOrModifiedViews;
  }

  @NotNull
  public List<DatabaseViewBundle> getDeletedViews() {
    return deletedViews;
  }

  public int getCurrentVersion() {
    return currentVersion;
  }

  public int getPreviousVersion() {
    return previousVersion;
  }

  /**
   * Separates the renamed tables from the deleted/newly added tables based on user input.
   * @param oldToNewNameMapping mapping from the old name of a entity to the actual name
   */
  public void applyRenameMapping(@NotNull Map<String, String> oldToNewNameMapping) {
    for (Map.Entry<String, String> tableNames : oldToNewNameMapping.entrySet()) {
      EntityBundle oldEntity = deletedEntities.remove(tableNames.getKey());
      EntityBundle newEntity = newEntities.remove(tableNames.getValue());

      if (oldEntity == null || newEntity == null) {
        throw  new IllegalArgumentException("Invalid old table name to new table name mapping");
      }

      // If the table structure remains the same after the update and the resulting new table is not an fts table which
      // requires external content, we can simply rename it without needing to recreate it.
      if (isTableStructureTheSame(oldEntity, newEntity) &&
          !(newEntity instanceof FtsEntityBundle && ftsTableNeedsExternalContentSource((FtsEntityBundle)newEntity))) {
        renamedEntities.put(tableNames.getKey(), tableNames.getValue());
      } else {
        modifiedEntities.put(newEntity.getTableName(), new EntityUpdate(oldEntity, newEntity));
      }
    }
  }

  private static void checkDatabase(@NotNull DatabaseBundle databaseBundle) {
    Preconditions.checkArgument(databaseBundle.getEntities() != null,
                                "Invalid DatabaseBundle object: the list of entities is null.");
    Preconditions.checkArgument(databaseBundle.getViews() != null,
                                "Invalid DatabaseBundle object: the list of views is null.");
  }
}
