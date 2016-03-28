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
package com.android.tools.idea.sdk;

import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.repository.api.*;
import com.android.repository.impl.meta.Archive;
import com.android.repository.impl.meta.CommonFactory;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.LayoutlibVersion;
import com.android.sdklib.repository.legacy.descriptors.PkgType;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.LegacyRepoUtils;
import com.android.sdklib.repository.targets.OptionalLibraryImpl;
import com.android.tools.idea.sdk.legacy.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.legacy.remote.internal.archives.ArchFilter;
import com.android.tools.idea.sdk.legacy.remote.internal.packages.RemoteAddonPkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.packages.RemotePlatformPkgInfo;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkAddonSource;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkRepoSource;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSource;
import com.android.tools.idea.sdk.legacy.remote.internal.sources.SdkSysImgSource;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.TaskMonitorProgressIndicatorAdapter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * {@link FallbackRemoteRepoLoader} implementation that uses the old {@link SdkSource} mechanism for parsing packages.
 */
public class LegacyRemoteRepoLoader implements FallbackRemoteRepoLoader {
  /**
   * Caching downloader used by {@link SdkSource}s.
   */
  private DownloadCache myDownloadCache;

  /**
   * Settings used when downloading.
   */
  private SettingsController mySettingsController;

  /**
   * Create a new loader.
   *
   * @param settingsController For download-related settings.
   * @param handler            The {@link AndroidSdkHandler} that's using this loader.
   */
  public LegacyRemoteRepoLoader(@NotNull SettingsController settingsController) {
    mySettingsController = settingsController;
  }

  /**
   * Sets the {@link DownloadCache} for us to use, so that a custom one can be used during tests.
   *
   * @param cache The {@link DownloadCache} to use. If {@code null} a new {@link DownloadCache} will be created lazily.
   */
  @VisibleForTesting
  public void setDownloadCache(@Nullable DownloadCache cache) {
    myDownloadCache = cache;
  }

  /**
   * Gets or creates a {@link DownloadCache} using the settings from our {@link SettingsController}.
   */
  private DownloadCache getDownloadCache() {
    if (myDownloadCache == null) {
      myDownloadCache = new DownloadCache(DownloadCache.Strategy.FRESH_CACHE);
    }
    return myDownloadCache;
  }

  /**
   * Parses xml files using the {@link SdkSource} mechanism into {@link LegacyRemotePackage}s.
   */
  @NotNull
  @Override
  public Collection<RemotePackage> parseLegacyXml(@NotNull RepositorySource source, @NotNull ProgressIndicator progress) {
    SdkSource legacySource;
    RemotePkgInfo[] packages = null;
    for (SchemaModule module : source.getPermittedModules()) {
      legacySource = null;
      if (module.equals(AndroidSdkHandler.getRepositoryModule())) {
        legacySource = new SdkRepoSource(source.getUrl(), "Legacy Repo Source");
      }
      else if (module.equals(AndroidSdkHandler.getAddonModule())) {
        legacySource = new SdkAddonSource(source.getUrl(), "Legacy Addon Source");
      }
      else if (module.equals(AndroidSdkHandler.getSysImgModule())) {
        legacySource = new SdkSysImgSource(source.getUrl(), "Legacy System Image Source");
      }
      if (legacySource != null) {
        legacySource.load(getDownloadCache(), new TaskMonitorProgressIndicatorAdapter(progress), mySettingsController.getForceHttp());
        if (legacySource.getFetchError() != null) {
          progress.logInfo(legacySource.getFetchError());
        }
        packages = legacySource.getPackages();
        if (packages != null) {
          break;
        }
      }
    }
    List<RemotePackage> result = Lists.newArrayList();
    if (packages != null) {
      for (RemotePkgInfo pkgInfo : packages) {
        if (pkgInfo.getPkgDesc().getType() == PkgType.PKG_SAMPLE) {
          continue;
        }
        RemotePackage pkg = new LegacyRemotePackage(pkgInfo, source);
        result.add(pkg);
      }
    }
    return result;
  }

  /**
   * A {@link RemotePackage} implementation that wraps a {@link RemotePkgInfo}.
   */
  private class LegacyRemotePackage implements RemotePackage {

    private final RemotePkgInfo myWrapped;
    private RepositorySource mySource;
    private TypeDetails myDetails;

