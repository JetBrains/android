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
package com.android.tools.idea.rendering;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.resources.ScreenSize;
import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.xml.AndroidManifest.*;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see com.android.xml.AndroidManifest
 */
public class ManifestInfo {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.ManifestInfo");

  private final Module myModule;
  private String myPackage;
  private String myManifestTheme;
  private Map<String, String> myActivityThemes;
  private XmlFile myManifestFile;
  private long myLastModified = -1;
  private long myLastChecked;
  private String myMinSdkName;
  private int myMinSdk;
  private int myTargetSdk;
  private String myApplicationIcon;
  private String myApplicationLabel;

  /**
   * Key for the per-project non-persistent property storing the {@link ManifestInfo} for
   * this project
   */
  @VisibleForTesting
  final static Key<ManifestInfo> MANIFEST_FINDER = new Key<ManifestInfo>("adt-manifest-info"); //$NON-NLS-1$

  /**
   * Constructs an {@link ManifestInfo} for the given module. Don't use this method;
   * use the {@link #get} factory method instead.
   *
   * @param module module to create an {@link ManifestInfo} for
   */
  private ManifestInfo(Module module) {
    myModule = module;
  }

  /**
   * Clears the cached manifest information. The next get call on one of the
   * properties will cause the information to be refreshed.
   */
  public void clear() {
    myLastChecked = 0;
  }

  /**
   * Returns the {@link ManifestInfo} for the given module
   *
   * @param module the module the finder is associated with
   * @return a {@ManifestInfo} for the given module, never null
   */
  @NotNull
  public static ManifestInfo get(Module module) {
    ManifestInfo finder = module.getUserData(MANIFEST_FINDER);
    if (finder == null) {
      finder = new ManifestInfo(module);
      module.putUserData(MANIFEST_FINDER, finder);
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
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return;
      }
      final VirtualFile manifestFile = AndroidRootUtil.getManifestFile(facet);
      if (manifestFile == null) {
        return;
      }
      myManifestFile = (XmlFile)PsiManager.getInstance(myModule.getProject()).findFile(manifestFile);
      if (myManifestFile == null) {
        return;
      }
    }

    // Check to see if our data is up to date
    long fileModified = myManifestFile.getModificationStamp();
    if (fileModified == myLastModified) {
      // Already have up to date data
      return;
    }
    myLastModified = fileModified;

    myActivityThemes = new HashMap<String, String>();
    myManifestTheme = null;
    myTargetSdk = 1; // Default when not specified
    myMinSdk = 1; // Default when not specified
    myMinSdkName = "1"; // Default when not specified
    myPackage = ""; //$NON-NLS-1$
    myApplicationIcon = null;
    myApplicationLabel = null;

    try {
      XmlTag root = myManifestFile.getRootTag();
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

        XmlTag[] activities = application.findSubTags(NODE_ACTIVITY);
        for (XmlTag activity : activities) {
          String theme = activity.getAttributeValue(ATTRIBUTE_THEME, ANDROID_URI);
          if (theme != null && theme.length() > 0) {
            String name = activity.getAttributeValue(ATTRIBUTE_NAME, ANDROID_URI);
            if (name != null) {
              int index = name.indexOf('.');
              if (index <= 0 && myPackage != null && !myPackage.isEmpty()) {
                name =  myPackage + (index == -1 ? "." : "") + name;
              }
              myActivityThemes.put(name, theme);
            }
          }
        }
      }

      // Look up target SDK
      XmlTag[] usesSdks = root.findSubTags(NODE_USES_SDK);
      if (usesSdks.length > 0) {
        XmlTag usesSdk = usesSdks[0];
        myMinSdk = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, 1);
        myTargetSdk = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, myMinSdk);
      }
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

  /**
   * Returns the default package registered in the Android manifest
   *
   * @return the default package registered in the manifest
   */
  @NotNull
  public String getPackage() {
    sync();
    return myPackage;
  }

  /**
   * Returns a map from activity full class names to the corresponding theme style to be
   * used
   *
   * @return a map from activity fqcn to theme style
   */
  @NotNull
  public Map<String, String> getActivityThemes() {
    sync();
    if (myActivityThemes == null) {
      sync();
    }
    return myActivityThemes;
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
