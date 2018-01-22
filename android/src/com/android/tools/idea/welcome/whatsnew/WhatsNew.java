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
package com.android.tools.idea.welcome.whatsnew;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.TipOfTheDayManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ResourceUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;

/**
 * Show a "what's new" dialog the first time the app starts up with a new major.minor version.
 *
 * The dialog consists of a single image, which resides in resources/whatsNew, and should be named like 1.2.3.4.png, where 1.2.3.4 is the
 * full version number.
 * The accessible text for the image will be taken from 1.2.3.4.txt in the same directory.
 *
 */
public class WhatsNew implements StartupActivity, DumbAware {
  private static final String RESOURCE_DIR = "whatsNew";

  @Override
  public void runActivity(@NotNull Project project) {
    WhatsNewService service = ServiceManager.getService(WhatsNewService.class);
    if (service == null || !IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }

    WhatsNewData data = service.getState();

    if (AndroidPlugin.isGuiTestingMode() && !data.myIsUnderTest) {
      return;
    }

    Revision applicationRevision = Revision.parseRevision(ApplicationInfo.getInstance().getStrictVersion());

    URL resourceUrl = ResourceUtil.getResource(WhatsNew.class, RESOURCE_DIR, "");
    if (resourceUrl == null) {
      // The dir doesn't exist (there aren't any images).
      return;
    }
    String messagePath = getMessageToShow(data, applicationRevision, resourceUrl);
    if (messagePath != null) {
      try {
        // We don't want to show two popups, so disable the normal tip of the day if we're showing what's new.
        disableTipOfTheDay();
        String text = getAccessibleText(messagePath, resourceUrl);
        InputStream stream = WhatsNew.class.getResourceAsStream(messagePath);
        if (stream == null) {
          // In a developer build the path will be the path directly to the image
          stream = new FileInputStream(messagePath);
        }
        try {
          ImageIcon image = new ImageIcon(ImageIO.read(stream));
          ApplicationManager.getApplication().invokeLater(new WhatsNewDialog(project, image, text)::show);
        }
        finally {
          stream.close();
        }
      }
      catch (IOException exception) {
        // shouldn't happen, but if it does just give up.
      }
    }
  }

  @Nullable
  private static String getAccessibleText(@NotNull String imagePath, @NotNull URL resourceUrl) {
    String base = FileUtil.getNameWithoutExtension(imagePath);
    Path image = null;
    try {
      image = toPath(resourceUrl).resolve(imagePath);
      Path textPath = image.getParent().resolve(base + SdkConstants.DOT_TXT);
      if (Files.exists(textPath)) {
        return new String(Files.readAllBytes(textPath), Charsets.UTF_8);
      }
    }
    catch (URISyntaxException | IOException e) {
      // give up
    }
    finally {
      closeFilesystem(image);
    }
    return null;
  }

  private static void closeFilesystem(@Nullable Path path) {
    if (path != null) {
      try {
        path.getFileSystem().close();
      }
      catch (IOException | UnsupportedOperationException e) {
        // ignore
      }
    }
  }

  private static void disableTipOfTheDay() {
    TipOfTheDayManager tips = Extensions.findExtension(StartupActivity.POST_STARTUP_ACTIVITY, TipOfTheDayManager.class);
    try {
      // This is obviously a horrible hack
      Field flag = TipOfTheDayManager.class.getDeclaredField("myVeryFirstProjectOpening");
      flag.setAccessible(true);
      flag.setBoolean(tips, false);
      flag.setAccessible(false);
    }
    catch (Exception e) {
      // nothing, just give up
    }
  }