    LegacyRemotePackage(RemotePkgInfo remote, RepositorySource source) {
      myWrapped = remote;
      mySource = source;
    }

    @Override
    @NotNull
    public TypeDetails getTypeDetails() {
      if (myDetails == null) {
        int layoutlibApi = 0;
        if (myWrapped instanceof RemotePlatformPkgInfo) {
          LayoutlibVersion layoutlibVersion = ((RemotePlatformPkgInfo)myWrapped).getLayoutLibVersion();
          if (layoutlibVersion != null) {
            layoutlibApi = layoutlibVersion.getApi();
          }
        }
        List<IAndroidTarget.OptionalLibrary> libs = Lists.newArrayList();
        if (myWrapped instanceof RemoteAddonPkgInfo) {
          for (RemoteAddonPkgInfo.Lib wrappedLib : ((RemoteAddonPkgInfo)myWrapped).getLibs()) {
            libs.add(new OptionalLibraryImpl(wrappedLib.getName(), new File(""), wrappedLib.getDescription(), false));
          }
        }
        ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
        myDetails = LegacyRepoUtils.createTypeDetails(myWrapped.getPkgDesc(), layoutlibApi, libs, null, progress, FileOpUtils.create());
      }
      return myDetails;
    }

    @NotNull
    @Override
    public Revision getVersion() {
      return myWrapped.getRevision();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return LegacyRepoUtils.getDisplayName(myWrapped.getPkgDesc());
    }

    @Override
    public License getLicense() {
      return myWrapped.getLicense();
    }

    @NotNull
    @Override
    public Collection<Dependency> getAllDependencies() {
      // TODO: implement (this isn't implemented in the current version either)
      return ImmutableList.of();
    }

    @NotNull
    @Override
    public String getPath() {
      return LegacyRepoUtils.getLegacyPath(myWrapped.getPkgDesc(), null);
    }

    @NotNull
    @Override
    public CommonFactory createFactory() {
      return (CommonFactory)AndroidSdkHandler.getCommonModule().createLatestFactory();
    }

    @Override
    public boolean obsolete() {
      return myWrapped.isObsolete();
    }

    @Override
    public int compareTo(RepoPackage o) {
      int res = ComparisonChain.start()
        .compare(getPath(), o.getPath())
        .compare(getVersion(), o.getVersion())
        .result();
      if (res != 0) {
        return res;
      }
      if (!(o instanceof RemotePackage)) {
        return getClass().getName().compareTo(o.getClass().getName());
      }
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RepoPackage && compareTo((RepoPackage)obj) == 0;
    }

    @Override
    public int hashCode() {
      return getPath().hashCode() * 37 + getVersion().hashCode();
    }

    @NotNull
    @Override
    public RepositorySource getSource() {
      return mySource;
    }

    @Override
    public void setSource(@NotNull RepositorySource source) {
      mySource = source;
    }

    @Override
    public Archive getArchive() {
      for (com.android.tools.idea.sdk.legacy.remote.internal.archives.Archive archive : myWrapped.getArchives()) {
        if (archive.isCompatible()) {
          CommonFactory f = (CommonFactory)RepoManager.getCommonModule().createLatestFactory();
          Archive arch = f.createArchiveType();
          Archive.CompleteType complete = f.createCompleteType();
          complete.setChecksum(archive.getChecksum());
          complete.setSize(archive.getSize());
          complete.setUrl(archive.getUrl());
          arch.setComplete(complete);
          ArchFilter filter = archive.getArchFilter();
          if (filter.getHostBits() != null) {
            arch.setHostBits(filter.getHostBits().getSize());
          }
          if (filter.getHostOS() != null) {
            arch.setHostOs(filter.getHostOS().getXmlName());
          }
          if (filter.getJvmBits() != null) {
            arch.setJvmBits(filter.getJvmBits().getSize());
          }
          if (filter.getMinJvmVersion() != null) {
            arch.setMinJvmVersion(f.createRevisionType(filter.getMinJvmVersion()));
          }
          return arch;
        }
      }
      return null;
    }

    @NotNull
    @Override
    public Channel getChannel() {
      if (getVersion().isPreview()) {
        // We map the old concept of previews to the second-stablest channel.
        return Channel.create(1);
      }
      return Channel.create(0);
    }

    @Override
    public String toString() {
      return "Legacy package: " + myWrapped.toString();
    }
  }
}
