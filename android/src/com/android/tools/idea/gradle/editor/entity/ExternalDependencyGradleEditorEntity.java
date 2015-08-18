/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.entity;

import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.value.GradleEditorEntityValueManager;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * {@link GradleEditorEntity} which holds information about external dependency.
 */
public class ExternalDependencyGradleEditorEntity extends AbstractGradleEditorEntity
  implements GradleEntityDeclarationValueLocationAware, GradleEntityDefinitionValueLocationAware {

  @NotNull private String myScope;
  @NotNull private final List<GradleEditorSourceBinding> myScopeBindings = Lists.newArrayList();

  @NotNull private String myGroupId;
  @NotNull private final List<GradleEditorSourceBinding> myGroupIdSourceBindings = Lists.newArrayList();

  @NotNull private String myArtifactId;
  @NotNull private final List<GradleEditorSourceBinding> myArtifactIdSourceBindings = Lists.newArrayList();

  @NotNull private String myVersion;
  @NotNull private final List<GradleEditorSourceBinding> myVersionSourceBindings = Lists.newArrayList();
  @NotNull private final GradleEditorSourceBinding myVersionDeclarationLocation;

  @NotNull private final GradleEditorEntityValueManager myVersionValueManager;

  public ExternalDependencyGradleEditorEntity(@NotNull String scope,
                                              @NotNull List<GradleEditorSourceBinding> scopeSourceBindings,
                                              @NotNull String groupId,
                                              @NotNull List<GradleEditorSourceBinding> groupIdSourceBindings,
                                              @NotNull String artifactId,
                                              @NotNull List<GradleEditorSourceBinding> artifactIdSourceBindings,
                                              @NotNull String version,
                                              @NotNull List<GradleEditorSourceBinding> versionSourceBindings,
                                              @NotNull GradleEditorSourceBinding entityLocation,
                                              @NotNull GradleEditorSourceBinding versionDeclarationLocation,
                                              @NotNull GradleEditorEntityValueManager versionValueManager,
                                              @NotNull Set<GradleEditorEntityMetaData> metaData) {
    super(entityLocation, metaData, null);
    myScope = scope;
    myScopeBindings.addAll(scopeSourceBindings);
    myGroupId = groupId;
    myGroupIdSourceBindings.addAll(groupIdSourceBindings);
    myArtifactId = artifactId;
    myArtifactIdSourceBindings.addAll(artifactIdSourceBindings);
    myVersion = version;
    myVersionSourceBindings.addAll(versionSourceBindings);
    myVersionDeclarationLocation = versionDeclarationLocation;
    myVersionValueManager = versionValueManager;
  }

  @NotNull
  @Override
  public String getName() {
    return String.format("%s %s:%s:%s", myScope, myGroupId, myArtifactId, myVersion);
  }

  @NotNull
  public String getScope() {
    return myScope;
  }

  @NotNull
  public List<GradleEditorSourceBinding> getScopeBindings() {
    return myScopeBindings;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
  public List<GradleEditorSourceBinding> getGroupIdSourceBindings() {
    return myGroupIdSourceBindings;
  }

  @NotNull
  public String getArtifactId() {
    return myArtifactId;
  }

  @NotNull
  public List<GradleEditorSourceBinding> getArtifactIdSourceBindings() {
    return myArtifactIdSourceBindings;
  }

  @NotNull
  public String getVersion() {
    return myVersion;
  }

  @NotNull
  public List<GradleEditorSourceBinding> getVersionSourceBindings() {
    return myVersionSourceBindings;
  }

  @NotNull
  @Override
  public GradleEditorSourceBinding getDeclarationValueLocation() {
    return myVersionDeclarationLocation;
  }

  @Nullable
  @Override
  public GradleEditorSourceBinding getDefinitionValueLocation() {
    return myVersionSourceBindings.size() == 1 ? myVersionSourceBindings.get(0) : null;
  }

  @NotNull
  public GradleEditorEntityValueManager getVersionValueManager() {
    return myVersionValueManager;
  }

  /**
   * Tries to apply given version to the current entity and {@link #getVersionSourceBindings() backing files}.
   * <p/>
   * Main success scenario here is to show UI for config properties manipulations and flush user-defined values via this method.
   *
   * @param newVersion  new value to use
   * @return            <code>null</code> as an indication that given value has been successfully applied; an error message otherwise
   */
  @Nullable
  public String changeVersion(@NotNull String newVersion) {
    if (newVersion.equals(getVersion())) {
      return null;
    }
    List<GradleEditorSourceBinding> sourceBindings = getVersionSourceBindings();
    if (sourceBindings.size() != 1) {
      return String.format(
        "Can't apply version '%s' to the entity '%s'. Reason: expected the entity to hold only one version source binding "
        + "but it has %d (%s)",
        newVersion, this, sourceBindings.size(), sourceBindings);
    }
    GradleEditorSourceBinding binding = sourceBindings.get(0);
    RangeMarker rangeMarker = binding.getRangeMarker();
    if (!rangeMarker.isValid()) {
      return String.format("Can't apply version '%s' to the entity '%s'. Reason: source file binding is incorrect", newVersion, this);
    }
    myVersion = newVersion;
    rangeMarker.getDocument().replaceString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset(), newVersion);
    return null;
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myVersionDeclarationLocation);
    for (GradleEditorSourceBinding binding : myScopeBindings) {
      Disposer.dispose(binding);
    }
    for (GradleEditorSourceBinding binding : myGroupIdSourceBindings) {
      Disposer.dispose(binding);
    }
    for (GradleEditorSourceBinding binding : myArtifactIdSourceBindings) {
      Disposer.dispose(binding);
    }
    for (GradleEditorSourceBinding binding : myVersionSourceBindings) {
      Disposer.dispose(binding);
    }
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    if (!getMetaData().isEmpty()) {
      buffer.append('[').append(Joiner.on('|').join(getMetaData())).append("] ");
    }
    buffer.append(myScope.isEmpty() ? String.format("<defined %d times>", myScopeBindings.size()) : myScope);
    buffer.append(" ").append(myGroupId.isEmpty() ? String.format("<defined %d times>", myGroupIdSourceBindings.size()) : myGroupId);
    buffer.append(":").append(myArtifactId.isEmpty() ? String.format("<defined %d times>", myArtifactIdSourceBindings.size()) : myArtifactId);
    buffer.append(":").append(myVersion.isEmpty() ? String.format("<defined %d times>", myVersionSourceBindings.size()) : myVersion);
    return buffer.toString();
  }
}
