/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.fd;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.InstantRun;
import com.android.builder.model.SourceProvider;
import com.android.ddmlib.*;
import com.android.repository.Revision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.*;
import com.android.utils.XmlUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.io.Files;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XExecutionStack;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * The {@linkplain FastDeployManager} is responsible for handling Instant Run related functionality
 * in the IDE: determining if an app is running with the fast deploy runtime, whether it's up to date, communicating with it, etc.
 */
public final class FastDeployManager implements ProjectComponent, BulkFileListener {
  public static final String MINIMUM_GRADLE_PLUGIN_VERSION_STRING = "2.0.0-alpha1";
  public static final Revision MINIMUM_GRADLE_PLUGIN_VERSION = Revision.parseRevision(MINIMUM_GRADLE_PLUGIN_VERSION_STRING);
  private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup("InstantRun", ToolWindowId.RUN);
  private static final Object INSTANCE_LOCK = new Object();

  // -----------------------------------------------------------------------
  // NOTE: Keep all these communication constants (and message send/receive
  // logic) in sync with the corresponding values in the fast deploy runtime
  // -----------------------------------------------------------------------

  /**
   * Magic (random) number used to identify the protocol
   */
  private static final long PROTOCOL_IDENTIFIER = 0x35107124L;

  /**
   * Version of the protocol
   */
  private static final int PROTOCOL_VERSION = 4;

  /**
   * Message: sending patches
   */
  private static final int MESSAGE_PATCHES = 1;

  /**
   * Message: ping, send ack back
   */
  private static final int MESSAGE_PING = 2;

  /**
   * Message: look up a very quick checksum of the given path; this
   * may not pick up on edits in the middle of the file but should be a
   * quick way to determine if a path exists and some basic information
   * about it.
   */
  @SuppressWarnings("unused")
  public static final int MESSAGE_PATH_EXISTS = 3;

  /**
   * Message: query whether the app has a given file and if so return
   * its checksum. (This is used to determine whether the app can receive
   * a small delta on top of a (typically resource ) file instead of resending the whole
   * file over again.)
   */
  @SuppressWarnings("unused")
  public static final int MESSAGE_PATH_CHECKSUM = 4;

  /**
   * Message: restart activities
   */
  private static final int MESSAGE_RESTART_ACTIVITY = 5;

  /**
   * Message: show toast
   */
  private static final int MESSAGE_SHOW_TOAST = 6;

  /**
   * Done transmitting
   */
  private static final int MESSAGE_EOF = 7;

  /**
   * No updates
   */
  public static final int UPDATE_MODE_NONE = 0;

  /**
   * Patch changes directly, keep app running without any restarting
   */
  public static final int UPDATE_MODE_HOT_SWAP = 1;

  /**
   * Patch changes, restart activity to reflect changes
   */
  public static final int UPDATE_MODE_WARM_SWAP = 2;

  /**
   * Store change in app directory, restart app
   */
  public static final int UPDATE_MODE_COLD_SWAP = 3;

  private static final Logger LOG = Logger.getInstance(FastDeployManager.class);

  /** Display instant run statistics */
  @SuppressWarnings("SimplifiableConditionalExpression")
  public static final boolean DISPLAY_STATISTICS = System.getProperty("fd.stats") != null ? Boolean.getBoolean("fd.stats") : false;

  /** Local port on the desktop machine that we tunnel to the Android device via */
  private static final int STUDIO_PORT = 8888;

  private static final String LOCAL_HOST = "127.0.0.1";
  /** The name of the attribute in the build-info.xml file from Gradle which names the build-id */
  private static final String BUILD_ID_ATTR = "build-id";
  /** The name of the attribute in the build-info.xml file from Gradle which names the verifier status */
  private static final String VERIFIER_ATTR = "verifier";
  /** The value set on the {@link #VERIFIER_ATTR} if the verifier was successful */
  private static final String VERIFIER_COMPATIBLE_VALUE = "COMPATIBLE";
  /** The name of the build timestamp file on the device in the data folder */
  private static final String BUILD_ID_TXT = "build-id.txt";
  /** Prefix for classes.dex files */
  private static final String CLASSES_DEX_PREFIX = "classes";
  /** Suffix for classes.dex files */
  public static final String CLASSES_DEX_SUFFIX = ".dex";

  private static String getDataFolder(@NotNull String applicationId) {
    // Keep in sync with FileManager#getDataFolder in the runtime library
    return "/data/data/" + applicationId + "/files/studio-fd";
  }

  @NotNull
  private static String getDexFileFolder(@NotNull String pkg) {
    // Keep in sync with FileManager#getDexFileFolder in the runtime library
    return getDataFolder(pkg) + "/dex";
  }

  private static final String RESOURCE_FILE_NAME = "resources.ap_";

  @NotNull private final Project myProject;

  /**
   * Whether, since the last build, we've seen changes to any files <b>other</b> than Java or XML files (since Java-only changes
   * can be processed faster by Gradle by skipping portions of the dependency graph, and similarly for resource files)
   */
  private boolean mySeenNonJavaChanges = true; // Initially true: on IDE start we don't know what you've done outside the IDE

  private boolean mySeenLocalJavaChanges;
  private boolean mySeenLocalResourceChanges;

  /** File listener connection */
  @Nullable private MessageBusConnection myConnection;

  /** Don't call directly: this is a project component instantiated by the IDE; use {@link #get(Project)} instead! */
  @SuppressWarnings("WeakerAccess") // Called by infrastructure
  public FastDeployManager(@NotNull Project project) {
    myProject = project;
    if (isInstantRunEnabled(project)) {
      startFileListener();
    }
  }

  /** Returns the per-project instance of the fast deploy manager */
  @NotNull
  public static FastDeployManager get(@NotNull Project project) {
    //noinspection ConstantConditions
    return project.getComponent(FastDeployManager.class);
  }

  /** Finds the devices associated with run configurations for the given project */
  @NotNull
  public static List<IDevice> findDevices(@Nullable Project project) {
    if (project == null) {
      return Collections.emptyList();
    }

    List<RunContentDescriptor> runningProcesses = ExecutionManager.getInstance(project).getContentManager().getAllDescriptors();
    if (runningProcesses.isEmpty()) {
      return Collections.emptyList();
    }
    List<IDevice> devices = Lists.newArrayList();
    for (RunContentDescriptor descriptor : runningProcesses) {
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler instanceof AndroidMultiProcessHandler) {
        AndroidMultiProcessHandler handler = (AndroidMultiProcessHandler)processHandler;
        devices.addAll(handler.getDevices());
      }
      else if (processHandler != null) {
        Client c = processHandler.getUserData(AndroidDebugRunner.ANDROID_DEBUG_CLIENT);
        if (c != null && c.isValid()) {
          devices.add(c.getDevice());
        }
      }
    }

