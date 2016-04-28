/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

@State(
  name = "AvailableLibraryUpdateStorage",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
/**
 * Stores available library updates to disk. These stored updates are displayed to the user in the "Project Structure" dialog, until the
 * next scheduled check for updates is executed or until the user manually triggers a check for updates.
 */
public class AvailableLibraryUpdateStorage implements PersistentStateComponent<AvailableLibraryUpdateStorage.AvailableLibraryUpdates> {
  @NotNull public AvailableLibraryUpdates state = new AvailableLibraryUpdates();

  @NotNull
  public static AvailableLibraryUpdateStorage getInstance(Project project) {
    return ServiceManager.getService(project, AvailableLibraryUpdateStorage.class);
  }

  @Override
  @NotNull
  public AvailableLibraryUpdates getState() {
    return state;
  }

  @Override
  public void loadState(AvailableLibraryUpdates state) {
    this.state = state;
    this.state.index();
  }

  public static class AvailableLibraryUpdates {
    @Tag("library-updates")
    @AbstractCollection(surroundWithTag = false)
    @NotNull public List<AvailableLibraryUpdate> updates = Lists.newArrayList();

    @Tag("last-search-timestamp")
    public long lastSearchTimeMillis = -1L;

    @Transient
    @NotNull private final Map<LibraryUpdateId, AvailableLibraryUpdate> myUpdatesById = Maps.newHashMap();

    public void clear() {
      updates.clear();
      myUpdatesById.clear();
    }

    void index() {
      myUpdatesById.clear();
      updates.forEach(this::index);
    }

    public void add(@NotNull FoundArtifact artifact) {
      AvailableLibraryUpdate update = new AvailableLibraryUpdate();
      update.groupId = artifact.getGroupId();
      update.name = artifact.getName();
      update.version = artifact.getVersions().get(0).toString();
      updates.add(update);
      index(update);
    }

    private void index(@NotNull AvailableLibraryUpdate update) {
      myUpdatesById.put(new LibraryUpdateId(update.name, update.groupId), update);
    }

    @Nullable
    public AvailableLibraryUpdate findUpdateFor(@NotNull PsArtifactDependencySpec spec) {
      String version = spec.version;
      if (isNotEmpty(version)) {
        GradleVersion parsedVersion = GradleVersion.tryParse(spec.version);
        if (parsedVersion != null) {
          LibraryUpdateId id = new LibraryUpdateId(spec.name, spec.group);
          AvailableLibraryUpdate update = myUpdatesById.get(id);
          if (update != null) {
            GradleVersion foundVersion = GradleVersion.parse(update.version);
            if (foundVersion.compareTo(parsedVersion) > 0) {
              return update;
            }
          }
        }
      }
      return null;
    }
  }

  @Tag("library-update")
  public static class AvailableLibraryUpdate {
    @Tag("group-id")
    public String groupId;

    @Tag("name")
    public String name;

    @Tag("version")
    public String version;
  }
}
