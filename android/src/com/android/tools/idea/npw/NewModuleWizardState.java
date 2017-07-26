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

import com.android.SdkConstants;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.templates.KeystoreUtils;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.templates.SupportLibrary;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Deprecated. Use {@link ScopedStateStore} instead.
 */
@Deprecated
public class NewModuleWizardState extends TemplateWizardState {
  private static final Logger LOG = Logger.getInstance(NewModuleWizardState.class);

  public static final String ATTR_PROJECT_LOCATION = "projectLocation";
  public static final String MODULE_IMPORT_NAME = "Import Existing Project";
  public static final String ARCHIVE_IMPORT_NAME = "Import .JAR or .AAR Package";
  /**
   * State for the template wizard, used to embed an activity template
   */
  private final TemplateWizardState myActivityTemplateState;

  public NewModuleWizardState() {
    myActivityTemplateState = new TemplateWizardState();

    myHidden.add(ATTR_PROJECT_LOCATION);
    myHidden.remove(ATTR_IS_LIBRARY_MODULE);

    put(ATTR_IS_LAUNCHER, false);
    put(ATTR_CREATE_ICONS, false);
    put(ATTR_IS_NEW_PROJECT, true);
    put(ATTR_THEME_EXISTS, true);
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

  private void putSdkDependentParams() {
    final AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(new StudioLoggerProgressIndicator(getClass()), false);
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
   * Updates the dependencies stored in the parameters map, to include support libraries required by the extra features selected.
   */
  public void updateDependencies() {
    @SuppressWarnings("unchecked") SetMultimap<String, String> dependencies = (SetMultimap<String, String>)get(ATTR_DEPENDENCIES_MULTIMAP);
    if (dependencies == null) {
      dependencies = HashMultimap.create();
    }

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    // Support Library
    Object fragmentsExtra = get(ATTR_FRAGMENTS_EXTRA);
    Object navigationDrawerExtra = get(ATTR_NAVIGATION_DRAWER_EXTRA);
    if ((fragmentsExtra != null && Boolean.parseBoolean(fragmentsExtra.toString())) ||
        (navigationDrawerExtra != null && Boolean.parseBoolean(navigationDrawerExtra.toString()))) {
      dependencies.put(SdkConstants.GRADLE_COMPILE_CONFIGURATION,  urlManager.getLibraryStringCoordinate(SupportLibrary.SUPPORT_V4, true));
    }

    // AppCompat Library
    Object actionBarExtra = get(ATTR_ACTION_BAR_EXTRA);
    if (actionBarExtra != null && Boolean.parseBoolean(actionBarExtra.toString())) {
      dependencies.put(SdkConstants.GRADLE_COMPILE_CONFIGURATION, urlManager.getLibraryStringCoordinate(SupportLibrary.APP_COMPAT_V7,
                                                                                                        true));
    }

    // GridLayout Library
    Object gridLayoutExtra = get(ATTR_GRID_LAYOUT_EXTRA);
    if (gridLayoutExtra != null && Boolean.parseBoolean(gridLayoutExtra.toString())) {
      dependencies.put(SdkConstants.GRADLE_COMPILE_CONFIGURATION, urlManager.getLibraryStringCoordinate(SupportLibrary.GRID_LAYOUT_V7,
                                                                                                        true));
    }

    put(ATTR_DEPENDENCIES_MULTIMAP, dependencies);
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

  private static void copyParameters(@NotNull Map<String, Object> from, @NotNull Map<String, Object> to, String... keys) {
    for (String key : keys) {
      to.put(key, from.get(key));
    }
  }
}
