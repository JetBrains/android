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
package com.android.tools.idea.model;

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlNode;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.xml.AndroidManifest.*;

/**
 * To get a MergedManifest use {@link MergedManifest#get(AndroidFacet)} or {@link MergedManifest#get(Module)}
 */
public class MergedManifest {

  private final Module myModule;
  private String myPackage;
  private String myApplicationId;
  private Integer myVersionCode;
  private String myManifestTheme;
  private Map<String, ActivityAttributes> myActivityAttributesMap;
  private ManifestInfo.ManifestFile myManifestFile;
  private long myLastChecked;
  private AndroidVersion myMinSdk;
  private AndroidVersion myTargetSdk;
  private String myApplicationIcon;
  private String myApplicationLabel;
  private boolean myApplicationSupportsRtl;
  private Boolean myApplicationDebuggable;
  private @Nullable("user error, no manifest file") Manifest myManifest;
  private @Nullable("is lazy initialised") Map<String, XmlNode.NodeKey> myNodeKeys;

  /**
   * Constructs a new MergedManifest
   * @param module the module containing the manifest
   */
  MergedManifest(@NotNull Module module) {
    myModule = module;
  }

  /**
   * Returns the {@link MergedManifest} for the given {@link Module}.
   *
   * @param module the android module
   * @return a {@link MergedManifest} for the given module, never null
   */
  @NotNull
  public static MergedManifest get(@NotNull Module module) {
    MergedManifest manifest = module.getComponent(MergedManifest.class);
    assert manifest != null;
    return manifest;
  }

  /**
   * Returns the {@link MergedManifest} for the given {@link AndroidFacet}.
   *
   * @param facet the Android facet associated with a module.
   * @return a {@link MergedManifest} for the given module
   */
  @NotNull
  public static MergedManifest get(@NotNull AndroidFacet facet) {
    return get(facet.getModule());
  }

  /**
   * Clears the cached manifest information. The next get call on one of the
   * properties will cause the information to be refreshed.
   */

  public void clear() {
    myLastChecked = 0;
  }

  /**
   * Returns the default package registered in the Android manifest
   *
   * @return the default package registered in the manifest
   */
  @Nullable
  public String getPackage() {
    sync();
    return myPackage;
  }

  /**
   * Gets the merged manifest application ID.
   */
  @Nullable
  public String getApplicationId() {
    sync();
    return myApplicationId;
  }

  @Nullable
  public Integer getVersionCode() {
    sync();
    return myVersionCode;
  }

  /**
   * Returns a map from activity full class names to the corresponding {@link ActivityAttributes}
   *
   * @return a map from activity fqcn to ActivityAttributes
   */
  @NotNull
  public Map<String, ActivityAttributes> getActivityAttributesMap() {
    sync();
    if (myActivityAttributesMap == null) {
      return Collections.emptyMap();
    }
    return myActivityAttributesMap;
  }

  /**
   * Returns the attributes of an activity.
   */
  @Nullable
  public ActivityAttributes getActivityAttributes(@NotNull String activity) {
    int index = activity.indexOf('.');
    if (index <= 0 && myApplicationId != null && !myApplicationId.isEmpty()) {
      activity = myApplicationId + (index == -1 ? "." : "") + activity;
    }
    return getActivityAttributesMap().get(activity);
  }

  /**
   * Returns the manifest theme registered on the application, if any
   *
   * @return a manifest theme, or null if none was registered
   */
  @Nullable
  public String getManifestTheme() {
    sync();
    return myManifestTheme;
  }

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
  public String getDefaultTheme(@Nullable IAndroidTarget renderingTarget, @Nullable ScreenSize screenSize, @Nullable Device device) {
    sync();

    if (myManifestTheme != null) {
      return myManifestTheme;
    }

    // For Android Wear and Android TV, the defaults differ
    if (device != null) {
      if (HardwareConfigHelper.isWear(device)) {
        return "@android:style/Theme.DeviceDefault.Light";
      } else if (HardwareConfigHelper.isTv(device)) {
        //noinspection SpellCheckingInspection
        return "@style/Theme.Leanback";
      }
    }

    // From manifest theme documentation:
    // "If that attribute is also not set, the default system theme is used."
    int targetSdk;
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;
    AndroidModuleInfo info = facet.getAndroidModuleInfo();
    targetSdk = info.getTargetSdkVersion().getApiLevel();

    int renderingTargetSdk = targetSdk;
    if (renderingTarget instanceof CompatibilityRenderTarget) {
      renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
      //targetSdk = SdkVersionInfo.HIGHEST_KNOWN_API
    } else if (renderingTarget != null) {
      renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
    }

    int apiLevel = Math.min(targetSdk, renderingTargetSdk);
    if (apiLevel >= 21) {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Material.Light"; //$NON-NLS-1$
    } else if (apiLevel >= 14 || apiLevel >= 11 && screenSize == ScreenSize.XLARGE) {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo"; //$NON-NLS-1$
    } else {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme"; //$NON-NLS-1$
    }
  }

