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
package com.android.tools.idea.run.activity;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryActivitiesFromManifestIndex;
import static com.android.xml.AndroidManifest.NODE_INTENT;

import com.android.SdkConstants;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.IDevice;
import com.android.tools.analytics.UsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.DefaultActivityLocatorStats;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.IntentFilter;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DefaultActivityLocator extends ActivityLocator {
  private static final Logger LOG = Logger.getInstance(DefaultActivityLocator.class);

  @NotNull
  private final AndroidFacet myFacet;

  public DefaultActivityLocator(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  @Slow
  @WorkerThread
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    String defaultActivity = computeDefaultActivityWithDevicePreference(getActivitiesFromMergedManifest(myFacet), device);
    if (defaultActivity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }
    return defaultActivity;
  }

  @Override
  public void validate() throws ActivityLocatorException {
    if (DumbService.isDumb(myFacet.getModule().getProject())) {
      return;
    }
    List<ActivityWrapper> activities = getActivitiesFromManifestIndex(myFacet);
    if (computeDefaultActivity(activities) == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }
  }

  /**
   * Retrieves the list of activities from the merged manifest of the Android module
   * corresponding to the given facet.
   */
  @WorkerThread
  @VisibleForTesting
  public static List<ActivityWrapper> getActivitiesFromMergedManifest(@NotNull final AndroidFacet facet) {
    return DumbService.getInstance(facet.getModule().getProject()).runReadActionInSmartMode(() -> getActivitiesFromManifestIndex(facet));
  }

  @NotNull
  private static List<ActivityWrapper> getActivitiesFromManifestIndex(@NotNull final AndroidFacet facet) {
    boolean onEdt = ApplicationManager.getApplication().isDispatchThread();
    Stopwatch timer = Stopwatch.createStarted();
    List<ActivityWrapper> activityWrappers = queryActivitiesFromManifestIndex(facet).getJoined();
    logManifestLatency(onEdt, timer.elapsed(TimeUnit.MILLISECONDS));
    return activityWrappers;
  }

  private static void logManifestLatency(
    boolean blocksUiThread,
    long latencyMs
  ) {
    AndroidStudioEvent.Builder proto = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEFAULT_ACTIVITY_LOCATOR_STATS)
      .setDefaultActivityLocatorStats(
        DefaultActivityLocatorStats.newBuilder()
          .setBlocksUiThread(blocksUiThread)
          .setIndexBased(true)
          .setUsedPotentiallyStaleManifest(false)
          .setLatencyMs(latencyMs)
      );
    UsageTracker.log(proto);
  }

  @Nullable
  public static String getDefaultLauncherActivityName(@NotNull Project project, @NotNull final Manifest manifest) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      // this method needs both read access and indexing support
      return DumbService.getInstance(project).runReadActionInSmartMode(() -> getDefaultLauncherActivityName(project, manifest));
    }

    Application application = manifest.getApplication();
    if (application == null) {
      return null;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && DumbService.isDumb(project)) {
      LOG.warn("Cannot locate default activity when indices are not available");
      return null;
    }

    return computeDefaultActivity(merge(application.getActivities(), application.getActivityAliases()));
  }

  /**
   * Returns true if there is a default launcher Activity in the manifest.
   */
  @Nullable
  public static boolean hasDefaultLauncherActivity(@NotNull final Manifest manifest) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Application application = manifest.getApplication();
    if (application == null) {
      return false;
    }

    List<ActivityWrapper> activities = merge(application.getActivities(), application.getActivityAliases());
    for (ActivityWrapper activity : activities) {
      if (ActivityLocatorUtils.containsLauncherIntent(activity) && activity.isEnabled()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the fqcn (fully qualified class name) of the default activity given a list of candidate activities,
   * or <@code null> if none can be found.
   */
  @Nullable
  public static String computeDefaultActivity(@NotNull List<? extends ActivityWrapper> activities) {
    List<ActivityWrapper> launchableActivities = getLaunchableActivities(activities);
    if (launchableActivities.isEmpty()) {
      return null;
    }
    else if (launchableActivities.size() == 1) {
      return launchableActivities.get(0).getQualifiedName();
    }

    // Prefer the launcher which has the CATEGORY_DEFAULT intent filter.
    // There is no such rule, but since Context.startActivity() prefers such activities, we do the same.
    // https://code.google.com/p/android/issues/detail?id=67068
    ActivityWrapper defaultLauncher = findDefaultLauncher(launchableActivities);
    if (defaultLauncher != null) {
      return defaultLauncher.getQualifiedName();
    }

    // Just return the first one we find
    return launchableActivities.get(0).getQualifiedName();
  }

  /**
   * Returns the fqcn (fully qualified class name) of the default activity given a list of candidate activities,
   * or <@code null> if none can be found. Some device types (e.g. Android TV) have specific requirements for
   * the default activity so this method gives preference to device specific defaults first.
   */
  @Nullable
  @Slow
  @WorkerThread
  public static String computeDefaultActivityWithDevicePreference(@NotNull List<ActivityWrapper> activities, @NotNull IDevice device) {
    List<ActivityWrapper> launchableActivities = getLaunchableActivities(activities);
    if (launchableActivities.isEmpty()) {
      return null;
    }
    else if (launchableActivities.size() == 1) {
      return launchableActivities.get(0).getQualifiedName();
    }

    // First check if we have an activity specific to the device
    ActivityWrapper activity = findLauncherActivityForDevice(launchableActivities, device);
    if (activity != null) {
      return activity.getQualifiedName();
    }

    // Prefer the launcher which has the CATEGORY_DEFAULT intent filter.
    // There is no such rule, but since Context.startActivity() prefers such activities, we do the same.
    // https://code.google.com/p/android/issues/detail?id=67068
    ActivityWrapper defaultLauncher = findDefaultLauncher(launchableActivities);
    if (defaultLauncher != null) {
      return defaultLauncher.getQualifiedName();
    }

    // Just return the first one we find
    return launchableActivities.get(0).getQualifiedName();
  }

  /**
   * Returns a launchable activity specific to the given device.
   */
  @Nullable
  @Slow
  @WorkerThread
  private static ActivityWrapper findLauncherActivityForDevice(@NotNull List<ActivityWrapper> launchableActivities,
                                                               @NotNull IDevice device) {
    // Currently, this just checks if the device is a TV, and if so, looks for the leanback launcher
    // https://code.google.com/p/android/issues/detail?id=176033
    if (device.supportsFeature(IDevice.HardwareFeature.TV)) {
      return findLeanbackLauncher(launchableActivities);
    }
    return null;
  }

  @Nullable
  private static ActivityWrapper findLeanbackLauncher(@NotNull List<ActivityWrapper> launcherActivities) {
    for (ActivityWrapper activity : launcherActivities) {
      if (activity.hasCategory(AndroidUtils.LEANBACK_LAUNCH_CATEGORY_NAME)) {
        return activity;
      }
    }

    return null;
  }

  @Nullable
  private static ActivityWrapper findDefaultLauncher(@NotNull List<ActivityWrapper> launcherActivities) {
    for (ActivityWrapper activity : launcherActivities) {
      if (activity.hasCategory(AndroidUtils.DEFAULT_CATEGORY_NAME)) {
        return activity;
      }
    }

    return null;
  }

  @NotNull
  private static List<ActivityWrapper> getLaunchableActivities(@NotNull List<? extends ActivityWrapper> allActivities) {
    List<ActivityWrapper> launchableActivities = allActivities
      .stream()
      .filter(activity -> ActivityLocatorUtils.containsLauncherIntent(activity) && activity.isEnabled())
      .collect(Collectors.toList());

    if (launchableActivities.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("No launchable activities found, total # of activities: " + allActivities.size());
      allActivities
        .forEach(wrapper -> LOG.debug(String.format("activity: %1$s, isEnabled: %2$s, containsLauncherIntent: %3$s",
                                                    wrapper.getQualifiedName(),
                                                    wrapper.isEnabled(),
                                                    ActivityLocatorUtils.containsLauncherIntent(wrapper))));
    }

    return launchableActivities;
  }

  private static List<ActivityWrapper> merge(List<Activity> activities, List<ActivityAlias> activityAliases) {
    final List<ActivityWrapper> activityWrappers = Lists.newArrayListWithExpectedSize(activities.size() + activityAliases.size());
    for (Activity a : activities) {
      activityWrappers.add(ActivityWrapper.get(a));
    }
    for (ActivityAlias a : activityAliases) {
      activityWrappers.add(ActivityWrapper.get(a));
    }
    return activityWrappers;
  }

  /**
   * {@link ActivityWrapper} is a simple wrapper class around an {@link Activity} or an {@link ActivityAlias}.
   */
  public static abstract class ActivityWrapper {
    public abstract boolean hasCategory(@NotNull String name);

    public abstract boolean hasAction(@NotNull String name);

    public abstract boolean isEnabled();

    /**
     * @return the value of android:exported attribute for the activity, null if not specified.
     *
     * Note that when the attribute is not explicitly set, it is considered exported if it has an intent filter.
     * If you want to check whether activity is exported either explicitly or implicitly, use {{@link #isLogicallyExported()} instead.
     */
    @Nullable
    public abstract Boolean getExported();

    /**
     * @return whether the activity is exported, either explicitly, or by having an intent filter.
     */
    public boolean isLogicallyExported() {
      return Boolean.TRUE.equals(getExported()) || hasIntentFilter();
    }

    /**
     * @return whether there is at least 1 intent filter specified for this activity.
     */
    public abstract boolean hasIntentFilter();

    /**
     * @return an overall theme for the activity.
     */
    @Nullable
    public String getTheme() {
      throw new UnsupportedOperationException("An activity theme name is not tracked");
    }

    @Nullable
    public abstract String getQualifiedName();

    public static ActivityWrapper get(@NotNull Activity activity) {
      return new RealActivityWrapper(activity);
    }

    public static ActivityWrapper get(@NotNull ActivityAlias activityAlias) {
      return new ActivityAliasWrapper(activityAlias);
    }

    public static ActivityWrapper get(@NotNull Element activityOrAlias) {
      return new ElementActivityWrapper(activityOrAlias);
    }

    public static List<ActivityWrapper> get(@NotNull List<Element> activities, @NotNull List<Element> aliases) {
      List<ActivityWrapper> list = Lists.newArrayListWithCapacity(activities.size() + aliases.size());
      for (Element element : activities) {
        list.add(new ElementActivityWrapper(element));
      }
      for (Element element : aliases) {
        list.add(new ElementActivityWrapper(element));
      }
      return list;
    }
  }

  private static class RealActivityWrapper extends ActivityWrapper {
    private final Activity myActivity;

    public RealActivityWrapper(Activity activity) {
      myActivity = activity;
    }

    @Override
    public boolean hasCategory(@NotNull String name) {
      for (IntentFilter filter : myActivity.getIntentFilters()) {
        if (AndroidDomUtil.containsCategory(filter, name)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean hasAction(@NotNull String name) {
      for (IntentFilter filter : myActivity.getIntentFilters()) {
        if (AndroidDomUtil.containsAction(filter, name)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean isEnabled() {
      AndroidAttributeValue<String> enabled = myActivity.getEnabled();
      if (enabled == null) {
        return true;
      }
      String stringValue = enabled.getStringValue();
      return stringValue == null // true if not specified
             || Boolean.valueOf(stringValue)
             // If the manifest specifies a resource reference, such as @bool/something,
             // we'd need to actually compute the "real" value of the constant, which can
             // depend on *any* resource qualifier, and those qualifiers depends on the
             // context (such as the specific device and device state). Instead we'll
             // just treat resource referenced enabled-values as true.
             || stringValue.startsWith(PREFIX_RESOURCE_REF);
    }

    @Nullable
    @Override
    public Boolean getExported() {
      AndroidAttributeValue<String> exported = myActivity.getExported();
      if (exported == null || exported.getValue() == null || exported.getValue().isEmpty()) {
        return null;
      }

      return Boolean.valueOf(exported.getValue());
    }

    @Override
    public boolean hasIntentFilter() {
      return !myActivity.getIntentFilters().isEmpty();
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return ActivityLocatorUtils.getQualifiedName(myActivity);
    }
  }

  private static class ActivityAliasWrapper extends ActivityWrapper {
    private final ActivityAlias myAlias;

    public ActivityAliasWrapper(ActivityAlias activityAlias) {
      myAlias = activityAlias;
    }

    @Override
    public boolean hasCategory(@NotNull String name) {
      for (IntentFilter filter : myAlias.getIntentFilters()) {
        if (AndroidDomUtil.containsCategory(filter, name)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean hasAction(@NotNull String name) {
      for (IntentFilter filter : myAlias.getIntentFilters()) {
        if (AndroidDomUtil.containsAction(filter, name)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean isEnabled() {
      AndroidAttributeValue<String> enabled = myAlias.getEnabled();
      if (enabled == null) {
        return true;
      }
      String stringValue = enabled.getStringValue();
      return stringValue == null // true if not specified
             || Boolean.valueOf(stringValue)
             // If the manifest specifies a resource reference, such as @bool/something,
             // we'd need to actually compute the "real" value of the constant, which can
             // depend on *any* resource qualifier, and those qualifiers depends on the
             // context (such as the specific device and device state). Instead we'll
             // just treat resource referenced enabled-values as true.
             || stringValue.startsWith(PREFIX_RESOURCE_REF);
    }

    @Nullable
    @Override
    public Boolean getExported() {
      AndroidAttributeValue<String> exported = myAlias.getExported();
      if (exported == null || exported.getValue() == null || exported.getValue().isEmpty()) {
        return null;
      }

      return Boolean.valueOf(exported.getValue());
    }

    @Override
    public boolean hasIntentFilter() {
      return !myAlias.getIntentFilters().isEmpty();
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return ActivityLocatorUtils.getQualifiedName(myAlias);
    }
  }

  private static class ElementActivityWrapper extends ActivityWrapper {
    private final Element myActivity;

    public ElementActivityWrapper(Element activity) {
      myActivity = activity;
    }

    @Override
    public boolean hasCategory(@NotNull String name) {
      Node node = myActivity.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
          Element filter = (Element)node;
          if (ActivityLocatorUtils.containsCategory(filter, name)) {
            return true;
          }
        }
        node = node.getNextSibling();
      }

      return false;
    }

    @Override
    public boolean hasAction(@NotNull String name) {
      Node node = myActivity.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
          Element filter = (Element)node;
          if (ActivityLocatorUtils.containsAction(filter, name)) {
            return true;
          }
        }
        node = node.getNextSibling();
      }

      return false;
    }

    @Override
    public boolean isEnabled() {
      String enabledAttr = myActivity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ENABLED);
      return StringUtil.isEmpty(enabledAttr) // true if not specified
             || Boolean.valueOf(enabledAttr)
             || enabledAttr.startsWith(PREFIX_RESOURCE_REF);
    }

    @Nullable
    @Override
    public Boolean getExported() {
      String exportedAttr = myActivity.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_EXPORTED);
      return StringUtil.isEmpty(exportedAttr) ? null : Boolean.valueOf(exportedAttr);
    }

    @Override
    public boolean hasIntentFilter() {
      Node node = myActivity.getFirstChild();
      while (node != null) {
        if (node.getNodeType() == Node.ELEMENT_NODE && NODE_INTENT.equals(node.getNodeName())) {
          return true;
        }
        node = node.getNextSibling();
      }

      return false;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return ActivityLocatorUtils.getQualifiedName(myActivity);
    }
  }
}
