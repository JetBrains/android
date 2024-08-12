/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary;

import static com.google.common.base.Verify.verify;

import com.android.annotations.Nullable;
import com.android.tools.idea.execution.common.ComponentLaunchOptions;
import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.configuration.ComplicationWatchFaceInfo;
import com.android.tools.idea.run.configuration.DefaultComplicationWatchFaceInfo;
import com.android.tools.idea.run.configuration.execution.ComplicationLaunchOptions;
import com.android.tools.idea.run.configuration.execution.TileLaunchOptions;
import com.android.tools.idea.run.configuration.execution.WatchFaceLaunchOptions;
import com.android.tools.idea.run.editor.AndroidProfilersPanel;
import com.android.tools.idea.run.editor.ProfilerState;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.XmlSerializer;
import java.util.HashMap;
import java.util.Map;
import org.jdom.Element;

/** State specific to the android binary run configuration. */
public final class BlazeAndroidBinaryRunConfigurationState implements RunConfigurationState {
  /** Element name used to group the {@link ProfilerState} settings */
  private static final String PROFILERS_ELEMENT_NAME = "Profilers";

  public static final String LAUNCH_TILE = "launch_tile";

  public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  public static final String DO_NOTHING = "do_nothing";
  public static final String LAUNCH_DEEP_LINK = "launch_deep_link";
  public static final String LAUNCH_COMPLICATION = "launch_complication";
  public static final String LAUNCH_WATCHFACE = "launch_watchface";
  private static final String LAUNCH_OPTIONS_NAME = "LaunchOptions";

  private static final String LAUNCH_METHOD_ATTR = "launch-method";
  // Remove once v2 becomes default.
  private static final String USE_SPLIT_APKS_IF_POSSIBLE = "use-split-apks-if-possible";

  private static final String WORK_PROFILE_ATTR = "use-work-profile-if-present";
  private static final String USER_ID_ATTR = "user-id";

  private AndroidBinaryLaunchMethod launchMethod = AndroidBinaryLaunchMethod.MOBILE_INSTALL;
  private boolean useSplitApksIfPossible = false;
  private boolean useWorkProfileIfPresent = false;
  private Integer userId;

  private static final String SHOW_LOGCAT_AUTOMATICALLY = "show-logcat-automatically";
  private boolean showLogcatAutomatically = false;
  private ProfilerState profilerState;

  private static final String DEEP_LINK = "DEEP_LINK";
  private static final String ACTIVITY_CLASS = "ACTIVITY_CLASS";
  private static final String MODE = "MODE";
  private String deepLink = "";
  private String activityClass = "";
  private String mode = LAUNCH_DEFAULT_ACTIVITY;

  private static final String AM_START_OPTIONS = "AM_START_OPTIONS";
  private static final String CLEAR_APP_STORAGE = "CLEAR_APP_STORAGE";
  private String amStartOptions = "";

  private boolean clearAppStorage = false;

  private String buildSystem;

  public final ComplicationWatchFaceInfo watchfaceInfo = DefaultComplicationWatchFaceInfo.INSTANCE;

  private final BlazeAndroidRunConfigurationCommonState commonState;
  // TODO: move activity launch here from top level properties.
  private final Map<String, ComponentLaunchOptions> wearLaunchOptions = new HashMap<>();

  BlazeAndroidBinaryRunConfigurationState(String buildSystemName) {
    commonState = new BlazeAndroidRunConfigurationCommonState(buildSystemName);
    profilerState = new ProfilerState();
    buildSystem = buildSystemName;

    wearLaunchOptions.put(LAUNCH_TILE, new TileLaunchOptions());
    wearLaunchOptions.put(LAUNCH_COMPLICATION, new ComplicationLaunchOptions());
    wearLaunchOptions.put(LAUNCH_WATCHFACE, new WatchFaceLaunchOptions());
  }

  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  public AndroidBinaryLaunchMethod getLaunchMethod() {
    return launchMethod;
  }

  /** Returns ComponentLaunchOptions if wear surface as chosen for launch otherwise returns null. */
  @Nullable
  public ComponentLaunchOptions getCurrentWearLaunchOptions() {
    return wearLaunchOptions.get(mode);
  }