    return devices;
  }

  /**
   * Updates the command line arguments for launching a Gradle task to enable instant run. This method should
   * only be called when instant run is enabled.
   */
  @NotNull
  public List<String> updateGradleCommandLine(@NotNull List<String> original) {
    assert isInstantRunEnabled(myProject);
    for (String arg : original) {
      if (arg.startsWith("-Pandroid.optional.compilation=")) {
        // Already handled (e.g. RESTART_DEX_ONLY)
        return original;
      }
    }
    List<String> arguments = Lists.newArrayListWithExpectedSize(original.size() + 1);
    arguments.addAll(original);
    //noinspection SpellCheckingInspection
    String property = "-Pandroid.optional.compilation=INSTANT_DEV";
    synchronized (INSTANCE_LOCK) {
      if (mySeenNonJavaChanges) {
        mySeenNonJavaChanges = false;
        // Start the file listener again: We turn it off as soon as we see the first non java change
        startFileListener();
      }
      else {
        if (mySeenLocalResourceChanges) {
          property += ",LOCAL_RES_ONLY";
        }

        if (mySeenLocalJavaChanges) {
          property += ",LOCAL_JAVA_ONLY";
        }
      }

      mySeenLocalJavaChanges = false;
      mySeenLocalResourceChanges = false;
      mySeenNonJavaChanges = false;
    }

    arguments.add(property);
    return arguments;
  }

  private void startFileListener() {
    synchronized (INSTANCE_LOCK) {
      if (myConnection == null) {
        myConnection = ApplicationManager.getApplication().getMessageBus().connect();
        myConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
      }
    }
  }

  private void stopFileListener() {
    synchronized (INSTANCE_LOCK) {
      if (myConnection != null) {
        myConnection.disconnect();
        myConnection = null;
      }
    }
  }

  // ---- Implements BulkFileListener ----

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @SuppressWarnings("IfStatementWithIdenticalBranches")
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    if (myProject.isDisposed()) {
      return;
    }

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();

      // Determines whether changing the given file constitutes a "simple Java change" that Gradle
      // can process without involving the full dex dependency chain. This is true for changes to
      // Java files in an app module. It's also true for files <b>not</b> related to compilation.
      // Similarly, it tracks local resource changes.

      if (file == null) {
        continue;
      }

      ProjectFileIndex projectIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
      if (!projectIndex.isInSource(file)) {
        // Ignore common file events -- such as workspace.xml on Window focus loss etc
        if (file.getName().endsWith(DOT_GRADLE)) {
          // build.gradle at the root level is not part of the project
          recordNonLocalChange();
          return;
        }
        continue;
      }

      // Make sure it's not something like for example
      //    .AndroidStudioX.Y/config/options/statistics.application.usages.xml
      Module module = projectIndex.getModuleForFile(file, false);
      if (module == null) {
        continue;
      }

      if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, myProject)) {
        // This filters out edits like .dex files in build etc
        continue;
      }

      // Make sure the editing is in an Android app module
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        FileType fileType = file.getFileType();
        if (fileType == StdFileTypes.JAVA) {
          recordSimpleJavaEdit();
          continue;
        }
        else if (fileType == StdFileTypes.XML &&
                 !file.getName().equals(ANDROID_MANIFEST_XML) &&
                 AndroidResourceUtil.isResourceFile(file, facet)) {
          recordSimpleResourceEdit();
          continue;
        }
        else if (fileType.isBinary() &&
                 fileType == FileTypeManager.getInstance().getFileTypeByExtension(EXT_PNG) &&
                 AndroidResourceUtil.isResourceFile(file, facet)) {
          // Drawable resource
          recordSimpleResourceEdit();
          continue;
        } // else: It's possible that it's an edit to a resource of an arbitrary file type in res/raw*/ or other assets;
        // for now, these will result in full incremental rebuilds.
      } // else: edit in a non-Android module or a library: these require full incremental builds

      recordNonLocalChange();
      break;
    }
  }

  /** Called when we've noticed an edit of a Java file that is in an app module */
  private void recordSimpleJavaEdit() {
    synchronized (INSTANCE_LOCK) {
      mySeenLocalJavaChanges = true;
    }
  }

  /** Called when we've noticed an edit of a resource file that is in an app module */
  private void recordSimpleResourceEdit() {
    synchronized (INSTANCE_LOCK) {
      mySeenLocalResourceChanges = true;
    }
  }

  /** Called when we've noticed an edit outside of an app module, or in something other than a resource file or a Java file */
  private void recordNonLocalChange() {
    synchronized (INSTANCE_LOCK) {
      mySeenNonJavaChanges = true;
      // We no longer need to listen for changes to files while the user continues editing until the next build
      stopFileListener();
    }
  }

  /**
   * The application state on device/emulator: the app is not running (or we cannot find it due to
   * connection problems etc), or it's in the foreground or background.
   */
  public enum AppState {
    /** The app is not running (or we cannot find it due to connection problems etc) */
    NOT_RUNNING,
    /** The app is running an obsolete/older version of the runtime library */
    OBSOLETE,
    /** The app is actively running in the foreground */
    FOREGROUND,
    /** The app is running, but is not in the foreground */
    BACKGROUND
  }

  /**
   * Checks whether the app associated with the given module is already running on the given device
   *
   * @param device the device to check
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is already running and is listening for incremental updates
   */
  public static boolean isAppRunning(@NotNull IDevice device, @NotNull Module module) {
    AppState appState = getAppState(device, module);
    // TODO: Use appState != AppState.NOT_RUNNING instead when we automatically
    // handle fronting background activities here
    return appState == AppState.FOREGROUND;
  }

  /**
   * Returns the build id in the project as seen by the IDE
   *
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return the build id, if found
   */
  @Nullable
  private static String getLocalBuildId(@NotNull Module module) {
    Element root = getLocalBuildInfo(module);
    if (root == null) {
      return null;
    }

    String id = root.getAttribute(BUILD_ID_ATTR);
    if (id.isEmpty()) {
      return null;
    }

    return id;
  }

  /**
   * Returns the verifier status from the last build
   *
   * @param model the relevant model
   * @return the status; "COMPATIBLE" if the build succeeded
   */
  @Nullable
  private static String getVerifierStatus(@NotNull AndroidGradleModel model) {
    Element root = getLocalBuildInfo(getLocalBuildInfoFile(model));
    if (root == null) {
      return null;
    }

    String id = root.getAttribute(VERIFIER_ATTR);
    if (id.isEmpty()) {
      return null;
    }

    return id;
  }

  @Nullable
  private static Element getLocalBuildInfo(@NotNull Module module) {
    assert isPatchableApp(module);

    AndroidGradleModel model = getAppModel(module);
    return getLocalBuildInfo(getLocalBuildInfoFile(model));
  }

  @Nullable
  private static Element getLocalBuildInfo(@Nullable File file) {
    try {
      if (file != null && file.exists()) {
        String xml = Files.toString(file, Charsets.UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, false);
        if (document != null) {
          return document.getDocumentElement();
        }
      }
    }
    catch (Throwable t) {
      LOG.warn(t);
    }

    return null;
  }

  @Nullable
  private static File getLocalBuildInfoFile(@Nullable AndroidGradleModel model) {
    try {
      if (model != null) {
        InstantRun instantRun = model.getSelectedVariant().getMainArtifact().getInstantRun();
        File file = instantRun.getInfoFile();
        if (!file.exists()) {
          // Temporary hack workaround; model is passing the wrong value! See InstantRunAnchorTask.java
          return new File(instantRun.getRestartDexFile().getParentFile(), "build-info.xml");
        }
        return file;
      }
    }
    catch (Throwable t) {
      LOG.warn(t);
    }

    return null;
  }

  /**
   * Returns the build id on the device
   *
   * @param device the device to check
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return the build id, if found
   */
  @Nullable
  private static String getDeviceBuildId(@NotNull IDevice device, @NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return null;
    }

    String pkg = getPackageName(facet);
    if (pkg == null) {
      return null;
    }

    try {
      File localIdFile = FileUtil.createTempFile("build-id", "txt");

      String remoteIdFile = getDataFolder(pkg) + "/" + BUILD_ID_TXT;

      // on a user device, we cannot pull from a path where the segments aren't readable (I think this is a ddmlib limitation)
      // So we first copy to /data/local/tmp and pull from there..
      String remoteTmpFile = "/data/local/tmp/build-id.txt";
      device.executeShellCommand("cp " + remoteIdFile + " " + remoteTmpFile, new CollectingOutputReceiver());
      device.pullFile(remoteTmpFile, localIdFile.getPath());

      String id = Files.toString(localIdFile, Charsets.UTF_8).trim();

      //noinspection ResultOfMethodCallIgnored
      localIdFile.delete();

      return id;
    } catch (IOException ignore) {
    }
    catch (AdbCommandRejectedException e) {
      LOG.warn(e);
    }
    catch (SyncException e) {
      LOG.warn(e);
    }
    catch (TimeoutException e) {
      LOG.warn(e);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.warn(e);
    }

    return null;
  }

  /**
   * Checks whether the local and remote build id's match
   *
   * @param device the device to pull the id from
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the build id's match. If not, there has been some intermediate build locally (or a clean)
   *              such that Gradle state doesn't match what's on the device
   */
  public static boolean buildIdsMatch(@NotNull IDevice device, @NotNull Module module) {
    String localBuildId = getLocalBuildId(module);
    if (localBuildId == null) {
      return false;
    }
    String deviceBuildId = getDeviceBuildId(device, module);
    return localBuildId.equals(deviceBuildId);
  }

  /**
   * Called after a build &amp; successful push to device: updates the build id on the device to whatever the
   * build id was assigned by Gradle.
   *
   * @param device the device to push to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void transferLocalIdToDeviceId(@NotNull IDevice device, @NotNull Module module) {
    AndroidGradleModel model = getAppModel(module);
    if (model == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null : module; // because we have an AndroidGradleModel
    String pkg = getPackageName(facet);
    if (pkg == null) {
      return;
    }

    File file = getLocalBuildInfoFile(model);
    if (file == null) {
      return;
    }

    String buildId = StringUtil.notNullize(getLocalBuildId(module));
    try {
      String remote = copyToDeviceScratchFile(device, pkg, buildId);

      String dataDir = getDataFolder(pkg);

      // Essentially does: cp /data/local/tmp/build.txt /data/data/pkg/files/studio-fd/build.txt
      String cmd = "run-as " + pkg + " mkdir -p " + dataDir + "; run-as " + pkg + " cp " + remote + " " + dataDir + "/" + BUILD_ID_TXT;
      CollectingOutputReceiver receiver = new CollectingOutputReceiver();
      device.executeShellCommand(cmd, receiver);
      String output = receiver.getOutput();
      if (!output.trim().isEmpty()) {
        LOG.warn(output);
      }
    } catch (IOException ioe) {
      LOG.warn("Couldn't write build id file", ioe);
    }
    catch (AdbCommandRejectedException e) {
      LOG.warn(e);
    }
    catch (TimeoutException e) {
      LOG.warn(e);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.warn(e);
    }
    catch (SyncException e) {
      LOG.warn(e);
    }
  }

  @NotNull
  private static String copyToDeviceScratchFile(@NotNull IDevice device, @NotNull String pkgName, @NotNull String contents)
    throws IOException, AdbCommandRejectedException, SyncException, TimeoutException {

    File local = null;
    try {
      local = FileUtil.createTempFile("data", "fdr");
      Files.write(contents.getBytes(Charsets.UTF_8), local);
      return copyToDeviceScratchFile(device, pkgName, local);
    } finally {
      if (local != null) {
        //noinspection ResultOfMethodCallIgnored
        local.delete();
      }
    }
  }

  @NotNull
  private static String copyToDeviceScratchFile(@NotNull IDevice device, @NotNull String pkgName, @NotNull File local)
    throws IOException, AdbCommandRejectedException, SyncException, TimeoutException {
    String remoteTmpBuildId = "/data/local/tmp/" + pkgName + "-data.fdr";
    device.pushFile(local.getAbsolutePath(), remoteTmpBuildId);
    return remoteTmpBuildId;
  }

  /**
   * Returns true if the app associated with the given module can be dex swapped.
   * That's the case if the app is already installed, and the build id's match.
   *
   * @param module  a module context, normally the main app module (but if it's a library module
   *                the infrastructure will look for other app modules
   * @param devices the set of devices to check
   * @return true if the app can be dex swapped in one or more of the given devices
   */
  public static boolean canDexSwap(@NotNull Module module, @NotNull Collection<IDevice> devices) {
    // TODO: we need to fix 2 things: a) update resources, b) handle no-changes
    //for (IDevice device : devices) {
    //  if (buildIdsMatch(device, module)) {
    //    return true;
    //  }
    //}

    return false;
  }

  /**
   * Dex swap the app on the given device. Should only be called if {@link #canDexSwap(Module, Collection)} returned true
   *
   * @param facet  the app module's facet
   * @param device the device to install it on
   * @return true if installation succeeded
   */
  public static boolean installDex(@NotNull AndroidFacet facet, @NotNull IDevice device) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model == null) {
      return false;
    }

    File restart = DexFileType.RESTART_DEX.getFile(model);
    if (!restart.exists()) {
      // TODO: is this really a "no changes" scenario?
      // TODO: to reproduce: launch app, terminate it, restart IDE, re-launch (no changes to code in the IDE)
      LOG.warn("Couldn't find restart file " + restart);
      return false;
    }

    String pkg = getPackageName(facet);
    if (pkg == null) {
      return false;
    }

    try {
      String dexFolder = getDexFileFolder(pkg);

      // Ensure the dir exists
      CollectingOutputReceiver receiver = new CollectingOutputReceiver();
      device.executeShellCommand("run-as " + pkg + " mkdir -p " + dexFolder, receiver);
      receiver = new CollectingOutputReceiver();
      String output = receiver.getOutput();
      if (!output.trim().isEmpty()) {
        LOG.warn(output);
      }

      // List the files in the dex folder to compute a new .dex file name that follows the existing
      // naming pattern but is numbered higher than any existing .dex files
      device.executeShellCommand("run-as " + pkg + " ls " + dexFolder, receiver);
      int max = getMaxDexFileNumber(receiver.getOutput());
      String fileName = String.format("%s0x%04x%s", CLASSES_DEX_PREFIX, max + 1, CLASSES_DEX_SUFFIX);
      String target = dexFolder + "/" + fileName;

      // Copy the restart .dex file over to the device in the dex folder with the new, unique name
      String remote = copyToDeviceScratchFile(device, pkg, restart);
      String cmd = "run-as " + pkg + " cp " + remote + " " + target;
      receiver = new CollectingOutputReceiver();
      device.executeShellCommand(cmd, receiver);
      output = receiver.getOutput();
      if (!output.trim().isEmpty()) {
        LOG.warn(output);
      }

      // TODO: Push a .dex.index file too? We can't do it right now;
      // on the device side we iterate through the .dex file and write
      // entries like this:
      //    DexFile dexFile = new DexFile(file);
      //    Enumeration<String> entries = dexFile.entries();
      //    while (entries.hasMoreElements()) {
      //      String nextPath = entries.nextElement();
      //      if (nextPath.indexOf('$') != -1) {
      //          (write entry, one per line)
      // However, we don't have a DexFile implementation here.
      // Instead, rely on this being handled on the server side. (For now
      // this means the classes won't be cleaned up.)

      // Record the new build id on the device
      transferLocalIdToDeviceId(device, facet.getModule());

      return true;
    } catch (IOException ioe) {
      LOG.warn("Couldn't write build id file", ioe);
    }
    catch (AdbCommandRejectedException e) {
      LOG.warn(e);
    }
    catch (TimeoutException e) {
      LOG.warn(e);
    }
    catch (ShellCommandUnresponsiveException e) {
      LOG.warn(e);
    }
    catch (SyncException e) {
      LOG.warn(e);
    }

    return false;
  }

  private static int getMaxDexFileNumber(@NotNull String fileListing) {
    int max = -1;

    for (String name : Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().splitToList(fileListing)) {
      if (name.startsWith(CLASSES_DEX_PREFIX) && name.endsWith(CLASSES_DEX_SUFFIX)) {
        String middle = name.substring(CLASSES_DEX_PREFIX.length(),
                                       name.length() - CLASSES_DEX_SUFFIX.length());
        try {
          int version = Integer.decode(middle);
          if (version > max) {
            max = version;
          }
        } catch (NumberFormatException ignore) {
        }
      }
    }

    return max;
  }

  /**
   * Restart the activity on this device, if it's running and is in the foreground
   * @param device the device to apply the change to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   */
  public static void restartActivity(@NotNull IDevice device, @NotNull Module module) {
    AppState appState = getAppState(device, module);
    if (appState == AppState.FOREGROUND || appState == AppState.BACKGROUND) {
      final AndroidFacet facet = findAppModule(module, module.getProject());
      if (facet == null) {
        return;
      }
      talkToApp(device, facet, new Communicator<Boolean>() {
        @Override
        public Boolean communicate(@NotNull DataInputStream input, @NotNull DataOutputStream output) throws IOException {
          output.writeInt(MESSAGE_RESTART_ACTIVITY);
          writeToken(facet, output);
          return false;
        }
      }, true);
    }
  }

  @Nullable
  private static AndroidGradleModel getAppModel(@NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return null;
    }

    return AndroidGradleModel.get(facet);
  }

  /**
   * Checks whether the app associated with the given module is capable of being run time patched
   * (whether or not it's running). This checks whether we have a Gradle project, and if that
   * Gradle project is using a recent enough Gradle plugin with incremental support, etc. It
   * also checks whether the user has disabled instant run.
   *
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @return true if the app is using an incremental support enabled Gradle plugin
   */
  public static boolean isPatchableApp(@NotNull Module module) {
    if (!isInstantRunEnabled(module.getProject())) {
      return false;
    }

    AndroidGradleModel model = getAppModel(module);
    if (model == null) {
      return false;
    }

    String version = model.getAndroidProject().getModelVersion();
    try {
      // Sigh, would be nice to have integer versions to avoid having to do this here
      Revision revision = Revision.parseRevision(version);

      // Supported in version 1.6 of the Gradle plugin and up
      return revision.compareTo(MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
    } catch (NumberFormatException ignore) {
      return false;
    }
  }

  /**
   * Returns whether an update will result in a cold swap by looking at the results of a gradle build.
   */
  public static boolean isColdSwap(@NotNull AndroidGradleModel model) {
    String status = getVerifierStatus(model);
    if (status != null) {
      return !VERIFIER_COMPATIBLE_VALUE.equals(status);
    }

    File restart = DexFileType.RESTART_DEX.getFile(model);
    if (!restart.exists()) {
      return false;
    }

    return DexFileType.RELOAD_DEX.getFile(model).exists();
  }

  /**
   * Performs an incremental update of the app associated with the given module on the given device
   *
   * @param device the device to apply the update to
   * @param module a module context, normally the main app module (but if it's a library module
   *               the infrastructure will look for other app modules
   * @param forceRestart if true, force a full restart of the given app (normally false)
   */
  public static void perform(@NotNull final IDevice device, @NotNull final Module module, final boolean forceRestart) {
    Project project = module.getProject();
    UpdateMode updateMode = forceRestart ? UpdateMode.COLD_SWAP : UpdateMode.HOT_SWAP;
    FastDeployManager manager = get(project);
    manager.performUpdate(device, updateMode, module);
  }

  public static void pushChanges(@NotNull final IDevice device, @NotNull final AndroidFacet facet) {
    FastDeployManager manager = get(facet.getModule().getProject());
    manager.pushChanges(device, facet, getLastInstalledArscTimestamp(device, facet));
  }

  private static long getLastInstalledArscTimestamp(@NotNull IDevice device, @NotNull AndroidFacet facet) {
    String pkgName = getPackageName(facet);
    if (pkgName == null) {
      return 0;
    }

    return ServiceManager.getService(InstalledPatchCache.class).getInstalledArscTimestamp(device, pkgName);
  }

  /** Returns true if any of the devices in the given list require a rebuild */
  public static boolean isRebuildRequired(@NotNull Collection<IDevice> devices, @NotNull Module module) {
    for (IDevice device : devices) {
      if (isRebuildRequired(device, module)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Returns true if a full rebuild is required for the app. Currently, this is the
   * case if something in the manifest has changed (at the moment, we're only looking
   * at manifest file edits, not diffing the contents or disregarding "irrelevant"
   * edits such as whitespace or comments.
   */
  private static boolean isRebuildRequired(@NotNull IDevice device, @NotNull Module module) {
    FastDeployManager manager = get(module.getProject());
    AndroidFacet facet = manager.findAppModule(module);
    if (facet == null) {
      return false;
    }

    String pkgName = getPackageName(facet);
    if (pkgName == null) {
      return true;
    }

    InstalledPatchCache cache = ServiceManager.getService(InstalledPatchCache.class);

    long currentTimeStamp = getManifestLastModified(facet);
    long installedTimeStamp = cache.getInstalledManifestTimestamp(device, pkgName);

    if (currentTimeStamp <= installedTimeStamp) {
      // See if the resources have changed.
      // Since this method can be called before we've built, we're looking at the previous
      // manifest now. However, the above timestamp check will already cause us to treat
      // manifest edits as requiring restarts -- so the goal here is to look for the referenced
      // resources from the manifest (when the manifest itself hasn't been edited) and see
      // if any of *them* have changed.
      HashCode currentHash = InstalledPatchCache.computeManifestResources(facet);
      HashCode installedHash = cache.getInstalledManifestResourcesHash(device, pkgName);
      if (installedHash != null && !installedHash.equals(currentHash)) {
        // Yes, some resources have changed.
        return true;
      }

      return false;
    }

    return true;
  }

  /**
   * Returns the timestamp of the most recently modified manifest file applicable for the given facet
   */
  private static long getManifestLastModified(@NotNull AndroidFacet facet) {
    long maxLastModified = 0L;
    AndroidModel androidModel = facet.getAndroidModel();
    if (androidModel != null) {
      // Suppress deprecation: the recommended replacement is not suitable
      // (that's an API for VirtualFiles; we need the java.io.File instances)
      //noinspection deprecation
      for (SourceProvider provider : androidModel.getActiveSourceProviders()) {
        File manifest = provider.getManifestFile();
        long lastModified = manifest.lastModified();
        maxLastModified = Math.max(maxLastModified, lastModified);
      }
    }

    return maxLastModified;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "FastDeployManager";
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  /** Is instant run enabled in the given project */
  public static boolean isInstantRunEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.INSTANT_RUN;
  }

  /** Is showing toasts enabled in the given project */
  public static boolean isShowToastEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.SHOW_TOAST;
  }

  /** Synchronizes the file listening state with whether instant run is enabled */
  static void updateFileListener(@NotNull Project project) {
    FastDeployManager manager = get(project);
    boolean listening = manager.myConnection != null;
    if (isInstantRunEnabled(project)) {
      if (!listening) {
        manager.startFileListener();
      }
    } else if (listening) {
      manager.stopFileListener();
    }
  }

  /** Assuming instant run is enabled, does code patching require an activity restart in the given project? */
  public static boolean isRestartActivity(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.RESTART_ACTIVITY;
  }

  public void performUpdate(@NotNull IDevice device, @NotNull UpdateMode updateMode, @Nullable Module module) {
    AndroidFacet facet = findAppModule(module);
    if (facet != null) {
      AndroidGradleModel model = AndroidGradleModel.get(facet);
      if (model != null) {
        runGradle(device, model, facet, updateMode);
      }
    }
  }

  private void pushChanges(@NotNull IDevice device, @NotNull AndroidFacet facet, long deviceArscTimestamp) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      afterBuild(device, model, facet, UpdateMode.HOT_SWAP, deviceArscTimestamp);
    }
  }

  private void runGradle(@NotNull final IDevice device,
                         @NotNull final AndroidGradleModel model,
                         @NotNull final AndroidFacet facet,
                         @NotNull final UpdateMode updateMode) {
    File arsc = findResourceArsc(facet);
    final long arscBefore = arsc != null ? arsc.lastModified() : 0L;

    // Clean out *old* patch files (e.g. from a previous build such that if you for example
    // only change a resource, we don't redeploy the same .dex file over and over!
    // This should be performed by the Gradle plugin; this is a temporary workaround.
    removeOldPatches(model);

    final Project project = facet.getModule().getProject();
    final GradleInvoker invoker = GradleInvoker.getInstance(project);

    final Ref<GradleInvoker.AfterGradleInvocationTask> reference = Ref.create();
    final GradleInvoker.AfterGradleInvocationTask task = new GradleInvoker.AfterGradleInvocationTask() {
      @Override
      public void execute(@NotNull GradleInvocationResult result) {
        // Get rid of listener. We should add more direct task listening to the GradleTasksExecutor; this
        // seems race-condition and unintentional side effect prone.
        invoker.removeAfterGradleInvocationTask(reference.get());

        // Build is done: send message to app etc
        afterBuild(device, model, facet, updateMode, arscBefore);
      }
    };
    reference.set(task);
    invoker.addAfterGradleInvocationTask(task);
    String taskName = getIncrementalDexTask(model, facet.getModule());
    invoker.executeTasks(Collections.singletonList(taskName));
  }

  @Nullable
  private static File findBuildFolder(@NotNull AndroidFacet facet) {
    String rootPath = AndroidRootUtil.getModuleDirPath(facet.getModule());
    if (rootPath == null) {
      return null;
    }
    File root = new File(FileUtil.toSystemDependentName(rootPath));

    File build = new File(root, "build");
    if (build.exists()) {
      return build;
    }

    return null;
  }

  /** Looks up the merged manifest file for a given facet */
  @Nullable
  public static File findMergedManifestFile(@NotNull AndroidFacet facet) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      AndroidArtifact mainArtifact = model.getSelectedVariant().getMainArtifact();
      Collection<AndroidArtifactOutput> outputs = mainArtifact.getOutputs();
      for (AndroidArtifactOutput output : outputs) {
        // For now, use first manifest file that exists
        File manifest = output.getGeneratedManifest();
        if (manifest.exists()) {
          return manifest;
        }
      }
    }

    return null;
  }

  // TODO: Get the intermediates folder from the model itself!
  @Nullable
  private static File findIntermediatesFolder(@NotNull AndroidFacet facet) {
    File build = findBuildFolder(facet);
    if (build != null) {
      File intermediates = new File(build, "intermediates");
      if (intermediates.exists()) {
        return intermediates;
      }
    }

    return null;
  }

  @Nullable
  public static File findResourceArsc(@NotNull AndroidFacet facet) {
    File intermediates = findIntermediatesFolder(facet);
    if (intermediates != null) {
      String variantName = getVariantName(facet);
      File resourceClassFolder = new File(intermediates, "res" + File.separator + "resources-" + variantName + ".ap_");
      if (resourceClassFolder.exists()) {
        return resourceClassFolder;
      }
    }

    return null;
  }

  @NotNull
  public static String getIncrementalDexTask(@NotNull AndroidGradleModel model, @NotNull Module module) {
    assert isInstantRunSupported(model) : module;
    String taskName = model.getSelectedVariant().getMainArtifact().getInstantRun().getIncrementalAssembleTaskName();
    String gradlePath = GradleUtil.getGradlePath(module);
    if (gradlePath != null) {
      taskName = gradlePath + ":" + taskName;
    }
    return taskName;
  }

  /** Returns true if Instant Run is supported for this gradle model (whether or not it's enabled) */
  public static boolean isInstantRunSupported(@NotNull AndroidGradleModel model) {
    String version = model.getAndroidProject().getModelVersion();
    try {
      Revision modelVersion = Revision.parseRevision(version);

      // Supported in version 1.6 of the Gradle plugin and up
      return modelVersion.compareTo(MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
    } catch (NumberFormatException e) {
      Logger.getInstance(FastDeployManager.class).warn("Failed to parse '" + version + "'", e);
      return false;
    }
  }

  /**
   * Dex file types produced by the build system.
   */
  enum DexFileType {
    RELOAD_DEX {
      @NotNull
      @Override
      File getFile(AndroidGradleModel model) {
        return model.getSelectedVariant().getMainArtifact().getInstantRun().getReloadDexFile();
      }
    },
    RESTART_DEX {
      @NotNull
      @Override
      File getFile(AndroidGradleModel model) {
        return model.getSelectedVariant().getMainArtifact().getInstantRun().getRestartDexFile();
      }
    };

    /**
     * Returns the dex file location for this dex file type.
     * @param model gradle model
     * @return a file location for a possibly existing file.
     */
    @NotNull
    abstract File getFile(AndroidGradleModel model);
  }

  private void afterBuild(@NotNull IDevice device,
                          @NotNull AndroidGradleModel model,
                          @NotNull AndroidFacet facet,
                          @NotNull UpdateMode updateMode,
                          long arscBefore) {
    List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(4);

    updateMode = gatherGradleCodeChanges(model, changes, facet, updateMode);
    updateMode = gatherGradleResourceChanges(model, facet, changes, arscBefore, updateMode);

    push(device, facet.getModule(), changes, updateMode);

    String pkgName = getPackageName(facet);
    if (pkgName == null) {
      return;
    }
    File resourceArsc = findResourceArsc(facet);
    if (resourceArsc != null) {
      long timestamp = resourceArsc.lastModified();
      InstalledPatchCache patchCache = ServiceManager.getService(InstalledPatchCache.class);
      patchCache.setInstalledArscTimestamp(device, pkgName, timestamp);
    }

    // Note that while we update the patch cache with the resource file timestamp here,
    // we *don't* do that for the manifest file: the resource timestamp is updated because
    // the resource files will be pushed to the app, but the manifest changes can't be.

    refreshDebugger(pkgName);
  }

  private void refreshDebugger(String packageName) {
    // First we reapply the breakpoints on the new code, otherwise the breakpoints
    // remain set on the old classes and will never be hit again.
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
        if (!debugger.getSessions().isEmpty()) {
          List<Breakpoint> breakpoints = debugger.getBreakpointManager().getBreakpoints();
          for (Breakpoint breakpoint : breakpoints) {
            if (breakpoint.isEnabled()) {
              breakpoint.setEnabled(false);
              breakpoint.setEnabled(true);
            }
          }
        }
      }
    });

    // Now we refresh the call-stacks and the variable panes.
    DebuggerManagerEx debugger = DebuggerManagerEx.getInstanceEx(myProject);
    for (final DebuggerSession session : debugger.getSessions()) {
      Client client = session.getProcess().getProcessHandler().getUserData(AndroidDebugRunner.ANDROID_DEBUG_CLIENT);
      if (client != null && client.isValid() && StringUtil.equals(packageName, client.getClientData().getClientDescription())) {
        session.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            DebuggerContextImpl context = session.getContextManager().getContext();
            SuspendContextImpl suspendContext = context.getSuspendContext();
            if (suspendContext != null) {
              XExecutionStack stack = suspendContext.getActiveExecutionStack();
              if (stack != null) {
                ((JavaExecutionStack)stack).initTopFrame();
              }
            }
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                session.refresh(false);
                XDebugSession xSession = session.getXDebugSession();
                if (xSession != null) {
                  xSession.rebuildViews();
                }
              }
            });
          }
        });
      }
    }
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"}) // won't be as soon as it really calls Gradle
  @NotNull
  private static UpdateMode gatherGradleResourceChanges(AndroidGradleModel model,
                                                 AndroidFacet facet,
                                                 List<ApplicationPatch> changes,
                                                 long arscBefore,
                                                 @NotNull UpdateMode updateMode) {
    File arsc = findResourceArsc(facet);
    if (arsc != null && arsc.lastModified() > arscBefore) {
      String path = RESOURCE_FILE_NAME;
      try {
        byte[] bytes = Files.toByteArray(arsc);
        changes.add(new ApplicationPatch(path, bytes));
        return updateMode.combine(UpdateMode.WARM_SWAP);
      }
      catch (IOException e) {
        LOG.warn("Couldn't read resource file file " + arsc);
      }
    }

    return updateMode;
  }

  @NotNull
  private static UpdateMode gatherGradleCodeChanges(AndroidGradleModel model,
                                                    List<ApplicationPatch> changes,
                                                    @NotNull AndroidFacet facet,
                                                    @NotNull UpdateMode updateMode) {
    try {
      File incremental = DexFileType.RELOAD_DEX.getFile(model);
      boolean canReload = incremental.exists();
      if (canReload) {
        byte[] bytes = Files.toByteArray(incremental);
        changes.add(new ApplicationPatch("classes.dex.3", bytes));
        updateMode = updateMode.combine(UpdateMode.HOT_SWAP);
      }

      File restart = DexFileType.RESTART_DEX.getFile(model);
      if (restart.exists()) {
        byte[] bytes = Files.toByteArray(restart);
        changes.add(new ApplicationPatch("classes.dex", bytes));
        if (!canReload) {
          updateMode = updateMode.combine(UpdateMode.COLD_SWAP);
        }
      }
    }
    catch (Throwable t) {
      Logger.getInstance(FastDeployManager.class).error("Couldn't generate dex", t);
    }

    String status = getVerifierStatus(model);
    if (status != null && !status.equals(VERIFIER_COMPATIBLE_VALUE)) {
      // Convert tokens like "FIELD_REMOVED" to "Field Removed" for better readability
      status = StringUtil.capitalizeWords(status.toLowerCase(Locale.US).replace('_', ' '), true);
      postBalloon(MessageType.WARNING, "Couldn't apply changes on the fly: " + status, facet.getModule().getProject());
    }

    return updateMode;
  }

  public static void removeOldPatches(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      removeOldPatches(model);
    }
  }

  private static void removeOldPatches(AndroidGradleModel model) {
    // This method may be called even when instant run isn't eligible
    if (!isInstantRunSupported(model)) {
      return;
    }
    File restart = DexFileType.RESTART_DEX.getFile(model);
    if (restart.exists()) {
      boolean deleted = restart.delete();
      if (!deleted) {
        Logger.getInstance(FastDeployManager.class).error("Couldn't delete " + restart);
      }
    }
    File incremental = DexFileType.RELOAD_DEX.getFile(model);
    if (incremental.exists()) {
      boolean deleted = incremental.delete();
      if (!deleted) {
        Logger.getInstance(FastDeployManager.class).error("Couldn't delete " + incremental);
      }
    }
  }

  @NotNull
  private static String getVariantName(@NotNull AndroidFacet facet) {
    return getVariantName(AndroidGradleModel.get(facet));
  }

  @NotNull
  private static String getVariantName(@Nullable AndroidGradleModel model) {
    if (model != null) {
      return model.getSelectedVariant().getName();
    }

    return "debug";
  }

  static void postBalloon(@NotNull final MessageType type, @NotNull final String message, @NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
        if (frame != null) {
          JComponent component = frame.getRootPane();
          if (component != null) {
            Rectangle rect = component.getVisibleRect();
            Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
            RelativePoint point = new RelativePoint(component, p);
            BalloonBuilder builder =
              JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, type.getDefaultIcon(), type.getPopupBackground(), null);
            builder.setShowCallout(false);
            builder.setCloseButtonEnabled(true);
            builder.createBalloon().show(point, Balloon.Position.atLeft);
          }
        }
      }
    });
  }

  private static void writeToken(
    // we'll need this when really looking up the token from the build area
    @SuppressWarnings("UnusedParameters") @NotNull AndroidFacet facet, @NotNull DataOutputStream output) throws IOException {
    // TODO: Look up persistent token here
    long token = 0L;
    output.writeLong(token);
  }

  private void push(@Nullable IDevice device,
                    @Nullable final Module module,
                    @NotNull final List<ApplicationPatch> changes,
                    @NotNull UpdateMode updateMode) {
    if (changes.isEmpty() || updateMode == UpdateMode.NO_CHANGES) {
      // Sync the build id to the device; Gradle might rev the build id even when there are no changes,
      // and we need to make sure that the device id reflects this new build id, or the next
      // build will discover different id's and will conclude that it needs to do a full rebuild
      AndroidFacet facet = findAppModule(module);
      if (device != null && facet != null) {
        transferLocalIdToDeviceId(device, facet.getModule());
      }

      Notification notification = NOTIFICATION_GROUP.createNotification("Instant Run:", "No Changes.", NotificationType.INFORMATION, null);
      notification.notify(myProject);
      return;
    }

    if (updateMode == UpdateMode.HOT_SWAP && isRestartActivity(myProject)) {
      updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
    }

    if (device != null) {
      final UpdateMode updateMode1 = updateMode;
      final AndroidFacet facet = findAppModule(module);
      if (facet != null) {
        talkToApp(device, facet, new Communicator<Boolean>() {
          @Override
          public Boolean communicate(@NotNull DataInputStream input, @NotNull DataOutputStream output) throws IOException {
            output.writeInt(MESSAGE_PATCHES);
            writeToken(facet, output);
            ApplicationPatch.write(output, changes, updateMode1);

            // Let the app know whether it should show toasts
            output.writeBoolean(isShowToastEnabled(myProject));

            // Finally read a boolean back from the other side; this has the net effect of
            // waiting until applying/verifying code on the other side is done. (It doesn't
            // count the actual restart time, but for activity restarts it's typically instant,
            // and for cold starts we have no easy way to handle it (the process will die and a
            // new process come up; to measure that we'll need to work a lot harder.)
            input.readBoolean();

            return false;
          }

          @Override
          int getTimeout() {
            return 8000; // allow up to 8 seconds for resource push
          }
        }, true);

        transferLocalIdToDeviceId(device, facet.getModule());
      }
    }
    if (DISPLAY_STATISTICS) {
      notifyEnd(myProject);
    }

    if (updateMode == UpdateMode.HOT_SWAP && !isRestartActivity(myProject) && !ourHideRestartTip) {
      StringBuilder sb = new StringBuilder(300);
      sb.append("<html>");
      sb.append("Instant Run applied code changes.\n");
      sb.append("You can restart the current activity by clicking <a href=\"restart\">here</a>");
      Shortcut[] shortcuts = ActionManager.getInstance().getAction("Android.RestartActivity").getShortcutSet().getShortcuts();
      String shortcut;
      if (shortcuts.length > 0) {
        shortcut = KeymapUtil.getShortcutText(shortcuts[0]);
        sb.append(" or pressing ").append(shortcut).append(" anytime");
      }
      sb.append(".\n");

      sb.append("You can also <a href=\"configure\">configure</a> restarts to happen automatically. ");
      sb.append("(<a href=\"dismiss\">Dismiss</a>, <a href=\"dismiss_all\">Dismiss All</a>)");
      sb.append("</html>");
      String message = sb.toString();
      final Ref<Notification> notificationRef = Ref.create();
      NotificationListener listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String action = event.getDescription();
            if ("restart".equals(action)) {
              if (module != null) {
                RestartActivityAction.restartActivity(module);
              }
            }
            else if ("configure".equals(action)) {
              InstantRunConfigurable configurable = new InstantRunConfigurable(myProject);
              ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable);
            }
            else if ("dismiss".equals(action)) {
              notificationRef.get().hideBalloon();
            }
            else if ("dismiss_all".equals(action)) {
              //noinspection AssignmentToStaticFieldFromInstanceMethod
              ourHideRestartTip = true;
              notificationRef.get().hideBalloon();
            }
            else {
              assert false : action;
            }
          }
        }
      };
      Notification notification = NOTIFICATION_GROUP.createNotification("Instant Run", message, NotificationType.INFORMATION, listener);
      notificationRef.set(notification);
      notification.notify(myProject);
    }
  }

  private static boolean ourHideRestartTip;

  @Nullable
  private static String getPackageName(@Nullable AndroidFacet facet) {
    if (facet != null) {
      try {
        return ApkProviderUtil.computePackageName(facet);
      }
      catch (ApkProvisionException e) {
        AndroidModuleInfo info = AndroidModuleInfo.get(facet);
        if (info.getPackage() != null) {
          return info.getPackage();
        }
      }
    }

    return null;
  }

  @Nullable
  private AndroidFacet findAppModule(@Nullable Module module) {
    return findAppModule(module, myProject);
  }

  @Nullable
  private static AndroidFacet findAppModule(@Nullable Module module, @NotNull Project project) {
    if (module != null) {
      assert module.getProject() == project;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }

    // TODO: Here we should really look for app modules that *depend*
    // on the given module (if non null), not just use the first app
    // module we find.

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(m);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }
    return null;
  }

  public static void showToast(@NotNull IDevice device, @NotNull Module module, @NotNull final String message) {
    try {
      talkToApp(device, findAppModule(module, module.getProject()), new Communicator<Boolean>() {
        @Override
        public Boolean communicate(@NotNull DataInputStream input, @NotNull DataOutputStream output) throws IOException {
          output.writeInt(MESSAGE_SHOW_TOAST);
          output.writeUTF(message);
          return false;
        }
      }, true);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }
  }

  /**
   * Attempts to connect to a given device and sees if an instant run enabled app is running there.
   */
  @NotNull
  public static AppState getAppState(@NotNull IDevice device, @NotNull Module module) {
    try {
      return talkToApp(device, findAppModule(module, module.getProject()), new Communicator<AppState>() {
        @Override
        public AppState communicate(@NotNull DataInputStream input, @NotNull DataOutputStream output) throws IOException {
          output.writeInt(MESSAGE_PING);
          // Wait for "pong"
          boolean foreground = input.readBoolean();
          LOG.info("Ping sent and replied successfully, application seems to be running. Foreground=" + foreground);
          return foreground ? AppState.FOREGROUND : AppState.BACKGROUND;
        }
      }, AppState.NOT_RUNNING);
    }
    catch (Throwable e) {
      return AppState.NOT_RUNNING;
    }
  }

  private static abstract class Communicator <T> {
    abstract T communicate(@NotNull DataInputStream input, @NotNull DataOutputStream output) throws IOException;
    int getTimeout() {
      return 2000;
    }
  }

  @NotNull
  private static <T> T talkToApp(@NotNull IDevice device,
                                 @Nullable AndroidFacet facet,
                                 @NotNull Communicator<T> communicator,
                                 @NotNull T errorValue) {
    if (facet == null) {
      return errorValue;
    }

    String packageName = getPackageName(facet);
    if (packageName == null) {
      return errorValue;
    }

    try {
      device.createForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      try {
        return talkToAppWithinPortForward(communicator, errorValue);
      }
      catch (UnknownHostException e) {
        LOG.warn(e);
      }
      catch (SocketException e) {
        if (e.getMessage().equals("Broken pipe")) {
          postBalloon(MessageType.ERROR, "No connection to app; cannot sync changes", facet.getModule().getProject());
          return errorValue;
        }
        LOG.warn(e);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      catch (Throwable e) {
        LOG.warn(e);
        return errorValue;
      }
      finally {
        device.removeForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      }
    }
    catch (TimeoutException e) {
      LOG.warn(e);
    }
    catch (AdbCommandRejectedException e) {
      LOG.warn(e);
    }
    catch (Throwable e) {
      LOG.warn(e);
    }

    return errorValue;
  }

  private static <T> T talkToAppWithinPortForward(@NotNull Communicator<T> communicator, @NotNull T errorValue) throws IOException {
    Socket socket = new Socket(LOCAL_HOST, STUDIO_PORT);
    try {
      socket.setSoTimeout(8*1000); // Allow up to 8 second before timing out
      DataOutputStream output = new DataOutputStream(socket.getOutputStream());
      try {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        try {
          output.writeLong(PROTOCOL_IDENTIFIER);
          output.writeInt(PROTOCOL_VERSION);

          int version = input.readInt();
          if (version != PROTOCOL_VERSION) {
            return errorValue;
          }

          socket.setSoTimeout(communicator.getTimeout());
          T value = communicator.communicate(input, output);

          output.writeInt(MESSAGE_EOF);

          return value;
        }
        finally {
          input.close();
        }
      } finally {
        output.close();
      }
    } finally {
      socket.close();
    }
  }

  private static long ourBeginTime;

  public static void notifyBegin() {
    ourBeginTime = System.currentTimeMillis();
  }

  private static void notifyEnd(Project project) {
    long end = System.currentTimeMillis();
    final String message = "Instant Run: " + (end - ourBeginTime) + "ms";
    postBalloon(MessageType.INFO, message, project);
  }
}
