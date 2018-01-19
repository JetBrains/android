/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.facet;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.AndroidImportableProperty;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.databinding.DataBindingUtil.refreshDataBindingStatus;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFacetConfiguration implements FacetConfiguration, PersistentStateComponent<JpsAndroidModuleProperties> {
  private static final FacetEditorTab[] NO_EDITOR_TABS = new FacetEditorTab[0];

  private AndroidFacet myFacet = null;

  private JpsAndroidModuleProperties myProperties = new JpsAndroidModuleProperties();

  @Nullable private AndroidModel myAndroidModel;

  public void init(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    init(module, contentRoot.getPath());
  }

  public void init(@NotNull Module module, @NotNull String baseDirectoryPath) {
    final String s = AndroidRootUtil.getPathRelativeToModuleDir(module, baseDirectoryPath);
    if (s == null || s.isEmpty()) {
      return;
    }
    myProperties.GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_APT;
    myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + myProperties.GEN_FOLDER_RELATIVE_PATH_AIDL;
    myProperties.MANIFEST_FILE_RELATIVE_PATH = '/' + s + myProperties.MANIFEST_FILE_RELATIVE_PATH;
    myProperties.RES_FOLDER_RELATIVE_PATH = '/' + s + myProperties.RES_FOLDER_RELATIVE_PATH;
    myProperties.ASSETS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.ASSETS_FOLDER_RELATIVE_PATH;
    myProperties.LIBS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.LIBS_FOLDER_RELATIVE_PATH;
    myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH = '/' + s + myProperties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH;

    for (int i = 0; i < myProperties.RES_OVERLAY_FOLDERS.size(); i++) {
      myProperties.RES_OVERLAY_FOLDERS.set(i, '/' + s + myProperties.RES_OVERLAY_FOLDERS.get(i));
    }
  }

  @Nullable
  public AndroidPlatform getAndroidPlatform() {
    return AndroidPlatform.getInstance(myFacet.getModule());
  }

  @Nullable
  public AndroidSdkData getAndroidSdk() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getSdkData() : null;
  }

  @Nullable
  public IAndroidTarget getAndroidTarget() {
    AndroidPlatform platform = getAndroidPlatform();
    return platform != null ? platform.getTarget() : null;
  }

  public void setFacet(@NotNull AndroidFacet facet) {
    myFacet = facet;
    facet.getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
  }

  @Override
  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    JpsAndroidModuleProperties state = getState();
    assert state != null;
    if (state.ALLOW_USER_CONFIGURATION) {
      return new FacetEditorTab[]{new AndroidFacetEditorTab(editorContext, this)};
    }
    return NO_EDITOR_TABS;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void setAdditionalNativeLibraries(@NotNull List<AndroidNativeLibData> additionalNativeLibraries) {
    myProperties.myNativeLibs = new ArrayList<>(additionalNativeLibraries.size());

    for (AndroidNativeLibData lib : additionalNativeLibraries) {
      JpsAndroidModuleProperties.AndroidNativeLibDataEntry data = new JpsAndroidModuleProperties.AndroidNativeLibDataEntry();
      data.myArchitecture = lib.getArchitecture();
      data.myUrl = VfsUtilCore.pathToUrl(lib.getPath());
      data.myTargetFileName = lib.getTargetFileName();
      myProperties.myNativeLibs.add(data);
    }
  }

  public boolean isImportedProperty(@NotNull AndroidImportableProperty property) {
    return !myProperties.myNotImportedProperties.contains(property);
  }

  public boolean isIncludeAssetsFromLibraries() {
    return myProperties.myIncludeAssetsFromLibraries;
  }

  public void setIncludeAssetsFromLibraries(boolean includeAssetsFromLibraries) {
    myProperties.myIncludeAssetsFromLibraries = includeAssetsFromLibraries;
  }

  @Nullable
  @Override
  public JpsAndroidModuleProperties getState() {
    return myProperties;
  }

  @Override
  public void loadState(JpsAndroidModuleProperties properties) {
    myProperties = properties;
  }

  public void setModel(@Nullable AndroidModel model) {
    myAndroidModel = model;
    if (myFacet != null) {
      refreshDataBindingStatus(myFacet);
      myFacet.getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(myFacet);
    }
  }

  @Nullable
  public AndroidModel getModel() {
    return myAndroidModel;
  }

  /**
   * Invoked when the facet is disposed. Nulls out fields to facilitate garbage collection.
   */
  public void disposeFacet() {
    myAndroidModel = null;
  }
}
