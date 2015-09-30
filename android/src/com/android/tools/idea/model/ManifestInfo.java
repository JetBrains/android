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
package com.android.tools.idea.model;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.xml.AndroidManifest.*;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see com.android.xml.AndroidManifest
 */
public abstract class ManifestInfo {
  public static class ActivityAttributes {
    @Nullable private final String myIcon;
    @Nullable private final String myLabel;
    @NotNull private final String myName;
    @Nullable private final String myParentActivity;
    @Nullable private final String myTheme;
    @Nullable private final String myUiOptions;

    public ActivityAttributes(@NotNull XmlTag activity, @Nullable String packageName) {
      // Get activity name.
      String name = activity.getAttributeValue(ATTRIBUTE_NAME, ANDROID_URI);
      if (name == null || name.length() == 0) {
        throw new RuntimeException("Activity name cannot be empty.");
      }
      int index = name.indexOf('.');
      if (index <= 0 && packageName != null && !packageName.isEmpty()) {
        name = packageName + (index == -1 ? "." : "") + name;
      }
      myName = name;

      // Get activity icon.
      String value = activity.getAttributeValue(ATTRIBUTE_ICON, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myIcon = value;
      }
      else {
        myIcon = null;
      }

      // Get activity label.
      value = activity.getAttributeValue(ATTRIBUTE_LABEL, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myLabel = value;
      }
      else {
        myLabel = null;
      }

      // Get activity parent. Also search the meta-data for parent info.
      value = activity.getAttributeValue(ATTRIBUTE_PARENT_ACTIVITY_NAME, ANDROID_URI);
      if (value == null || value.length() == 0) {
        // TODO: Not sure if meta data can be used for API Level > 16
        XmlTag[] metaData = activity.findSubTags(NODE_METADATA);
        for (XmlTag data : metaData) {
          String metaDataName = data.getAttributeValue(ATTRIBUTE_NAME, ANDROID_URI);
          if (VALUE_PARENT_ACTIVITY.equals(metaDataName)) {
            value = data.getAttributeValue(ATTRIBUTE_VALUE, ANDROID_URI);
            if (value != null) {
              index = value.indexOf('.');
              if (index <= 0 && packageName != null && !packageName.isEmpty()) {
                value = packageName + (index == -1 ? "." : "") + value;
                break;
              }
            }
          }
        }
      }
      if (value != null && value.length() > 0) {
        myParentActivity = value;
      }
      else {
        myParentActivity = null;
      }

      // Get activity theme.
      value = activity.getAttributeValue(ATTRIBUTE_THEME, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myTheme = value;
      }
      else {
        myTheme = null;
      }

      // Get UI options.
      value = activity.getAttributeValue(ATTRIBUTE_UI_OPTIONS, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myUiOptions = value;
      }
      else {
        myUiOptions = null;
      }
    }

    @Nullable
    public String getIcon() {
      return myIcon;
    }

    @Nullable
    public String getLabel() {
      return myLabel;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    public String getParentActivity() {
      return myParentActivity;
    }

    @Nullable
    public String getTheme() {
      return myTheme;
    }

    @Nullable
    public String getUiOptions() {
      return myUiOptions;
    }
  }

  /** Key for the per-module non-persistent property storing the {@link ManifestInfo} for this module. */
  @VisibleForTesting
  final static Key<ManifestInfo> MANIFEST_FINDER = new Key<ManifestInfo>("adt-manifest-info"); //$NON-NLS-1$

  /** Key for the per-module non-persistent property storing the merged {@link ManifestInfo} for this module. */
  @VisibleForTesting
  final static Key<ManifestInfo> MERGED_MANIFEST_FINDER = new Key<ManifestInfo>("adt-merged-manifest-info");

  /**
   * Returns the {@link ManifestInfo} for the given module.
   *
   * @param module the module the finder is associated with
   * @return a {@ManifestInfo} for the given module, never null
   * @deprecated Use {@link #get(Module, boolean)} which is explicit about
   * whether a merged manifest should be used.
   */
  @NotNull
  public static ManifestInfo get(@NotNull  Module module) {
    return get(module, false);
  }

  /**
   * Returns the {@link ManifestInfo} for the given module.
   *
   * @param module            the module the finder is associated with
   * @param useMergedManifest if {@code true}, the merged manifest is used if available, otherwise the main source set's manifest
   *                          is used
   * @return a {@ManifestInfo} for the given module
   */
  public static ManifestInfo get(@NotNull Module module, boolean useMergedManifest) {
    Key<ManifestInfo> key = useMergedManifest ? MERGED_MANIFEST_FINDER : MANIFEST_FINDER;

    ManifestInfo finder = module.getUserData(key);
    if (finder == null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        throw new IllegalArgumentException("Manifest information can only be obtained on modules with the Android facet.");
      }
      finder = get(facet, useMergedManifest);
    }

    return finder;
  }

