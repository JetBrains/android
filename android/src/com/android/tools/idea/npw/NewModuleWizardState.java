/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.npw;

import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.RepositoryUrlManager.*;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Value object which holds the current state of the wizard pages for the
 * {@link NewModuleWizard}
 *
 * Deprecated. Use {@link ScopedStateStore} instead.
 */
@Deprecated
public class NewModuleWizardState extends TemplateWizardState {
  private static final Logger LOG = Logger.getInstance(NewModuleWizardState.class);

  public static final String ATTR_CREATE_ACTIVITY = "createActivity";
  public static final String ATTR_PROJECT_LOCATION = "projectLocation";
  public static final String APP_NAME = "app";
  public static final String LIB_NAME = "lib";
  public static final String MODULE_IMPORT_NAME = "Import Existing Project";
  public static final String ARCHIVE_IMPORT_NAME = "Import .JAR or .AAR Package";
  /**
   * State for the template wizard, used to embed an activity template
   */
  protected final TemplateWizardState myActivityTemplateState;
  /**
   * Modules that will be imported.
   */
  private Map<String, VirtualFile> myModulesToImport;
  /**
   * Map from a template name to the wizard path that has provided this template.
   */
  private Map<String, WizardPath> myTemplateToPathMap = new HashMap<String, WizardPath>();
  /**
   * The active wizard path.
   */
  private WizardPath myActiveWizardPath;
  private WizardPath myDefaultPath;

  public NewModuleWizardState() {
    myActivityTemplateState = new TemplateWizardState();

    myHidden.add(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);

    put(ATTR_IS_LAUNCHER, false);
    put(ATTR_CREATE_ICONS, false);
    put(ATTR_IS_NEW_PROJECT, true);
    put(ATTR_CREATE_ACTIVITY, true);
    put(ATTR_IS_LIBRARY_MODULE, false);

    putSdkDependentParams();

    try {
      myActivityTemplateState.put(ATTR_DEBUG_KEYSTORE_SHA1, KeystoreUtils.sha1(getOrCreateDefaultDebugKeystore()));
    }
    catch (Exception e) {
      LOG.info("Could not compute SHA1 hash of debug keystore.", e);
    }

    myActivityTemplateState.myHidden.add(ATTR_PACKAGE_NAME);
    myActivityTemplateState.myHidden.add(ATTR_APP_TITLE);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API);
    myActivityTemplateState.myHidden.add(ATTR_MIN_API_LEVEL);
    myActivityTemplateState.myHidden.add(ATTR_TARGET_API);
    myActivityTemplateState.myHidden.add(ATTR_TARGET_API_STRING);
    myActivityTemplateState.myHidden.add(ATTR_BUILD_API);
    myActivityTemplateState.myHidden.add(ATTR_BUILD_API_STRING);
    myActivityTemplateState.myHidden.add(ATTR_COPY_ICONS);
    myActivityTemplateState.myHidden.add(ATTR_IS_LAUNCHER);
    myActivityTemplateState.myHidden.add(ATTR_PARENT_ACTIVITY_CLASS);
    myActivityTemplateState.myHidden.add(ATTR_ACTIVITY_TITLE);