  @VisibleForTesting
  @Nullable
  String getMessageToShow(@NotNull WhatsNewData data, @NotNull Revision applicationRevision, @NotNull URL resourceDir) {
    String seenRevisionStr = data.myRevision;
    Revision seenRevision = null;
    if (seenRevisionStr != null) {
      try {
        seenRevision = Revision.parseRevision(seenRevisionStr);
      }
      catch (NumberFormatException exception) {
        // Bad previous revision, treat as null.
      }
    }

    if (seenRevision == null || applicationRevision.compareTo(seenRevision, Revision.PreviewComparison.ASCENDING) > 0) {
      data.myRevision = applicationRevision.toString();
      Path latestMessage = getLatestMessage(resourceDir);
      if (latestMessage != null) {
        Revision latestMessageRevision = toRevision(latestMessage);
        // Don't want to show messages for lower major or minor revisions
        Revision truncatedLatestMessageRevision = new Revision(latestMessageRevision.getMajor(), latestMessageRevision.getMinor());
        Revision truncatedApplicationRevision = new Revision(applicationRevision.getMajor(), applicationRevision.getMinor());
        if (truncatedApplicationRevision.compareTo(truncatedLatestMessageRevision) == 0 &&
            (seenRevision == null || seenRevision.compareTo(latestMessageRevision, Revision.PreviewComparison.ASCENDING) < 0)) {
          return latestMessage.toString();
        }
      }
    }
    return null;
  }

  @NotNull
  private static Revision toRevision(@NotNull Path p) {
    String revisionStr = com.google.common.io.Files.getNameWithoutExtension(p.getFileName().toString());
    try {
      return Revision.parseRevision(revisionStr);
    }
    catch (RuntimeException exception) {
      // Bad version number
      return Revision.NOT_SPECIFIED;
    }
  }

  @Nullable
  private static Path getLatestMessage(@NotNull URL resourceDir) {
    Path dir = null;
    try {
      dir = toPath(resourceDir);
      return Files.list(dir)
        .filter(p -> p.toString().endsWith(SdkConstants.DOT_PNG))
        .max((p1, p2) -> toRevision(p1).compareTo(toRevision(p2), Revision.PreviewComparison.ASCENDING)).orElse(null);
    }
    catch (URISyntaxException | IOException exception) {
      // Just give up.
    }
    finally {
      closeFilesystem(dir);
    }
    return null;
  }

  private static Path toPath(@NotNull URL resourceDir) throws URISyntaxException, IOException {
    Path dir = null;
    try {
      dir = Paths.get(resourceDir.toURI());
    }
    catch (FileSystemNotFoundException exception) {
      // handled by "if" below
    }
    if (dir == null) {
      FileSystem filesystem = FileSystems.newFileSystem(resourceDir.toURI(), ImmutableMap.of());
      JarURLConnection connection = (JarURLConnection)resourceDir.openConnection();
      dir = filesystem.getPath(connection.getEntryName());
    }
    return dir;
  }

  @State(name = "whatsNew", storages = @Storage("androidStudioFirstRun.xml"))
  public static class WhatsNewService implements PersistentStateComponent<WhatsNewData> {
    private WhatsNewData myData;

    @NotNull
    @Override
    public WhatsNewData getState() {
      if (myData == null) {
        myData = new WhatsNewData();
      }
      return myData;
    }

    @Override
    public void loadState(@NotNull WhatsNewData state) {
      myData = state;
    }
  }

  @VisibleForTesting
  public static class WhatsNewData {
    @Tag("shownVersion") public String myRevision;

    // Not persisted. Used to indicate that we're in a UI test where this should be shown (by default it will not be shown in UI tests.
    @Transient
    public boolean myIsUnderTest = false;
  }

  private static class WhatsNewDialog extends DialogWrapper {
    private final Icon myImage;
    private final String myText;

    public WhatsNewDialog(@NotNull Project project, @NotNull Icon image, @Nullable String text) {
      super(project, false);
      setModal(true);
      myImage = image;
      myText = text;
      setTitle("What's New");
      init();
    }

    @NotNull
    @Override
    protected Action[] createActions() {
      return new Action[]{getOKAction()};
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JBLabel label = new JBLabel(myImage);
      if (myText != null) {
        label.getAccessibleContext().setAccessibleDescription(myText);
      }
      return label;
    }
  }
}
