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

import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.DefaultTutorialBundle;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WhatsNewAssistantBundleCreator implements AssistantBundleCreator {
  public static final String BUNDLE_ID = "DeveloperServices.WhatsNewAssistant";

  private static AssistantBundleCreator ourTestCreator = null;

  @NotNull private WhatsNewAssistantURLProvider myURLProvider;
  @NotNull private WhatsNewAssistantConnectionOpener myConnectionOpener;
  @NotNull private Revision myStudioRevision;

  @NotNull private Revision myLastSeenVersion;

  /**
   * Constructor initializes default production field, will be replaced in testing
   */
  public WhatsNewAssistantBundleCreator() {
    this(new WhatsNewAssistantURLProvider(),
         Revision.safeParseRevision(ApplicationInfo.getInstance().getStrictVersion()),
         new WhatsNewAssistantConnectionOpener());
  }

  public WhatsNewAssistantBundleCreator(@NotNull WhatsNewAssistantURLProvider urlProvider,
                                        @NotNull Revision studioRevision) {
    this(urlProvider, studioRevision, new WhatsNewAssistantConnectionOpener());
  }

  public WhatsNewAssistantBundleCreator(@NotNull WhatsNewAssistantURLProvider urlProvider,
                                        @NotNull Revision studioRevision,
                                        @NotNull WhatsNewAssistantConnectionOpener connectionOpener) {
    myURLProvider = urlProvider;
    myConnectionOpener = connectionOpener;
    myStudioRevision = studioRevision;

    // Also check if it's Studio, just in case safeParseRevision didn't fail already
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      myStudioRevision = Revision.NOT_SPECIFIED;
    }

    myLastSeenVersion = Revision.NOT_SPECIFIED;
  }

  @VisibleForTesting
  void setURLProvider(@NotNull WhatsNewAssistantURLProvider urlProvider) {
    myURLProvider = urlProvider;
  }

  @VisibleForTesting
  void setStudioRevision(@NotNull Revision revision) {
    myStudioRevision = revision;
  }

  @NotNull
  @Override
  public String getBundleId() {
    return BUNDLE_ID;
  }

  /**
   * Get the bundle for WNA. Should not be called on UI thread.
   * @param project is not used
   * @return
   */
  @Nullable
  @Override
  public WhatsNewAssistantBundle getBundle(@NotNull Project project) {
    assert ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isDispatchThread();

    // Must download any updated xml first
    updateConfig();

    // Parse and return the new bundle
    return parseBundle();
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
   * Takes note of the current version of the config, then updates the config and
   * checks if the new config is a higher version.
   * Calling this function multiple times will return false on any subsequent calls,
   * unless the config actually is updated, since this function pulls updated config.
   * Should not be called on the UI thread.
   * @return whether the downloaded config is a higher version than previous local file
   */
  public boolean isNewConfigVersion() {
    assert ApplicationManager.getApplication().isUnitTestMode() || !ApplicationManager.getApplication().isDispatchThread();

    // Check the current version. If there is no local config, it is either:
    //   a) newly installed Android Studio - isNewStudioVersion already takes care of auto-show
    //   b) no network the last time Studio was opened, so last seen version would have been the
    //      same as default resource, typically 0.
    WhatsNewAssistantBundle oldBundle = parseBundle();
    if (oldBundle != null) {
      myLastSeenVersion = oldBundle.getVersion();
    }

    // Must download any updated xml first
    updateConfig();

    // Parse and return the new bundle
    WhatsNewAssistantBundle newBundle = parseBundle();
    if (newBundle != null) {
      if (myLastSeenVersion.equals(Revision.NOT_SPECIFIED)) {
        return true;
      }
      return newBundle.getVersion().compareTo(myLastSeenVersion) > 0;
    }
    return false;
  }

  /**
   * Parse and return bundle from URL, retrying once after deleting local
   * cache if the first try results in an error.
   * @return the bundle, or {@code null} if there is an error while parsing
   */
  @Nullable
  private WhatsNewAssistantBundle parseBundle() {
    WhatsNewAssistantBundle bundle = parseBundleWorker();
    if (bundle != null)
      return bundle;

    // Error can be caused by corrupt/empty .xml config. First delete the local file, then retry.
    try {
      Path path = myURLProvider.getLocalConfig(getStudioRevision());
      Files.delete(path);
    } catch (IOException e) {
      getLog().warn("Error deleting cached file", e);
      return null;
    }

    getLog().info("Retrying WNA parseBundle after deleting possibly corrupt file.");
    updateConfig();
    return parseBundleWorker();
  }

  /**
   * Parse and return bundle from URL
   * @return the bundle, or {@code null} if there is an error while parsing
   */
  @Nullable
  private WhatsNewAssistantBundle parseBundleWorker() {
    try (InputStream configStream = openConfigStream()) {
      if (configStream == null)
        return null;
      return DefaultTutorialBundle.parse(configStream, WhatsNewAssistantBundle.class, getBundleId());
    }
    catch (Exception e) {
      getLog().warn("Error parsing bundle", e);
      return null;
    }
  }

  @Nullable
  private InputStream openConfigStream() throws FileNotFoundException {
    Path path = myURLProvider.getLocalConfig(getStudioRevision());
    if (Files.exists(path)) {
      return new FileInputStream(path.toFile());
    }
    else {
      // If there is no existing config file, that means it has not been downloaded recently
      // and there is no cache, so we just display the default resource.
      return myURLProvider.getResourceFileAsStream(this, getStudioRevision());
    }
  }

  /**
   * Update the config xml, replacing or creating a file for the current version.
   * If Studio version is 0.0.0 (dev build) then skip
   */
  private void updateConfig() {
    // Download XML from server and overwrite the local file
    if (StudioFlags.WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT.get() && !myStudioRevision.equals(Revision.parseRevision("0.0.0rc0"))) {
      URL webConfig = myURLProvider.getWebConfig(getStudioRevision());
      Path localConfigPath = myURLProvider.getLocalConfig(getStudioRevision());
      downloadConfig(webConfig, localConfigPath);
    }
  }

  /**
   * Download config xml from the web, using a temporary file and then moving it to the fixed location
   */
  private boolean downloadConfig(@NotNull URL sourceUrl, @NotNull Path destinationFilePath) {
    ReadableByteChannel byteChannel;
    try {
      // If timeout is not > 0, the default values are used: 60s read and 10s connect
      URLConnection connection = myConnectionOpener.openConnection(sourceUrl, -1);
      byteChannel = Channels.newChannel(connection.getInputStream());
    }
    catch (FileNotFoundException e) {
      // This occurs when the remote file can't be found, which as far as I know is only when no
      // web config has been provided yet, so INFO severity is fine to avoid spamming devs/users
      getLog().info("Remote WNA config file not found", e);
      return false;
    }
    catch (Exception e) {
      getLog().warn(e);
      return false;
    }

    if (byteChannel != null) {
      // Download to a temporary file first
      File temporaryConfig = null;
      try {
        temporaryConfig = FileUtil.createTempFile("whatsnew-" + getStudioRevision(), ".xml");
        try (
          FileOutputStream outputStream = new FileOutputStream(temporaryConfig);
          FileChannel fileChannel = outputStream.getChannel()
        ) {
          fileChannel.transferFrom(byteChannel, 0, Long.MAX_VALUE);
          FileUtil.copy(temporaryConfig, destinationFilePath.toFile());
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

  @NotNull
  private String getStudioRevision() {
    return myStudioRevision.getMajor() + "." + myStudioRevision.getMinor() + "." + myStudioRevision.getMicro();
  }

  public boolean shouldShowWhatsNew() {
    if (!shouldShowReleaseNotes())
      return false;
    return hasResourceConfig();
  }

  @VisibleForTesting
  static boolean shouldShowReleaseNotes() {
    if (!(StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.get() && IdeInfo.getInstance().isAndroidStudio())) return false;

    Optional<AssistantBundleCreator> creator = getCreator();
    // We can't test if the config file exists until after the download attempt, which occurs later
    return creator.isPresent();
  }

  /**
   * Checks whether this version of Studio has a matching version resource.
   * @return true if a resource config exists for current Studio version
   */
  @VisibleForTesting
  boolean hasResourceConfig() {
    try (InputStream stream = myURLProvider.getResourceFileAsStream(this, getStudioRevision())) {
      if (stream == null) {
        return false;
      }
      WhatsNewAssistantBundle bundle = DefaultTutorialBundle.parse(stream, WhatsNewAssistantBundle.class, getBundleId());
      // If Studio version is 0.0.0 (dev build) then return true, as an exception so we can show local file for editing
      return myStudioRevision.equals(Revision.parseRevision("0.0.0rc0")) || isBundleRevisionSame(bundle.getVersion());
    }
    catch (Exception e) {
      getLog().warn(e);
      return false;
    }
  }

  /**
   * Returns true if the specified revision is the same major.minor as Studio.
   */
  private boolean isBundleRevisionSame(@NotNull Revision revision) {
    return revision.getMajor() == myStudioRevision.getMajor() && revision.getMinor() == myStudioRevision.getMinor();
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