    updateParameters();
  }

  public static boolean isAndroidTemplate(@Nullable TemplateMetadata templateMetadata) {
    return templateMetadata != null && templateMetadata.getParameter(ATTR_MIN_API) != null;
  }

  public void setDefaultWizardPath(WizardPath defaultPath) {
    myDefaultPath = defaultPath;
  }

  public void templateChanged(@Nullable Project project, String templateName) {
    setTemplateName(templateName);

    if (templateName.equals(TemplateWizardModuleBuilder.LIB_TEMPLATE_NAME)) {
      put(ATTR_IS_LIBRARY_MODULE, true);
      put(ATTR_IS_LAUNCHER, false);
      put(ATTR_CREATE_ICONS, false);
      // Hide the create icons checkbox
      myHidden.add(ATTR_CREATE_ICONS);
    } else if (templateName.equals(TemplateWizardModuleBuilder.APP_TEMPLATE_NAME)) {
      put(ATTR_IS_LIBRARY_MODULE, false);
      put(ATTR_IS_LAUNCHER, true);
      put(ATTR_CREATE_ICONS, true);
      // Show the create icons checkbox
      myHidden.remove(ATTR_CREATE_ICONS);
    }

    if (myHidden.contains(ATTR_APP_TITLE)) {
      // If the app title is hidden, set it to the existing app title
      assert project != null : "Cannot be adding a module to a unknown project";
      put(ATTR_APP_TITLE, project.getName());
    }
  }

  private void setTemplateName(@Nullable String templateName) {
    myActiveWizardPath = myTemplateToPathMap.get(templateName);
  }

  @NotNull
  public WizardPath getActiveWizardPath() {
    return myActiveWizardPath != null ? myActiveWizardPath : myDefaultPath;
  }

  public void putSdkDependentParams() {
    final AndroidSdkHandler sdkHandler = AndroidSdkUtils.tryToChooseSdkHandler();
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(getClass()));
    if (buildTool != null) {
      // If buildTool is null, the template will use buildApi instead, which might be good enough.
      put(ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    }

    File location = sdkHandler.getLocation();
    if (location != null) {
      // Gradle expects a platform-neutral path
      put(ATTR_SDK_DIR, FileUtil.toSystemIndependentName(location.getPath()));
    }
  }

  @NotNull
  public TemplateWizardState getActivityTemplateState() {
    return myActivityTemplateState;
  }

  /**
   * Call this to update the list of dependencies to be compiled into the template
   */
  public void updateDependencies() {
    // Take care of dependencies selected through the wizard
    Set<String> dependencySet = new HashSet<String>();
    if (myParameters.containsKey(ATTR_DEPENDENCIES_LIST)) {
      dependencySet.addAll((Collection<String>)get(ATTR_DEPENDENCIES_LIST));
    }

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    // Support Library
    if ((get(ATTR_FRAGMENTS_EXTRA) != null && Boolean.parseBoolean(get(ATTR_FRAGMENTS_EXTRA).toString())) ||
        (get(ATTR_NAVIGATION_DRAWER_EXTRA) != null && Boolean.parseBoolean(get(ATTR_NAVIGATION_DRAWER_EXTRA).toString()))) {
      dependencySet.add(urlManager.getLibraryCoordinate(SUPPORT_ID_V4));
    }

    // AppCompat Library
    if (get(ATTR_ACTION_BAR_EXTRA) != null && Boolean.parseBoolean(get(ATTR_ACTION_BAR_EXTRA).toString())) {
      dependencySet.add(urlManager.getLibraryCoordinate(APP_COMPAT_ID_V7));
    }

    // GridLayout Library
    if (get(ATTR_GRID_LAYOUT_EXTRA) != null && Boolean.parseBoolean(get(ATTR_GRID_LAYOUT_EXTRA).toString())) {
      dependencySet.add(urlManager.getLibraryCoordinate(GRID_LAYOUT_ID_V7));
    }

    put(ATTR_DEPENDENCIES_LIST, new LinkedList<String>(dependencySet));
  }

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  public void updateParameters() {
    put(ATTR_COPY_ICONS, !Boolean.parseBoolean(get(ATTR_CREATE_ICONS).toString()));
    copyParameters(myParameters, myActivityTemplateState.myParameters, ATTR_PACKAGE_NAME, ATTR_APP_TITLE, ATTR_MIN_API, ATTR_MIN_API_LEVEL,
                   ATTR_TARGET_API, ATTR_TARGET_API_STRING, ATTR_BUILD_API_STRING,
                   ATTR_BUILD_API, ATTR_COPY_ICONS, ATTR_IS_NEW_PROJECT, ATTR_IS_LAUNCHER, ATTR_CREATE_ACTIVITY,
                   ATTR_CREATE_ICONS, ATTR_IS_GRADLE,
                   ATTR_TOP_OUT, ATTR_PROJECT_OUT, ATTR_SRC_OUT, ATTR_TEST_OUT, ATTR_RES_OUT, ATTR_MANIFEST_OUT);
  }

  protected void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }

  @Nullable
  public Map<String, VirtualFile> getModulesToImport() {
    return myModulesToImport;
  }

  public void setModulesToImport(@Nullable Map<String, VirtualFile> modulesToImport) {
    myModulesToImport = modulesToImport;
  }

  public void associateTemplateWithPath(String templateName, WizardPath path) {
    myTemplateToPathMap.put(templateName, path);
    // Initialize to the first existing wizard path by default.
    if (myActiveWizardPath == null) {
      myActiveWizardPath = path;
    }
  }

  public boolean isOnDefaultWizardPath() {
    return myActiveWizardPath == myDefaultPath;
  }
}
