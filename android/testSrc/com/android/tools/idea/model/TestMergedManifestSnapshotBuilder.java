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
package com.android.tools.idea.model;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.manifmerger.Actions;
import com.android.sdklib.AndroidVersion;
import com.android.tools.dom.ActivityAttributesSnapshot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@SuppressWarnings("unused")
public class TestMergedManifestSnapshotBuilder {
  @NotNull private final Module myModule;
  @Nullable private String myName;
  @Nullable private Integer myVersionCode;
  @Nullable private String myTheme;
  @Nullable private ImmutableMap<String, ActivityAttributesSnapshot> myAttributes;
  @Nullable private MergedManifestInfo myMergedManifestInfo;
  @Nullable private AndroidVersion myTargetSdk;
  @Nullable private ResourceValue myIcon;
  @Nullable private ResourceValue myLabel;
  private boolean myRtl;
  @Nullable private Boolean myDebuggable;
  @Nullable private Document myDocument;
  @Nullable private ImmutableList<VirtualFile> myFiles;
  @Nullable private ImmutablePermissionHolder myPermissions;
  @Nullable private ImmutableList<Element> myActivities;
  @Nullable private ImmutableList<Element> myServices;
  @Nullable private Actions myActions;
  @Nullable private AndroidVersion myMinSdk;
  private boolean myIsValid;
  private Exception myException;


  private TestMergedManifestSnapshotBuilder(@NotNull Module module) {
    myModule = module;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setActions(@NotNull Actions actions) {
    myActions = actions;
    return this;
  }

  @NotNull
  public static TestMergedManifestSnapshotBuilder builder(@NotNull Module module) {
    return new TestMergedManifestSnapshotBuilder(module);
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setPackageName(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setVersionCode(@NotNull Integer code) {
    myVersionCode = code;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setManifestTheme(@NotNull String theme) {
    myTheme = theme;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setActivityAttributes(@NotNull Map<String, ActivityAttributesSnapshot> attributes) {
    myAttributes = ImmutableMap.copyOf(attributes);
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setMergedManifestInfo(MergedManifestInfo mergedManifestInfo) {
    myMergedManifestInfo = mergedManifestInfo;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setMinSdk(@NotNull AndroidVersion sdk) {
    myMinSdk = sdk;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setTargetSdk(@NotNull AndroidVersion sdk) {
    myTargetSdk = sdk;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setAppIcon(@NotNull ResourceValue icon) {
    myIcon = icon;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setAppLabel(@NotNull ResourceValue label) {
    myLabel = label;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setSupportsRtl(boolean rtl) {
    myRtl = rtl;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setIsDebuggable(@NotNull Boolean debuggable) {
    myDebuggable = debuggable;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setDocument(@NotNull Document document) {
    myDocument = document;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setManifestFiles(List<VirtualFile> files) {
    myFiles = ImmutableList.copyOf(files);
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setPermissionsHolder(ImmutablePermissionHolder permissions) {
    myPermissions = permissions;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setActivities(@NotNull List<Element> activities) {
    myActivities = ImmutableList.copyOf(activities);
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setServices(@NotNull List<Element> services) {
    myServices = ImmutableList.copyOf(services);
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setIsValid(boolean isValid) {
    myIsValid = isValid;
    return this;
  }

  @NotNull
  public TestMergedManifestSnapshotBuilder setIsValid(Exception exception) {
    myException = exception;
    return this;
  }

  @NotNull
  public MergedManifestSnapshot build() {
    return new MergedManifestSnapshot(myModule,
                                      myName, myVersionCode, myTheme,
                                      myAttributes != null ? myAttributes : ImmutableMap.of(),
                                      myMergedManifestInfo,
                                      myMinSdk != null ? myMinSdk : AndroidVersion.DEFAULT,
                                      myTargetSdk != null ? myTargetSdk : AndroidVersion.DEFAULT,
                                      myIcon, myLabel, myRtl,
                                      myDebuggable,
                                      myDocument,
                                      myFiles != null ? myFiles : ImmutableList.of(),
                                      myPermissions != null ? myPermissions : ImmutablePermissionHolder.EMPTY,
                                      myActivities != null ? myActivities : ImmutableList.of(),
                                      myServices != null ? myServices : ImmutableList.of(),
                                      myActions,
                                      myIsValid,
                                      myException);
  }
}