  /**
   * Returns the application icon, or null
   *
   * @return the application icon, or null
   */
  @Nullable
  public String getApplicationIcon() {
    sync();
    return myApplicationIcon;
  }

  /**
   * Returns the application label, or null
   *
   * @return the application label, or null
   */
  @Nullable
  public String getApplicationLabel() {
    sync();
    return myApplicationLabel;
  }

  /**
   * Returns true if the application has RTL support.
   *
   * @return true if the application has RTL support.
   */
  public boolean isRtlSupported() {
    sync();
    return myApplicationSupportsRtl;
  }

  /**
   * Returns the value for the debuggable flag set in the manifest. Returns null if not set.
   */
  @Nullable
  public Boolean getApplicationDebuggable() {
    sync();
    return myApplicationDebuggable;
  }

  /**
   * Returns the target SDK version
   *
   * @return the target SDK version
   */
  @NotNull
  public AndroidVersion getTargetSdkVersion() {
    sync();
    return myTargetSdk != null ? myTargetSdk : getMinSdkVersion();
  }

  /**
   * Returns the minimum SDK version
   *
   * @return the minimum SDK version
   */
  @NotNull
  public AndroidVersion getMinSdkVersion() {
    sync();
    return myMinSdk != null ? myMinSdk : AndroidVersion.DEFAULT;
  }