  /**
   * Returns the {@link ManifestInfo} for the given {@link AndroidFacet}.
   *
   * @param facet             the Android facet associated with a module.
   * @param useMergedManifest if {@code true}, the merged manifest is used if available, otherwise the main source set's manifest
   *                          is used
   * @return a {@ManifestInfo} for the given module
   */
  public static ManifestInfo get(@NotNull AndroidFacet facet, boolean useMergedManifest) {
    Key<ManifestInfo> key = useMergedManifest ? MERGED_MANIFEST_FINDER : MANIFEST_FINDER;
    Module module = facet.getModule();
    ManifestInfo finder = module.getUserData(key);
    if (finder == null) {
      finder = useMergedManifest ? new MergedManifestInfo(facet) : new PrimaryManifestInfo(module);
      module.putUserData(key, finder);
    }
    return finder;
  }

  /**
   * Clears the cached manifest information. The next get call on one of the
   * properties will cause the information to be refreshed.
   */
  public abstract void clear();

  /**
   * Returns the default package registered in the Android manifest
   *
   * @return the default package registered in the manifest
   */
  @Nullable
  public abstract String getPackage();

  /**
   * Returns a map from activity full class names to the corresponding {@link ActivityAttributes}
   *
   * @return a map from activity fqcn to ActivityAttributes
   */
  @NotNull
  public abstract Map<String, ActivityAttributes> getActivityAttributesMap();

  /**
   * Returns the attributes of an activity.
   */
  @Nullable
  public abstract ActivityAttributes getActivityAttributes(@NotNull String activity);

  /**
   * Returns the manifest theme registered on the application, if any
   *
   * @return a manifest theme, or null if none was registered
   */
  @Nullable
  public abstract String getManifestTheme();

  /**
   * Returns the default theme for this project, by looking at the manifest default
   * theme registration, target SDK, rendering target, etc.
   *
   * @param renderingTarget the rendering target use to render the theme, or null
   * @param screenSize      the screen size to obtain a default theme for, or null if unknown
   * @param device          the device to obtain a default theme for, or null if unknown
   * @return the theme to use for this project, never null
   */
  @NotNull
  public abstract String getDefaultTheme(@Nullable IAndroidTarget renderingTarget,
                                         @Nullable ScreenSize screenSize,
                                         @Nullable Device device);

  /**
   * Returns the application icon, or null
   *
   * @return the application icon, or null
   */
  @Nullable
  public abstract String getApplicationIcon();

  /**
   * Returns the application label, or null
   *
   * @return the application label, or null
   */
  @Nullable
  public abstract String getApplicationLabel();

  /**
   * Returns true if the application has RTL support.
   *
   * @return true if the application has RTL support.
   */
  public abstract boolean isRtlSupported();

  /**
   * Returns the value for the debuggable flag set in the manifest. Returns null if not set.
   */
  @Nullable
  public abstract Boolean getApplicationDebuggable();

  /**
   * Returns the target SDK version
   *
   * @return the target SDK version
   */
  @NotNull
  public abstract AndroidVersion getTargetSdkVersion();

  /**
   * Returns the minimum SDK version
   *
   * @return the minimum SDK version
   */
  @NotNull
  public abstract AndroidVersion getMinSdkVersion();

  /**
   * @return the list of activities defined in the manifest.
   */
  @NotNull
  public List<Activity> getActivities() {
    return getApplicationComponents(new Function<Application, List<Activity>>() {
      @Override
      public List<Activity> fun(Application application) {
        return application.getActivities();
      }
    });
  }

  /**
   * @return the list of activity aliases defined in the manifest.
   */
  @NotNull
  public List<ActivityAlias> getActivityAliases() {
    return getApplicationComponents(new Function<Application, List<ActivityAlias>>() {
      @Override
      public List<ActivityAlias> fun(Application application) {
        return application.getActivityAliass();
      }
    });
  }

  /**
   * @return the list of services defined in the manifest.
   */
  @NotNull
  public List<Service> getServices() {
    return getApplicationComponents(new Function<Application, List<Service>>() {
      @Override
      public List<Service> fun(Application application) {
        return application.getServices();
      }
    });
  }

  private <T> List<T> getApplicationComponents(final Function<Application, List<T>> accessor) {
    final List<Manifest> manifests = getManifests();
    if (manifests.isEmpty()) {
      Logger.getInstance(ManifestInfo.class).warn("List of manifests is empty, possibly needs a gradle sync.");
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {
      @Override
      public List<T> compute() {
        List<T> components = Lists.newArrayList();

        for (Manifest m : manifests) {
          Application application = m.getApplication();
          if (application != null) {
            components.addAll(accessor.fun(application));
          }
        }

        return components;
      }
    });
  }

  @NotNull
  public List<UsesFeature> getUsedFeatures() {
    final List<Manifest> manifests = getManifests();
    if (manifests.isEmpty()) {
      Logger.getInstance(ManifestInfo.class).warn("List of manifests is empty, possibly needs a gradle sync.");
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<List<UsesFeature>>() {
      @Override
      public List<UsesFeature> compute() {
        List<UsesFeature> usesFeatures = Lists.newArrayList();

        for (Manifest m : manifests) {
          usesFeatures.addAll(m.getUsesFeatures());
        }

        return usesFeatures;
      }
    });
  }

  @NotNull
  protected abstract List<Manifest> getManifests();
}
