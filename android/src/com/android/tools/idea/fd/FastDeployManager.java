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
import com.android.builder.model.SourceProvider;
import com.android.builder.model.Variant;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.AndroidRunningState;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.InstalledPatchCache;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * TODO: Handle edits in assets
 * TODO: Handle manifest edits
 * TODO: Display error message if not using correct Gradle model
 */
public class FastDeployManager implements ProjectComponent {
  // -----------------------------------------------------------------------
  // NOTE: Keep all these communication constants (and message send/receive
  // logic) in sync with the corresponding values in the fast deploy runtime
  // -----------------------------------------------------------------------

  /**
   * Magic (random) number used to identify the protocol
   */
  public static final long PROTOCOL_IDENTIFIER = 0x35107124L;

  /**
   * Version of the protocol
   */
  public static final int PROTOCOL_VERSION = 2;

  /**
   * Message: sending patches
   */
  public static final int MESSAGE_PATCHES = 1;

  /**
   * Message: ping, send ack back
   */
  public static final int MESSAGE_PING = 2;

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
  public static final boolean DISPLAY_STATISTICS = System.getProperty("fd.stats") != null ? Boolean.getBoolean("fd.stats") : true;

  /** Local port on the desktop machine that we tunnel to the Android device via */
  public static final int STUDIO_PORT = 8888;

  private static final String LOCAL_HOST = "127.0.0.1";

  private static String getDataFolder(@NotNull String applicationId) {
    // Location on the device where application data is stored. Currently using sdcard location
    // such that app can write to itself via socket traffic; we can switch to /data here if we use
    // adb to push data over -- but that means we have to push *all* the resources, we can't just push
    // deltas and have the app copy from previous version locally.
    //return "/storage/sdcard/studio-fd/" + pkg;

    // Keep in sync with FileManager#getDataFolder in the runtime library
    return "/data/data/" + applicationId + "/files/studio-fd";
  }

  private static final String RESOURCE_FILE_NAME = "resources.ap_";

  @NotNull private final Project myProject;

  public FastDeployManager(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static FastDeployManager get(@NotNull Project project) {
    return project.getComponent(FastDeployManager.class);
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
    FastDeployManager manager = get(module.getProject());
    return manager.ping(device, module);
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

    FastDeployManager manager = get(module.getProject());
    AndroidFacet facet = manager.findAppModule(module);
    if (facet == null) {
      return false;
    }

    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model == null) {
      return false;
    }

    String version = model.getAndroidProject().getModelVersion();
    try {
      // Sigh, would be nice to have integer versions to avoid having to do this here
      FullRevision revision = FullRevision.parseRevision(version);

      // Supported in version 1.6 of the Gradle plugin and up
      return revision.getMajor() > 1 || revision.getMinor() >= 6;
    } catch (NumberFormatException ignore) {
      return false;
    }
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
    if (DISPLAY_STATISTICS) {
      notifyBegin();
    }

    Project project = module.getProject();
    UpdateMode updateMode = forceRestart ? UpdateMode.COLD_SWAP : UpdateMode.HOT_SWAP;
    FastDeployManager manager = get(project);
    manager.performUpdate(device, updateMode, module);
  }

  public static boolean pushChanges(@NotNull final IDevice device, @NotNull final AndroidFacet facet) {
    FastDeployManager manager = get(facet.getModule().getProject());
    manager.pushChanges(device, UpdateMode.HOT_SWAP, facet, getLastInstalledArscTimestamp(device, facet));
    return true;
  }

  private static long getLastInstalledArscTimestamp(@NotNull IDevice device, @NotNull AndroidFacet facet) {
    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
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
  public static boolean isRebuildRequired(@NotNull IDevice device, @NotNull Module module) {
    FastDeployManager manager = get(module.getProject());
    AndroidFacet facet = manager.findAppModule(module);
    if (facet == null) {
      return false;
    }

    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
      return true;
    }

    InstalledPatchCache cache = ServiceManager.getService(InstalledPatchCache.class);

    long currentTimeStamp = getManifestLastModified(facet);
    long installedTimeStamp = cache.getInstalledManifestTimestamp(device, pkgName);

    if (currentTimeStamp <= installedTimeStamp) {
      return false;
    }

    // TODO: File has been edited: here we can actually *update* the merged manifest and then compute hash
    // codes to see if the contents are equivalent.

    return true;
  }

