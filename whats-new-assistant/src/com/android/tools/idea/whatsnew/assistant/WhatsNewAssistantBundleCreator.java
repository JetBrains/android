/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhatsNewAssistantBundleCreator implements AssistantBundleCreator {
  public static final String BUNDLE_ID = "DeveloperServices.WhatsNewAssistant";
  private static AssistantBundleCreator ourTestCreator = null;

  private WhatsNewAssistantURLProvider myURLProvider;
  private WhatsNewAssistantConnectionOpener myConnectionOpener;

  private int lastSeenVersion = -1;

  /**
   * Constructor initializes default production field, will be replaced in testing
   */
  public WhatsNewAssistantBundleCreator() {
    this(new WhatsNewAssistantURLProvider(), new WhatsNewAssistantConnectionOpener());
  }

  public WhatsNewAssistantBundleCreator(@NotNull WhatsNewAssistantURLProvider urlProvider) {
    this(urlProvider, new WhatsNewAssistantConnectionOpener());
  }

  public WhatsNewAssistantBundleCreator(@NotNull WhatsNewAssistantURLProvider urlProvider,
                                        @NotNull WhatsNewAssistantConnectionOpener connectionOpener) {
    myURLProvider = urlProvider;
    myConnectionOpener = connectionOpener;
  }

  @VisibleForTesting
  void setURLProvider(@NotNull WhatsNewAssistantURLProvider urlProvider) {
    myURLProvider = urlProvider;
  }

  @NotNull
  @Override
  public String getBundleId() {
    return BUNDLE_ID;
  }

  @Nullable
  @Override
  public WhatsNewAssistantBundle getBundle(@NotNull Project project) {
    // Must download any updated xml first
    updateConfig();

    // Parse and return the new bundle
    URL localConfig = myURLProvider.getLocalConfig(getVersion());
    return parseBundle(localConfig);
  }

  /**
   * @return null in order to override with our custom bundle
   */
  @Nullable
  @Override
  public URL getConfig() {
    return null;
  }

  /**
   * @return whether the downloaded config is a higher version than previous local file
   */
  public boolean isNewConfigVersion() {
    // Check the current version
    URL localConfig = myURLProvider.getLocalConfig(getVersion());
    File localConfigFile = new File(localConfig.getPath());
    if (localConfigFile.exists()) {
      WhatsNewAssistantBundle oldBundle = parseBundle(localConfig);
      if (oldBundle != null) {
        lastSeenVersion = oldBundle.getVersion();
      }
    }

    // Must download any updated xml first
    updateConfig();

    // Parse and return the new bundle
    WhatsNewAssistantBundle newBundle = parseBundle(localConfig);
    if (newBundle != null && newBundle.getVersion() > lastSeenVersion) {
      return true;
    }
    return false;
  }

  /**
   * Parse and return bundle from URL
   * @param url
   * @return the bundle, or {@code null} if there is an error while parsing
   */
  @Nullable
  private static WhatsNewAssistantBundle parseBundle(@NotNull URL url) {
    try (InputStream configStream = url.openStream()){
      return DefaultTutorialBundle.parse(configStream, WhatsNewAssistantBundle.class);
    }
    catch (Exception e) {
      getLog().warn(String.format("Error parsing bundle from \"%s\"", url), e);
      return null;
    }
  }

  /**
   * Update the config xml, replacing or creating a file for the current version
   */
  private void updateConfig() {
    URL webConfig = myURLProvider.getWebConfig(getVersion());
    URL localConfigPath = myURLProvider.getLocalConfig(getVersion());

    // Download XML from server and overwrite the local file
    if (StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.get()) {
      if (downloadConfig(webConfig, localConfigPath)) return;
    }

    // If downloading doesn't work and file doesn't already exist, unpack it from resources
    File localConfigFile = new File(localConfigPath.getPath());
    if (!localConfigFile.exists()) {
      try (InputStream configResource = myURLProvider.getResourceFileAsStream(this, getVersion());
           OutputStream destinationStream = new FileOutputStream(localConfigFile)) {
        if (configResource != null) {
          FileUtil.copy(configResource, destinationStream);
        } else {
          // No resource file found and download didn't work
          throw new IllegalStateException("Cannot get or generate WNA xml file");
        }
      }
      catch (IOException e) {
        getLog().warn(String.format("Error creating local file \"%s\"", localConfigFile), e);
      }
    }
  }

  /**
   * Download config xml from the web, using a temporary file and then moving it to the fixed location
   */
  private boolean downloadConfig(@NotNull URL sourceUrl, @NotNull URL destinationFileUrl) {
    ReadableByteChannel byteChannel;
    try {
      // If timeout is not > 0, the default values are used: 60s read and 10s connect
      URLConnection connection = myConnectionOpener.openConnection(sourceUrl, -1);
      byteChannel = Channels.newChannel(connection.getInputStream());
    }
    catch (Exception e) {
      getLog().warn(e);
      return false;
    }

    if (byteChannel != null) {
      // Download to a temporary file first
      File temporaryConfig = null;
      try {
        temporaryConfig = FileUtil.createTempFile("whatsnew-" + getVersion(), ".xml");
        try (
          FileOutputStream outputStream = new FileOutputStream(temporaryConfig);
          FileChannel fileChannel = outputStream.getChannel()
        ) {
          fileChannel.transferFrom(byteChannel, 0, Long.MAX_VALUE);
          FileUtil.copy(temporaryConfig, new File(destinationFileUrl.getPath()));
          return true;
        }
      }
      catch (Exception e) {
        getLog().warn(e);
      }
      finally {
        if (temporaryConfig != null)
          temporaryConfig.delete();
      }
    }
    return false;
  }

  private static String getVersion() {
    Revision revision = Revision.parseRevision(ApplicationInfo.getInstance().getStrictVersion());
    return String.format("%d.%d.%d", revision.getMajor(), revision.getMinor(), revision.getMicro());
  }

  public static boolean shouldShowReleaseNotes() {
    if (!(StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.get() && IdeInfo.getInstance().isAndroidStudio())) return false;

    Optional<AssistantBundleCreator> creator = getCreator();
    // We can't test if the config file exists until after the download attempt, which occurs later
    // TODO: possibly initialize the file + downloading before the panel needs to be opened
    return creator.isPresent();
  }

  @VisibleForTesting
  public static void setTestCreator(@Nullable AssistantBundleCreator testCreator) {
    ourTestCreator = testCreator;
  }

  private static Optional<AssistantBundleCreator> getCreator() {
    if (ourTestCreator != null) return Optional.of(ourTestCreator);

    return Arrays.stream(AssistantBundleCreator.EP_NAME.getExtensions()).filter(extension -> extension.getBundleId().equals(BUNDLE_ID))
      .findFirst();
  }

  private static Logger getLog() {
    return Logger.getInstance(WhatsNewAssistantBundleCreator.class);
  }
}

