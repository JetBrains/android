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
package com.android.tools.idea.run.activity;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.model.MergedManifest;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultActivityLocator extends ActivityLocator {
  @NotNull
  private final AndroidFacet myFacet;

  public DefaultActivityLocator(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    String activity = computeDefaultActivity(myFacet, device);
    if (activity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }
    return activity;
  }

  @Override
  public void validate() throws ActivityLocatorException {
    String activity = computeDefaultActivity(myFacet, null);
    if (activity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }
  }

  /** Note: this requires indices to be ready, and may take a while to return if indexing is in progress. */
  @Nullable
  @VisibleForTesting
  static String computeDefaultActivity(@NotNull final AndroidFacet facet, @Nullable final IDevice device) {
    assert !facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST;
    final MergedManifest manifestInfo = ManifestInfo.get(facet);

    return DumbService.getInstance(facet.getModule().getProject()).runReadActionInSmartMode(new Computable<String>() {
      @Override
      public String compute() {
        return computeDefaultActivity(manifestInfo.getActivities(), manifestInfo.getActivityAliases(), device);
      }
    });
  }

  @Nullable
  public static String getDefaultLauncherActivityName(@NotNull Project project, @NotNull final Manifest manifest) {
    return DumbService.getInstance(project).runReadActionInSmartMode(new Computable<String>() {
      @Override
      public String compute() {
        Application application = manifest.getApplication();
        if (application == null) {
          return null;
        }

        return computeDefaultActivity(application.getActivities(), application.getActivityAliass(), null);
      }
    });
  }

  @Nullable
  private static String computeDefaultActivity(@NotNull List<Activity> activities,
                                              @NotNull List<ActivityAlias> activityAliases,
                                              @Nullable IDevice device) {
    List<ActivityWrapper> launchableActivities = getLaunchableActivities(merge(activities, activityAliases));
    if (launchableActivities.isEmpty()) {
      return null;
    }
    else if (launchableActivities.size() == 1) {
      return launchableActivities.get(0).getQualifiedName();
    }

    // First check if we have an activity specific to the device
    if (device != null) {
      ActivityWrapper activity = findLauncherActivityForDevice(launchableActivities, device);
      if (activity != null) {
        return activity.getQualifiedName();
      }
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

  /** Returns a launchable activity specific to the given device. */
  @Nullable
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
      for (IntentFilter filter : activity.getIntentFilters()) {
        if (AndroidDomUtil.containsCategory(filter, AndroidUtils.LEANBACK_LAUNCH_CATEGORY_NAME)) {
          return activity;
        }
      }
    }

    return null;
  }

  @Nullable
  private static ActivityWrapper findDefaultLauncher(@NotNull List<ActivityWrapper> launcherActivities) {
    for (ActivityWrapper activity : launcherActivities) {
      for (IntentFilter filter : activity.getIntentFilters()) {
        if (AndroidDomUtil.containsCategory(filter, AndroidUtils.DEFAULT_CATEGORY_NAME)) {
          return activity;
        }
      }
    }

    return null;
  }

  @NotNull
  private static List<ActivityWrapper> getLaunchableActivities(@NotNull List<ActivityWrapper> allActivities) {
    return ContainerUtil.filter(allActivities, new Condition<ActivityWrapper>() {
      @Override
      public boolean value(ActivityWrapper activity) {
        return ActivityLocatorUtils.containsLauncherIntent(activity.getIntentFilters());
      }
    });
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

  /** {@link ActivityWrapper} is a simple wrapper class around an {@link Activity} or an {@link ActivityAlias}. */
  public static abstract class ActivityWrapper {
    @NotNull
    public abstract List<IntentFilter> getIntentFilters();

    @Nullable
    public abstract String getQualifiedName();

    public static ActivityWrapper get(@NotNull Activity activity) {
      return new RealActivityWrapper(activity);
    }

    public static ActivityWrapper get(@NotNull ActivityAlias activityAlias) {
      return new ActivityAliasWrapper(activityAlias);
    }
  }

  private static class RealActivityWrapper extends ActivityWrapper {
    private final Activity myActivity;

    public RealActivityWrapper(Activity activity) {
      myActivity = activity;
    }

    @NotNull
    @Override
    public List<IntentFilter> getIntentFilters() {
      return myActivity.getIntentFilters();
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

    @NotNull
    @Override
    public List<IntentFilter> getIntentFilters() {
      return myAlias.getIntentFilters();
    }

    @Nullable
    @Override
    public String getQualifiedName() {
      return ActivityLocatorUtils.getQualifiedName(myAlias);
    }
  }
}