  /**
   * Returns the timestamp of the most recently modified manifest file applicable for the given facet
   */
  public static long getManifestLastModified(@NotNull AndroidFacet facet) {
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

  public void pushChanges(@NotNull IDevice device, @NotNull UpdateMode updateMode, @NotNull AndroidFacet facet, long deviceArscTimestamp) {
    AndroidGradleModel model = AndroidGradleModel.get(facet);
    if (model != null) {
      afterBuild(device, model, facet, updateMode, deviceArscTimestamp);
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
    String taskName = getIncrementalDexTask(model);
    invoker.executeTasks(Collections.singletonList(taskName));
  }

  // TODO: Get the intermediates folder from the model itself!
  @Nullable
  private static File findIntermediatesFolder(@NotNull AndroidGradleModel model) {
    Variant variant = model.getSelectedVariant();
    Collection<AndroidArtifactOutput> outputs = variant.getMainArtifact().getOutputs();
    for (AndroidArtifactOutput output : outputs) {
      File apk = output.getMainOutputFile().getOutputFile();
      File intermediates = new File(apk.getParentFile().getParentFile().getParentFile(), "intermediates");
      if (intermediates.exists()) {
        return intermediates;
      }
    }

    return null;
  }

  // TODO: Get the build folder from the model itself!
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

  // TODO: This should be provided as part of the model!
  @NotNull
  public static String getIncrementalDexTask(@NotNull AndroidGradleModel model) {
    final String variantName = getVariantName(model);

    // TODO: Add in task for resources too!
    return "incremental" + StringUtil.capitalize(variantName) + "SupportDex";
  }

  @Nullable
  private static File findReloadDex(final AndroidGradleModel model) {
    return findDexPatch(model, "reload-dex");
  }

  @Nullable
  private static File findStartDex(final AndroidGradleModel model) {
    return findDexPatch(model, "restart-dex");
  }

  @Nullable
  private static File findDexPatch(@NotNull AndroidGradleModel model, @NotNull String dexTypeFolder) {
    File intermediates = findIntermediatesFolder(model);
    if (intermediates != null) {
      final String variantName = getVariantName(model);
      File dexFile = new File(intermediates, dexTypeFolder + File.separator + variantName + File.separator + "classes.dex");
      if (dexFile.exists()) {
        return dexFile;
      }
    }

    return null;
  }

  private void afterBuild(@NotNull IDevice device,
                          @NotNull AndroidGradleModel model,
                          @NotNull AndroidFacet facet,
                          @NotNull UpdateMode updateMode,
                          long arscBefore) {
    List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(4);

    updateMode = gatherGradleCodeChanges(model, changes, updateMode);
    updateMode = gatherGradleResourceChanges(model, facet, changes, arscBefore, updateMode);

    push(device, facet.getModule(), changes, updateMode);

    String pkgName;
    try {
      pkgName = ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
      LOG.error(e);
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
                                                    @NotNull UpdateMode updateMode) {
    File restart = findStartDex(model);
    if (restart != null) {
      try {
        byte[] bytes = Files.toByteArray(restart);
        changes.add(new ApplicationPatch("classes.dex", bytes));

        File incremental = findReloadDex(model);
        if (incremental != null) {
          bytes = Files.toByteArray(incremental);
          changes.add(new ApplicationPatch("classes.dex.3", bytes));
          boolean deleted = incremental.delete();
          if (!deleted) {
            Logger.getInstance(FastDeployManager.class).error("Couldn't delete " + incremental);
          }
          return updateMode.combine(UpdateMode.HOT_SWAP);
        }
        return updateMode.combine(UpdateMode.COLD_SWAP);
      } catch (Throwable t) {
        Logger.getInstance(FastDeployManager.class).error("Couldn't generate dex", t);
      }
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
    File restart = findStartDex(model);
    if (restart != null) {
      boolean deleted = restart.delete();
      if (!deleted) {
        Logger.getInstance(FastDeployManager.class).error("Couldn't delete " + restart);
      }
    }
    File incremental = findReloadDex(model);
    if (incremental != null) {
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

  void postBalloon(@NotNull MessageType type, @NotNull String message) {
    postBalloon(type, message, myProject);
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

  private void push(@Nullable IDevice device,
                    @Nullable Module module,
                    @NotNull List<ApplicationPatch> changes,
                    @NotNull UpdateMode updateMode) {
    if (changes.isEmpty() || updateMode == UpdateMode.NO_CHANGES) {
      if (DISPLAY_STATISTICS) {
        postBalloon(MessageType.INFO, "Instant Run: No Changes");
      }
      return;
    }

    if (updateMode == UpdateMode.HOT_SWAP && isRestartActivity(myProject)) {
      updateMode = updateMode.combine(UpdateMode.WARM_SWAP);
    }

    if (device != null) {
      writeChanges(module, device, changes, updateMode);
    }
    if (DISPLAY_STATISTICS) {
      notifyEnd(myProject);
    }
  }

  public void writeChanges(@Nullable Module module, @NotNull IDevice device, @Nullable List<ApplicationPatch> changes,
                           @NotNull UpdateMode updateMode) {
    String packageName = getPackageName(module);
    try {
      device.createForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      try {
        writeChanges(changes, updateMode);
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
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Nullable
  String getPackageName(@Nullable Module module) {
    AndroidFacet facet = findAppModule(module);
    if (facet != null) {
      AndroidModuleInfo info = AndroidModuleInfo.get(facet);
      if (info.getPackage() != null) {
        return info.getPackage();
      }
    }

    return null;
  }

  @Nullable
  AndroidFacet findAppModule(@Nullable Module module) {
    if (module != null) {
      assert module.getProject() == myProject;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }

    // TODO: Here we should really look for app modules that *depend*
    // on the given module (if non null), not just use the first app
    // module we find.

    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(m);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }
    return null;
  }

  public void writeChanges(@Nullable List<ApplicationPatch> changes, @NotNull UpdateMode updateMode) {
    try {
      Socket socket = new Socket(LOCAL_HOST, STUDIO_PORT);
      try {
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        try {
          output.writeLong(PROTOCOL_IDENTIFIER);
          output.writeInt(PROTOCOL_VERSION);
          output.writeInt(MESSAGE_PATCHES);
          ApplicationPatch.write(output, changes, updateMode);

          // Finally read a boolean back from the other side; this has the net effect of
          // waiting until applying/verifying code on the other side is done. (It doesn't
          // count the actual restart time, but for activity restarts it's typically instant,
          // and for cold starts we have no easy way to handle it (the process will die and a
          // new process come up; to measure that we'll need to work a lot harder.)
          DataInputStream input = new DataInputStream(socket.getInputStream());
          try {
            input.readBoolean();
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
    catch (UnknownHostException e) {
      LOG.warn(e);
    }
    catch (SocketException e) {
      if (e.getMessage().equals("Broken pipe")) {
        postBalloon(MessageType.ERROR, "No connection to app; cannot sync resource changes");
        return;
      }
      LOG.warn(e);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  /**
   * Attempts to connect to a given device and sees if an instant run enabled app is running there.
   */
  public boolean ping(@NotNull IDevice device, @NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return false;
    }

    String packageName;
    try {
      packageName = ApkProviderUtil.computePackageName(facet);
    }
    catch (ApkProvisionException e) {
      LOG.warn("Unable to identify package name for app in module: " + module.getName());
      return false;
    }

    try {
      device.createForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);

      try {
        Socket socket = new Socket(LOCAL_HOST, STUDIO_PORT);
        try {
          DataOutputStream output = new DataOutputStream(socket.getOutputStream());
          try {
            output.writeLong(PROTOCOL_IDENTIFIER);
            output.writeInt(PROTOCOL_VERSION);
            output.writeInt(MESSAGE_PING);
            // Wait for "pong"
            DataInputStream input = new DataInputStream(socket.getInputStream());
            try {
              input.readBoolean();
            }
            finally {
              input.close();
            }
            LOG.info("Ping sent and replied successfully, application seems to be running..");
            return true;
          } finally {
            output.close();
          }
        } finally {
          socket.close();
        }
      }
      catch (Throwable e) {
        return false;
      }
      finally {
        device.removeForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      }
    }
    catch (Throwable e) {
      return false;
    }
  }

  /**
   * Wipe any previously stashed application data for the given app; when we install a new version
   * of the app here we assume it contains all the new necessary data
   */
  public static void wipeData(@NotNull AndroidRunningState state,
                              @NotNull IDevice device,
                              @NotNull String remotePath,
                              @NotNull AndroidOutputReceiver receiver)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    // Clear any locally cached data on the device related to this app
    String pkg = remotePath.substring(remotePath.lastIndexOf('/') + 1);
    state.executeDeviceCommandAndWriteToConsole(device, "rm -rf " + getDataFolder(pkg), receiver);
  }

  private static long ourBeginTime;

  public static void notifyBegin() {
    ourBeginTime = System.currentTimeMillis();
  }

  public static void notifyEnd(Project project) {
    long end = System.currentTimeMillis();
    final String message = "Instant Run: " + (end - ourBeginTime) + "ms";
    postBalloon(MessageType.INFO, message, project);
  }
}