  /** Set Wear launch options corresponding to the current mode. */
  public void setWearLaunchOptions(ComponentLaunchOptions options) {
    verify(
        mode.equals(LAUNCH_COMPLICATION)
            || mode.equals(LAUNCH_TILE)
            || mode.equals(LAUNCH_WATCHFACE));
    wearLaunchOptions.put(mode, options);
  }

  @VisibleForTesting
  public void setLaunchMethod(AndroidBinaryLaunchMethod launchMethod) {
    this.launchMethod = launchMethod;
  }

  // This method is deprecated, as unused by mobile-install v2.
  // TODO(b/120300546): Remove once mobile-install v1 is completely deprecated.
  public boolean useSplitApksIfPossible() {
    return useSplitApksIfPossible;
  }

  // This method is deprecated, as unused by mobile-install v2.
  // TODO(b/120300546): Remove once mobile-install v1 is completely deprecated.
  void setUseSplitApksIfPossible(boolean useSplitApksIfPossible) {
    this.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  public boolean useWorkProfileIfPresent() {
    return useWorkProfileIfPresent;
  }

  void setUseWorkProfileIfPresent(boolean useWorkProfileIfPresent) {
    this.useWorkProfileIfPresent = useWorkProfileIfPresent;
  }

  Integer getUserId() {
    return userId;
  }

  void setUserId(Integer userId) {
    this.userId = userId;
  }

  public boolean showLogcatAutomatically() {
    return showLogcatAutomatically;
  }

  public void setShowLogcatAutomatically(boolean showLogcatAutomatically) {
    this.showLogcatAutomatically = showLogcatAutomatically;
  }

  public String getDeepLink() {
    return deepLink;
  }

  public void setDeepLink(String deepLink) {
    this.deepLink = deepLink;
  }

  public String getActivityClass() {
    return activityClass;
  }

  public void setActivityClass(String activityClass) {
    this.activityClass = activityClass;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public ProfilerState getProfilerState() {
    return profilerState;
  }

  public void setAmStartOptions(String amStartOptions) {
    this.amStartOptions = amStartOptions;
  }

  public String getAmStartOptions() {
    return amStartOptions;
  }

  public boolean getClearAppStorage() {
    return clearAppStorage;
  }

  public void setClearAppStorage(boolean clearAppStorage) {
    this.clearAppStorage = clearAppStorage;
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public ImmutableList<ValidationError> validate(Project project) {
    ImmutableList.Builder<ValidationError> errors = ImmutableList.builder();
    errors.addAll(commonState.validate(project));
    if (commonState.isNativeDebuggingEnabled()
        && AndroidBinaryLaunchMethodsUtils.useMobileInstall(launchMethod)) {
      errors.add(
          ValidationError.fatal("Native debugging is not supported when using mobile-install."));
    }

    return errors.build();
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    commonState.readExternal(element);

    // Group profiler settings under its own section.
    Element profilersElement = element.getChild(PROFILERS_ELEMENT_NAME);
    if (profilersElement != null) {
      profilerState.readExternal(profilersElement);
    }

    setDeepLink(Strings.nullToEmpty(element.getAttributeValue(DEEP_LINK)));
    setActivityClass(Strings.nullToEmpty(element.getAttributeValue(ACTIVITY_CLASS)));
    String modeValue = element.getAttributeValue(MODE);
    setMode(Strings.isNullOrEmpty(modeValue) ? LAUNCH_DEFAULT_ACTIVITY : modeValue);
    String launchMethodAttribute = element.getAttributeValue(LAUNCH_METHOD_ATTR);
    if (launchMethodAttribute != null) {
      launchMethod = AndroidBinaryLaunchMethod.valueOf(launchMethodAttribute);
    } else {
      launchMethod = AndroidBinaryLaunchMethod.MOBILE_INSTALL;
    }
    setUseSplitApksIfPossible(
        Boolean.parseBoolean(element.getAttributeValue(USE_SPLIT_APKS_IF_POSSIBLE)));
    setUseWorkProfileIfPresent(Boolean.parseBoolean(element.getAttributeValue(WORK_PROFILE_ATTR)));

    String userIdString = element.getAttributeValue(USER_ID_ATTR);
    if (userIdString != null) {
      setUserId(Integer.parseInt(userIdString));
    }

    setShowLogcatAutomatically(
        Boolean.parseBoolean(element.getAttributeValue(SHOW_LOGCAT_AUTOMATICALLY)));

    String amStartOptionsString = element.getAttributeValue(AM_START_OPTIONS);
    if (amStartOptionsString != null) {
      setAmStartOptions(amStartOptionsString);
    }

    setClearAppStorage(Boolean.parseBoolean(element.getAttributeValue(CLEAR_APP_STORAGE)));

    // Read wear launch options
    Element launchOptionsElement = element.getChild(LAUNCH_OPTIONS_NAME);
    if (launchOptionsElement != null) {
      for (Map.Entry<String, ComponentLaunchOptions> option : wearLaunchOptions.entrySet()) {
        Element optionElement = launchOptionsElement.getChild(option.getKey());
        if (optionElement != null) {
          XmlSerializer.deserializeInto(option.getValue(), optionElement);
        } else {
          throw new VerifyException("Missing launch option declaration " + option.getKey());
        }
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    commonState.writeExternal(element);

    // Group profiler settings under its own section. Previously written profiler info
    // are replaced manually because ProfilerState#writeExternal does not handle the removal
    // process; unlike i.e., implementers of RunConfigurationState.
    Element profilersElement = new Element(PROFILERS_ELEMENT_NAME);
    element.removeChildren(PROFILERS_ELEMENT_NAME);
    element.addContent(profilersElement);
    profilerState.writeExternal(profilersElement);

    element.setAttribute(DEEP_LINK, deepLink);
    element.setAttribute(ACTIVITY_CLASS, activityClass);
    element.setAttribute(MODE, mode);
    element.setAttribute(LAUNCH_METHOD_ATTR, launchMethod.name());
    element.setAttribute(USE_SPLIT_APKS_IF_POSSIBLE, Boolean.toString(useSplitApksIfPossible));
    element.setAttribute(WORK_PROFILE_ATTR, Boolean.toString(useWorkProfileIfPresent));
    element.setAttribute(SHOW_LOGCAT_AUTOMATICALLY, Boolean.toString(showLogcatAutomatically));
    element.setAttribute(AM_START_OPTIONS, amStartOptions);
    element.setAttribute(CLEAR_APP_STORAGE, Boolean.toString(clearAppStorage));

    if (userId != null) {
      element.setAttribute(USER_ID_ATTR, Integer.toString(userId));
    } else {
      element.removeAttribute(USER_ID_ATTR);
    }

    // Add wear launch options
    Element launchOptionsElement = new Element(LAUNCH_OPTIONS_NAME);
    element.removeChildren(LAUNCH_OPTIONS_NAME);
    element.addContent(launchOptionsElement);

    for (Map.Entry<String, ComponentLaunchOptions> option : wearLaunchOptions.entrySet()) {
      Element optionElement = new Element(option.getKey());
      launchOptionsElement.addContent(optionElement);
      XmlSerializer.serializeInto(option.getValue(), optionElement);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeAndroidBinaryRunConfigurationStateEditor(
        commonState.getEditor(project), new AndroidProfilersPanel(project, profilerState), project);
  }

  // Create a deep copy of BlazeAndroidBinaryRunConfigurationState.
  @Override
  public BlazeAndroidBinaryRunConfigurationState clone() {
    BlazeAndroidBinaryRunConfigurationState clone =
        new BlazeAndroidBinaryRunConfigurationState(buildSystem);

    clone.launchMethod = launchMethod;
    clone.profilerState = profilerState;
    clone.deepLink = deepLink;
    clone.activityClass = activityClass;
    clone.mode = mode;
    clone.useSplitApksIfPossible = useSplitApksIfPossible;
    clone.useWorkProfileIfPresent = useWorkProfileIfPresent;
    clone.userId = userId;
    clone.showLogcatAutomatically = showLogcatAutomatically;
    clone.amStartOptions = amStartOptions;
    clone.clearAppStorage = clearAppStorage;
    clone.wearLaunchOptions.put(
        LAUNCH_TILE, ((TileLaunchOptions) wearLaunchOptions.get(LAUNCH_TILE)).clone());
    clone.wearLaunchOptions.put(
        LAUNCH_COMPLICATION,
        ((ComplicationLaunchOptions) wearLaunchOptions.get(LAUNCH_COMPLICATION)).clone());
    clone.wearLaunchOptions.put(
        LAUNCH_WATCHFACE,
        ((WatchFaceLaunchOptions) wearLaunchOptions.get(LAUNCH_WATCHFACE)).clone());
    return clone;
  }
}