  /**
   * Ensure that the package, theme and activity maps are initialized and up to date
   * with respect to the manifest file
   */
  private void sync() {
    // Since each of the accessors call sync(), allow a bunch of immediate
    // accessors to all bypass the file stat() below
    long now = System.currentTimeMillis();
    if (now - myLastChecked < 50 && myManifestFile != null) {
      return;
    }
    myLastChecked = now;

    // TODO remove this time based checking

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        syncWithReadPermission();
      }
    });
  }

  private void syncWithReadPermission() {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assert facet != null;

    if (myManifestFile == null) {
      myManifestFile = ManifestInfo.ManifestFile.create(facet);
    }

    // Check to see if our data is up to date
    boolean refresh = myManifestFile.refresh();
    if (!refresh) {
      // Already have up to date data
      return;
    }

    myActivityAttributesMap = new HashMap<String, ActivityAttributes>();
    myManifestTheme = null;
    myTargetSdk = AndroidVersion.DEFAULT;
    myMinSdk = AndroidVersion.DEFAULT;
    myPackage = ""; //$NON-NLS-1$
    myApplicationId = ""; //$NON-NLS-1$
    myVersionCode = null;
    myApplicationIcon = null;
    myApplicationLabel = null;
    myApplicationSupportsRtl = false;
    myNodeKeys = null;

    try {
      XmlFile xmlFile = myManifestFile.getXmlFile();
      if (xmlFile == null) {
        return;
      }

      XmlTag root = xmlFile.getRootTag();
      if (root == null) {
        return;
      }

      myApplicationId = ManifestInfo.getAttributeValue(root, ATTRIBUTE_PACKAGE, null);

      // The package comes from the main manifest, NOT from the merged manifest.
      Manifest manifest = facet.getManifest();
      myPackage = manifest == null ? myApplicationId : manifest.getPackage().getValue();

      String versionCode = ManifestInfo.getAttributeValue(root, SdkConstants.ATTR_VERSION_CODE);
      try {
        myVersionCode = Integer.valueOf(versionCode);
      }
      catch (NumberFormatException ignored) {}

      XmlTag[] applications = root.findSubTags(NODE_APPLICATION);
      if (applications.length > 0) {
        assert applications.length == 1;
        XmlTag application = applications[0];
        myApplicationIcon = ManifestInfo.getAttributeValue(application, ATTRIBUTE_ICON);
        myApplicationLabel = ManifestInfo.getAttributeValue(application, ATTRIBUTE_LABEL);
        myManifestTheme = ManifestInfo.getAttributeValue(application, ATTRIBUTE_THEME);
        myApplicationSupportsRtl = VALUE_TRUE.equals(ManifestInfo.getAttributeValue(application, ATTRIBUTE_SUPPORTS_RTL));

        String debuggable = ManifestInfo.getAttributeValue(application, ATTRIBUTE_DEBUGGABLE);
        myApplicationDebuggable = debuggable == null ? null : VALUE_TRUE.equals(debuggable);

        XmlTag[] activities = application.findSubTags(NODE_ACTIVITY);
        for (XmlTag activity : activities) {
          ActivityAttributes attributes = new ActivityAttributes(activity, myApplicationId);
          myActivityAttributesMap.put(attributes.getName(), attributes);
        }
      }

      // Look up target SDK
      XmlTag[] usesSdks = root.findSubTags(NODE_USES_SDK);
      if (usesSdks.length > 0) {
        XmlTag usesSdk = usesSdks[0];
        myMinSdk = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, AndroidVersion.DEFAULT);
        myTargetSdk = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, myMinSdk);
      }

      myManifest = AndroidUtils.loadDomElementWithReadPermission(facet.getModule().getProject(), xmlFile, Manifest.class);
    }
    catch (ProcessCanceledException e) {
      myManifestFile = null; // clear the file, to make sure we reload everything on next call to this method
      throw e;
    }
    catch (Exception e) {
      Logger.getInstance(MergedManifest.class).warn("Could not read Manifest data", e);
    }
  }

  private static AndroidVersion getApiVersion(XmlTag usesSdk, String attribute, AndroidVersion defaultApiLevel) {
    String valueString = ManifestInfo.getAttributeValue(usesSdk, attribute);
    if (valueString != null) {
      // TODO: Pass in platforms if we have them
      AndroidVersion version = SdkVersionInfo.getVersion(valueString, null);
      if (version != null) {
        return version;
      }
    }
    return defaultApiLevel;
  }

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

  @NotNull
  private <T> List<T> getApplicationComponents(final Function<Application, List<T>> accessor) {
    sync();
    return ApplicationManager.getApplication().runReadAction(new Computable<List<T>>() {
      @Override
      public List<T> compute() {
        List<T> components = Lists.newArrayList();
        if (myManifest != null) { // User error, no manifest file
          Application application = myManifest.getApplication();
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
    sync();
    return ApplicationManager.getApplication().runReadAction(new Computable<List<UsesFeature>>() {
      @Override
      public List<UsesFeature> compute() {
        List<UsesFeature> usesFeatures = Lists.newArrayList();
        if (myManifest != null) { // User error, no manifest file
          usesFeatures.addAll(myManifest.getUsesFeatures());
        }
        return usesFeatures;
      }
    });
  }

  @NotNull
  public ImmutableList<MergingReport.Record> getLoggingRecords() {
    sync();
    return myManifestFile == null ? ImmutableList.<MergingReport.Record>of() : myManifestFile.getLoggingRecords();
  }

  @Nullable
  public Actions getActions() {
    sync();
    return myManifestFile == null ? null : myManifestFile.getActions();
  }

  @Nullable
  public XmlTag getXmlTag() {
    sync();
    return myManifest == null ? null : myManifest.getXmlTag();
  }

  @Nullable("can not find a node key with that name")
  public XmlNode.NodeKey getNodeKey(String name) {
    sync();
    if (myNodeKeys == null) {
      myNodeKeys = new HashMap<>();
      Actions actions = getActions();
      if (actions != null) {
        Set<XmlNode.NodeKey> keys = actions.getNodeKeys();
        for (XmlNode.NodeKey key : keys) {
          myNodeKeys.put(key.toString(), key);
        }
      }
    }
    return myNodeKeys.get(name);
  }

  public static class ActivityAttributes {
    @Nullable private final String myIcon;
    @Nullable private final String myLabel;
    @NotNull private final String myName;
    @Nullable private final String myParentActivity;
    @Nullable private final String myTheme;
    @Nullable private final String myUiOptions;

    public ActivityAttributes(@NotNull XmlTag activity, @Nullable String packageName) {
      // Get activity name.
      String name = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_NAME);
      if (name == null || name.length() == 0) {
        throw new RuntimeException("Activity name cannot be empty.");
      }
      int index = name.indexOf('.');
      if (index <= 0 && packageName != null && !packageName.isEmpty()) {
        name = packageName + (index == -1 ? "." : "") + name;
      }
      myName = name;

      // Get activity icon.
      String value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_ICON);
      if (value != null && value.length() > 0) {
        myIcon = value;
      }
      else {
        myIcon = null;
      }

      // Get activity label.
      value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_LABEL);
      if (value != null && value.length() > 0) {
        myLabel = value;
      }
      else {
        myLabel = null;
      }

      // Get activity parent. Also search the meta-data for parent info.
      value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_PARENT_ACTIVITY_NAME);
      if (value == null || value.length() == 0) {
        // TODO: Not sure if meta data can be used for API Level > 16
        XmlTag[] metaData = activity.findSubTags(NODE_METADATA);
        for (XmlTag data : metaData) {
          String metaDataName = ManifestInfo.getAttributeValue(data, ATTRIBUTE_NAME);
          if (VALUE_PARENT_ACTIVITY.equals(metaDataName)) {
            value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_VALUE);
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
      value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_THEME);
      if (value != null && value.length() > 0) {
        myTheme = value;
      }
      else {
        myTheme = null;
      }

      // Get UI options.
      value = ManifestInfo.getAttributeValue(activity, ATTRIBUTE_UI_OPTIONS);
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
}
