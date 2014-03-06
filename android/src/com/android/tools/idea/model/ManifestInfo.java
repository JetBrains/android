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
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.xml.AndroidManifest.*;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see com.android.xml.AndroidManifest
 */
public class ManifestInfo {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.model.ManifestInfo");

  public static class ActivityAttributes {
    @Nullable
    private final String myIcon;
    @Nullable
    private final String myLabel;
    @NotNull
    private final String myName;
    @Nullable
    private final String myParentActivity;
    @Nullable
    private final String myTheme;
    @Nullable
    private final String myUiOptions;

    public ActivityAttributes(@NotNull XmlTag activity, @Nullable String packageName) {
      // Get activity name.
      String name = activity.getAttributeValue(ATTRIBUTE_NAME, ANDROID_URI);
      if (name == null || name.length() == 0) {
        throw new RuntimeException("Activity name cannot be empty.");
      }
      int index = name.indexOf('.');
      if (index <= 0 && packageName != null && !packageName.isEmpty()) {
        name =  packageName + (index == -1 ? "." : "") + name;
      }
      myName = name;

      // Get activity icon.
      String value = activity.getAttributeValue(ATTRIBUTE_ICON, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myIcon = value;
      } else {
        myIcon = null;
      }

      // Get activity label.
      value = activity.getAttributeValue(ATTRIBUTE_LABEL, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myLabel = value;
      } else {
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
                value =  packageName + (index == -1 ? "." : "") + value;
                break;
              }
            }
          }
        }
      }
      if (value != null && value.length() > 0) {
        myParentActivity = value;
      } else {
        myParentActivity = null;
      }

      // Get activity theme.
      value = activity.getAttributeValue(ATTRIBUTE_THEME, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myTheme = value;
      } else {
        myTheme = null;
      }

      // Get UI options.
      value = activity.getAttributeValue(ATTRIBUTE_UI_OPTIONS, ANDROID_URI);
      if (value != null && value.length() > 0) {
        myUiOptions = value;
      } else {
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

  private final Module myModule;
  private final boolean myPreferMergedManifest;
  private String myPackage;
  private String myManifestTheme;
  private Map<String, ActivityAttributes> myActivityAttributesMap;
  private ManifestFile myManifestFile;
  private long myLastChecked;
  private String myMinSdkName;
  private int myMinSdk;
  private int myTargetSdk;
  private String myApplicationIcon;
  private String myApplicationLabel;
  private boolean myApplicationSupportsRtl;
  private Manifest myManifest;

  /** Key for the per-module non-persistent property storing the {@link ManifestInfo} for this module. */
  @VisibleForTesting
  final static Key<ManifestInfo> MANIFEST_FINDER = new Key<ManifestInfo>("adt-manifest-info"); //$NON-NLS-1$

  /** Key for the per-module non-persistent property storing the merged {@link ManifestInfo} for this module. */
  @VisibleForTesting
  final static Key<ManifestInfo> MERGED_MANIFEST_FINDER = new Key<ManifestInfo>("adt-merged-manifest-info");

  /**
   * Constructs an {@link ManifestInfo} for the given module. Don't use this method;
   * use the {@link #get} factory method instead.
   *
   * @param module module to create an {@link ManifestInfo} for
   * @param preferMergedManifest use the merged manifest if available, fallback to the main manifest otherwise
   */
  private ManifestInfo(Module module, boolean preferMergedManifest) {
    myModule = module;
    myPreferMergedManifest = preferMergedManifest;
  }

  /**
   * Clears the cached manifest information. The next get call on one of the
   * properties will cause the information to be refreshed.
   */
  public void clear() {
    myLastChecked = 0;
  }

  /**
   * Returns the {@link ManifestInfo} for the given module.
   *
   * @param module the module the finder is associated with
   * @return a {@ManifestInfo} for the given module, never null
   * @deprecated Use {@link #get(com.intellij.openapi.module.Module, boolean)} which is explicit about
   * whether a merged manifest should be used.
   */
  @NotNull
  public static ManifestInfo get(Module module) {
    return get(module, false);
  }

  /**
   * Returns the {@link ManifestInfo} for the given module.
   *
   * @param module the module the finder is associated with
   * @param useMergedManifest if true, the merged manifest is used if available, otherwise the main sourceset's manifest
   *                          is used
   * @return a {@ManifestInfo} for the given module
   */
  public static ManifestInfo get(Module module, boolean useMergedManifest) {
    Key<ManifestInfo> key = useMergedManifest ? MERGED_MANIFEST_FINDER : MANIFEST_FINDER;

    ManifestInfo finder = module.getUserData(key);
    if (finder == null) {
      finder = new ManifestInfo(module, useMergedManifest);
      module.putUserData(key, finder);
    }

    return finder;
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

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        syncWithReadPermission();
      }
    });
  }

  private void syncWithReadPermission() {
    if (myManifestFile == null) {
      myManifestFile = ManifestFile.create(myModule, myPreferMergedManifest);
      if (myManifestFile == null) {
        return;
      }
    }

    // Check to see if our data is up to date
    boolean refresh = myManifestFile.refresh();
    if (!refresh) {
      // Already have up to date data
      return;
    }

    myActivityAttributesMap = new HashMap<String, ActivityAttributes>();
    myManifestTheme = null;
    myTargetSdk = 1; // Default when not specified
    myMinSdk = 1; // Default when not specified
    myMinSdkName = "1"; // Default when not specified
    myPackage = ""; //$NON-NLS-1$
    myApplicationIcon = null;
    myApplicationLabel = null;
    myApplicationSupportsRtl = false;

    try {
      XmlTag root = myManifestFile.getXmlFile().getRootTag();
      if (root == null) {
        return;
      }

      myPackage = root.getAttributeValue(ATTRIBUTE_PACKAGE);

      XmlTag[] applications = root.findSubTags(NODE_APPLICATION);
      if (applications.length > 0) {
        assert applications.length == 1;
        XmlTag application = applications[0];
        myApplicationIcon = application.getAttributeValue(ATTRIBUTE_ICON, ANDROID_URI);
        myApplicationLabel = application.getAttributeValue(ATTRIBUTE_LABEL, ANDROID_URI);
        myManifestTheme = application.getAttributeValue(ATTRIBUTE_THEME, ANDROID_URI);
        myApplicationSupportsRtl = VALUE_TRUE.equals(application.getAttributeValue(ATTRIBUTE_SUPPORTS_RTL, ANDROID_URI));

        XmlTag[] activities = application.findSubTags(NODE_ACTIVITY);
        for (XmlTag activity : activities) {
          ActivityAttributes attributes = new ActivityAttributes(activity, myPackage);
          myActivityAttributesMap.put(attributes.getName(), attributes);
        }
      }

      // Look up target SDK
      XmlTag[] usesSdks = root.findSubTags(NODE_USES_SDK);
      if (usesSdks.length > 0) {
        XmlTag usesSdk = usesSdks[0];
        myMinSdk = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, 1);
        myTargetSdk = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, myMinSdk);
      }

      myManifest = AndroidUtils.loadDomElementWithReadPermission(myModule.getProject(), myManifestFile.getXmlFile(), Manifest.class);
    }
    catch (Exception e) {
      LOG.error("Could not read Manifest data", e);
    }
  }

  private int getApiVersion(XmlTag usesSdk, String attribute, int defaultApiLevel) {
    String valueString = usesSdk.getAttributeValue(attribute, ANDROID_URI);
    if (attribute.equals(ATTRIBUTE_MIN_SDK_VERSION)) {
      myMinSdkName = valueString;
    }

    if (valueString != null) {
      int apiLevel = -1;
      try {
        apiLevel = Integer.valueOf(valueString);
      }
      catch (NumberFormatException e) {
        // Handle codename
        AndroidFacet facet = AndroidFacet.getInstance(myModule);
        if (facet != null) {
          IAndroidTarget target = facet.getTargetFromHashString("android-" + valueString);
          if (target != null) {
            // codename future API level is current api + 1
            apiLevel = target.getVersion().getApiLevel() + 1;
          }
        }
      }

      return apiLevel;
    }

    return defaultApiLevel;
  }

  @Nullable
  public Manifest getManifest() {
    sync();
    return myManifest;
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
    if (index <= 0 && myPackage != null && !myPackage.isEmpty()) {
      activity = myPackage + (index == -1 ? "." : "") + activity;
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
   * @return the theme to use for this project, never null
   */
  @NotNull
  public String getDefaultTheme(@Nullable IAndroidTarget renderingTarget, @Nullable ScreenSize screenSize) {
    sync();

    if (myManifestTheme != null) {
      return myManifestTheme;
    }

    // From manifest theme documentation:
    // "If that attribute is also not set, the default system theme is used."

    int renderingTargetSdk = myTargetSdk;
    if (renderingTarget != null) {
      renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
    }

    int apiLevel = Math.min(myTargetSdk, renderingTargetSdk);
    // For now this theme works only on XLARGE screens. When it works for all sizes,
    // add that new apiLevel to this check.
    if (apiLevel >= 11 && screenSize == ScreenSize.XLARGE || apiLevel >= 14) {
      return ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo"; //$NON-NLS-1$
    }
    else {
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
   * Returns the target SDK version
   *
   * @return the target SDK version
   */
  public int getTargetSdkVersion() {
    sync();
    return myTargetSdk;
  }

  /**
   * Returns the minimum SDK version
   *
   * @return the minimum SDK version
   */
  public int getMinSdkVersion() {
    sync();
    return myMinSdk;
  }

  /**
   * Returns the minimum SDK version name (which may not be a numeric string, e.g.
   * it could be a codename). It will never be null or empty; if no min sdk version
   * was specified in the manifest, the return value will be "1". Use
   * {@link #getMinSdkCodeName()} instead if you want to look up whether there is a code name.
   *
   * @return the minimum SDK version
   */
  @NotNull
  public String getMinSdkName() {
    sync();
    if (myMinSdkName == null || myMinSdkName.isEmpty()) {
      myMinSdkName = "1"; //$NON-NLS-1$
    }

    return myMinSdkName;
  }

  /**
   * Returns the code name used for the minimum SDK version, if any.
   *
   * @return the minSdkVersion codename or null
   */
  @Nullable
  public String getMinSdkCodeName() {
    String minSdkName = getMinSdkName();
    if (!Character.isDigit(minSdkName.charAt(0))) {
      return minSdkName;
    }

    return null;
  }
}
