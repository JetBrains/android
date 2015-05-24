/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.sdklib.SdkVersionInfo;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.xml.AndroidManifest.*;

class PrimaryManifestInfo extends ManifestInfo {
  private static final Logger LOG = Logger.getInstance(PrimaryManifestInfo.class);

  private final Module myModule;
  private String myPackage;
  private String myManifestTheme;
  private Map<String, ActivityAttributes> myActivityAttributesMap;
  private ManifestFile myManifestFile;
  private long myLastChecked;
  private AndroidVersion myMinSdk;
  private AndroidVersion myTargetSdk;
  private String myApplicationIcon;
  private String myApplicationLabel;
  private boolean myApplicationSupportsRtl;
  private Manifest myManifest;
  private Boolean myApplicationDebuggable;

  PrimaryManifestInfo(Module module) {
    myModule = module;
  }

  @Override
  public void clear() {
    myLastChecked = 0;
  }

  @Nullable
  @Override
  public String getPackage() {
    sync();
    return myPackage;
  }

  @NotNull
  @Override
  public Map<String, ActivityAttributes> getActivityAttributesMap() {
    sync();
    if (myActivityAttributesMap == null) {
      return Collections.emptyMap();
    }
    return myActivityAttributesMap;
  }

  @Nullable
  @Override
  public ActivityAttributes getActivityAttributes(@NotNull String activity) {
    int index = activity.indexOf('.');
    if (index <= 0 && myPackage != null && !myPackage.isEmpty()) {
      activity = myPackage + (index == -1 ? "." : "") + activity;
    }
    return getActivityAttributesMap().get(activity);
  }

  @Nullable
  @Override
  public String getManifestTheme() {
    sync();
    return myManifestTheme;
  }

  @NotNull
  @Override
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
    AndroidModuleInfo info = AndroidModuleInfo.get(myModule);
    if (info != null) {
      targetSdk = info.getTargetSdkVersion().getApiLevel();
    } else if (myTargetSdk != null) {
      targetSdk = myTargetSdk.getApiLevel();
    } else {
      targetSdk = SdkVersionInfo.HIGHEST_KNOWN_API;
    }
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

  @Nullable
  @Override
  public String getApplicationIcon() {
    sync();
    return myApplicationIcon;
  }

  @Nullable
  @Override
  public String getApplicationLabel() {
    sync();
    return myApplicationLabel;
  }

  @Override
  public boolean isRtlSupported() {
    sync();
    return myApplicationSupportsRtl;
  }

  @Nullable
  @Override
  public Boolean getApplicationDebuggable() {
    sync();
    return myApplicationDebuggable;
  }

  @NotNull
  @Override
  public AndroidVersion getTargetSdkVersion() {
    sync();
    return myTargetSdk != null ? myTargetSdk : getMinSdkVersion();
  }

  @NotNull
  @Override
  public AndroidVersion getMinSdkVersion() {
    sync();
    return myMinSdk != null ? myMinSdk : AndroidVersion.DEFAULT;
  }

  @NotNull
  @Override
  protected List<Manifest> getManifests() {
    sync();
    return myManifest != null ? Collections.singletonList(myManifest) : Collections.<Manifest>emptyList();
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
      myManifestFile = ManifestFile.create(myModule);
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
    myTargetSdk = AndroidVersion.DEFAULT;
    myMinSdk = AndroidVersion.DEFAULT;
    myPackage = ""; //$NON-NLS-1$
    myApplicationIcon = null;
    myApplicationLabel = null;
    myApplicationSupportsRtl = false;

    try {
      XmlFile xmlFile = myManifestFile.getXmlFile();
      if (xmlFile == null) {
        return;
      }

      XmlTag root = xmlFile.getRootTag();
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

        String debuggable = application.getAttributeValue(ATTRIBUTE_DEBUGGABLE, ANDROID_URI);
        myApplicationDebuggable = debuggable == null ? null : VALUE_TRUE.equals(debuggable);

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
        myMinSdk = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, AndroidVersion.DEFAULT);
        myTargetSdk = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, myMinSdk);
      }

      myManifest = AndroidUtils.loadDomElementWithReadPermission(myModule.getProject(), xmlFile, Manifest.class);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error("Could not read Manifest data", e);
    }
  }

  private static AndroidVersion getApiVersion(XmlTag usesSdk, String attribute, AndroidVersion defaultApiLevel) {
    String valueString = usesSdk.getAttributeValue(attribute, ANDROID_URI);
    if (valueString != null) {
      // TODO: Pass in platforms if we have them
      AndroidVersion version = SdkVersionInfo.getVersion(valueString, null);
      if (version != null) {
        return version;
      }
    }
    return defaultApiLevel;
  }

  private static class ManifestFile {
    private final Module myModule;
    private VirtualFile myVFile;
    private XmlFile myXmlFile;
    private long myLastModified = 0;

    private ManifestFile(@NotNull Module module, @NotNull VirtualFile file) {
      myModule = module;
      myVFile = file;
    }

    @Nullable
    public static synchronized ManifestFile create(@NotNull Module module) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(facet);
      if (manifestFile == null) {
        return null;
      }

      return new ManifestFile(module, manifestFile);
    }

    @Nullable
    private XmlFile parseManifest() {
      if (myVFile == null || !myVFile.exists()) {
        return null;
      }
      Project project = myModule.getProject();
      if (project.isDisposed()) {
        return null;
      }
      PsiFile psiFile = PsiManager.getInstance(project).findFile(myVFile);
      return (psiFile instanceof XmlFile) ? (XmlFile)psiFile : null;
    }

    public synchronized boolean refresh() {
      long lastModified = getLastModified();
      if (myXmlFile == null || myLastModified < lastModified) {
        myXmlFile = parseManifest();
        if (myXmlFile == null) {
          return false;
        }
        myLastModified = lastModified;
        return true;
      } else {
        return false;
      }
    }

    private long getLastModified() {
      if (myXmlFile != null) {
        return myXmlFile.getModificationStamp();
      } else {
        return 0;
      }
    }

    @Nullable
    public synchronized XmlFile getXmlFile() {
      return myXmlFile;
    }
  }
}
