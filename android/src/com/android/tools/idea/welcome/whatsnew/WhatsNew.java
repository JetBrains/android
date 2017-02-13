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
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ResourceUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
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
  public static final String RESOURCE_DIR = "whatsNew";

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
      // There the dir doesn't exist (there aren't any images).
      return;
    }
    Path message = getMessageToShow(data, applicationRevision, resourceUrl);
    if (message != null) {
      disableTipOfTheDay();
      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          new WhatsNewDialog(project, message).show();
        }
        catch (MalformedURLException exception) {
          // shouldn't happen, but if it does just give up.
        }
      });
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
  Path getMessageToShow(@NotNull WhatsNewData data, @NotNull Revision applicationRevision, @NotNull URL resourceDir) {
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
          return latestMessage;
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
    FileSystem filesystem = null;
    try {
      Path dir = null;
      try {
        dir = Paths.get(resourceDir.toURI());
      }
      catch (FileSystemNotFoundException exception) {
        // handled by "if" below
      }
      try {
        if (dir == null) {
          filesystem = FileSystems.newFileSystem(resourceDir.toURI(), ImmutableMap.of());
          JarURLConnection connection = (JarURLConnection)resourceDir.openConnection();
          dir = filesystem.getPath(connection.getEntryName());
        }
        return Files.list(dir)
          .filter(p -> p.toString().endsWith(SdkConstants.DOT_PNG))
          .max((p1, p2) -> toRevision(p1).compareTo(toRevision(p2), Revision.PreviewComparison.ASCENDING)).orElse(null);
      }
      finally {
        if (filesystem != null) {
          filesystem.close();
        }
      }
    }
    catch (URISyntaxException | IOException exception) {
      // Just give up.
    }
    return null;
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
    public void loadState(WhatsNewData state) {
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
    private Icon myImage;
    private String myText;

    public WhatsNewDialog(@NotNull Project project, @NotNull Path message) throws MalformedURLException {
      super(project, false);
      setModal(true);
      myImage = new ImageIcon(message.toUri().toURL());
      myText = getAccessibleText(message);
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

    private static String getAccessibleText(Path image) {
      String base = com.google.common.io.Files.getNameWithoutExtension(image.getFileName().toString());
      Path textPath = image.getParent().resolve(base + SdkConstants.DOT_TXT);
      if (!textPath.getFileSystem().isOpen()) {
        try (FileSystem fileSystem = FileSystems.newFileSystem(textPath.toUri(), ImmutableMap.of())) {
          textPath = fileSystem.getPath(textPath.toString());
          return readText(textPath);
        }
        catch (IOException e) {
          return null;
        }
      }
      else {
        return readText(textPath);
      }
    }

    @Nullable
    private static String readText(Path textPath) {
      if (Files.exists(textPath)) {
        try {
          return new String(Files.readAllBytes(textPath), Charsets.UTF_8);
        }
        catch (IOException e) {
          // give up
        }
      }
      return null;
    }
  }
}
