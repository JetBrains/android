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

import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.Variant;
import com.android.ddmlib.*;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.run.AndroidRunningState;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidOutputReceiver;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import static com.android.SdkConstants.*;
import static java.io.File.separator;

/**
 * TODO: Migrate aapt to gradle
 * TODO: Handle edits in assets
 * TODO: Handle manifest edits
 * TODO: Display error message if not using correct Gradle model
 */
public class FastDeployManager implements ProjectComponent, BulkFileListener {
  private static final Logger LOG = Logger.getInstance(FastDeployManager.class);

  /** Display instant run statistics */
  @SuppressWarnings("SimplifiableConditionalExpression")
  public static final boolean DISPLAY_STATISTICS = System.getProperty("fd.stats") != null ? Boolean.getBoolean("fd.stats") : true;

  /** Build code changes with Gradle rather than IDE hooks? */
  @SuppressWarnings("SimplifiableConditionalExpression")
  public static boolean REBUILD_CODE_WITH_GRADLE = System.getProperty("fd.code") != null ? Boolean.getBoolean("fd.code") : true;

  /** Build resources changes with Gradle rather than IDE hooks? */
  @SuppressWarnings("SimplifiableConditionalExpression")
  public static final boolean REBUILD_RESOURCES_WITH_GRADLE = System.getProperty("fd.resources") != null ? Boolean.getBoolean("fd.resources") : false;

  /** Local port on the desktop machine that we tunnel to the Android device via */
  public static final int STUDIO_PORT = 8888;

  private static String getDataFolder(@NotNull String applicationId) {
    // Location on the device where application data is stored. Currently using sdcard location
    // such that app can write to itself via socket traffic; we can switch to /data here if we use
    // adb to push data over -- but that means we have to push *all* the resources, we can't just push
    // deltas and have the app copy from previous version locally.
    //return "/storage/sdcard/studio-fd/" + pkg;

    // Keep in sync with FileManager#getDataFolder in the runtime library
    return "/data/data/" + applicationId + "/files/studio-fd";
  }

  private static final boolean SEND_WHOLE_AP_FILE = true;
  private static final String RESOURCE_FILE_NAME = "resources.ap_";

  @NotNull private final Project myProject;
  @Nullable private MessageBusConnection myConnection;

  public FastDeployManager(@NotNull Project project) {
    myProject = project;
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

  public boolean isActive() {
    return myConnection != null;
  }

  public void setActive(boolean activate) {
    if (activate == isActive()) {
      return;
    }
    if (activate) {
      enable();
    }
    else {
      disable();
    }
  }

  public void enable() {
    if (myConnection == null) {
      myConnection = ApplicationManager.getApplication().getMessageBus().connect();
      myConnection.subscribe(VirtualFileManager.VFS_CHANGES, this);
    }
  }

  public void disable() {
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  public static boolean isInstantRunEnabled(@NotNull Project project) {
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    return buildConfiguration.INSTANT_RUN;
  }

  // ---- Implements BulkFileListener ----

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    Set<VirtualFile> files = null;
    Map<AndroidFacet, Collection<VirtualFile>> map = null;
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file == null) {
        continue;
      }

      if (file.getParent().getName().equals(Project.DIRECTORY_STORE_FOLDER)) {
        // Ignore common file events -- such as workspace.xml on Window focus loss etc
        continue;
      }

      if (myProject.isDisposed()) {
        continue;
      }

      ApplicationManager.getApplication().assertReadAccessAllowed();
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      if (psiFile == null) {
        continue;
      }
      AndroidFacet facet = AndroidFacet.getInstance(psiFile);
      if (facet == null) {
        continue;
      }
      boolean isJava = file.getFileType() == StdFileTypes.JAVA;
      if (!isJava && !AndroidResourceUtil.isResourceFile(file, facet)) {
        continue;
      }

      if (map == null) {
        map = new HashMap<AndroidFacet, Collection<VirtualFile>>();
      }
      Collection<VirtualFile> list = map.get(facet);
      if (list == null) {
        list = new ArrayList<VirtualFile>();
        map.put(facet, list);
      }
      list.add(file);

      if (files == null) {
        files = new HashSet<VirtualFile>();
      }
      files.add(file);
    }
    if (map != null) {
      computeDeltas(map, false);
    }
  }

  public void computeDeltas(AndroidFacet facet, VirtualFile file, boolean forceRestart) {
    Map<AndroidFacet, Collection<VirtualFile>> map = Maps.newHashMap();
    Collection<VirtualFile> virtualFiles = Collections.singletonList(file);
    map.put(facet, virtualFiles);
    computeDeltas(map, forceRestart);
  }

  public void computeDeltas(Map<AndroidFacet, Collection<VirtualFile>> map, boolean forceRestart) {
    process(forceRestart, map);
  }

  public void process(boolean forceRestart, Map<AndroidFacet, Collection<VirtualFile>> changedFiles) {
    for (Map.Entry<AndroidFacet, Collection<VirtualFile>> entry : changedFiles.entrySet()) {
      AndroidFacet facet = entry.getKey();
      Collection<VirtualFile> files = entry.getValue();
      AndroidGradleModel model = AndroidGradleModel.get(facet);
      if (model == null) {
        continue;
      }
      process(facet, model, files, forceRestart);
    }
  }

  public void process(AndroidFacet facet, AndroidGradleModel model, Collection<VirtualFile> files, boolean forceRestart) {
    if (REBUILD_CODE_WITH_GRADLE || REBUILD_RESOURCES_WITH_GRADLE) {
      runGradle(AndroidGradleModel.get(facet), facet, forceRestart, files);
    } else {
      afterBuild(model, facet, forceRestart, files);
    }
  }

  private void runGradle(final AndroidGradleModel model, final AndroidFacet facet,
                                        final boolean forceRestart,
                                        final Collection<VirtualFile> files) {
    assert REBUILD_CODE_WITH_GRADLE;


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
        afterBuild(model, facet, forceRestart, files);
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

  @Nullable
  private static File findMergedResFolder(@NotNull AndroidFacet facet) {
    File intermediates = findIntermediatesFolder(facet);
    if (intermediates != null) {
      String variantName = getVariantName(facet);
      File res = new File(intermediates, "res" + separator + "merged" + separator + variantName);
      if (res.isDirectory()) {
        return res;
      }

      // Older Gradle plugin had it here: Do we still need to look for it?
      // Look for resources in known Android plugin intermediate dirs; this should be handled by Gradle instead:
      // res/debug/values/values.xml
      //res = new File(intermediates, "res" + separator + variantName);
      //if (res.isDirectory()) {
      //  return res;
      //}
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

  // TODO: Get the merged resource folder from the model itself!
  @Nullable
  private static File findMergedResourceFolder(@NotNull AndroidFacet facet) {
    File intermediates = findIntermediatesFolder(facet);
    if (intermediates != null) {
      String variantName = getVariantName(facet);
      File resourceDir = new File(intermediates, "res" + File.separator + "merged" + File.separator + variantName);
      if (resourceDir.exists()) {
        return resourceDir;
      }
    }
    return null;
  }

  // TODO: Get the generated folder from the model itself!
  @Nullable
  private static File findGeneratedFolder(@NotNull AndroidFacet facet) {
    File build = findBuildFolder(facet);
    if (build != null) {
      File generated = new File(build, "generated");
      if (generated.exists()) {
        return generated;
      }
    }

    return null;
  }

  @Nullable
  private static File findMergedManifestFile(@NotNull AndroidFacet facet) {
    // TODO: This might already be in the model
    File intermediates = findIntermediatesFolder(facet);
    if (intermediates != null) {
      String variantName = getVariantName(facet);
      File manifest = new File(intermediates, "manifests" + File.separator + "full" + File.separator + variantName
                                              + File.separator + ANDROID_MANIFEST_XML);
      if (manifest.exists()) {
        return manifest;
      }
    }

    return null;
  }

  // TODO: Get the assets folder from the model itself!
  @Nullable
  private static File findAssetsFolder(@NotNull AndroidFacet facet) {
    File intermediates = findIntermediatesFolder(facet);
    if (intermediates != null) {
      String variantName = getVariantName(facet);
      File assets = new File(intermediates, "assets" + File.separator + variantName);
      if (assets.exists()) {
        return assets;
      }
    }

    return null;
  }

  // TODO: Get the R class folder from the model itself!
  @Nullable
  private static File findResourceClassFolder(@NotNull AndroidFacet facet) {
    File generated = findGeneratedFolder(facet);
    if (generated != null) {
      String variantName = getVariantName(facet);
      File resourceClassFolder = new File(generated, "source" + File.separator + "r" + File.separator + variantName);
      if (resourceClassFolder.exists()) {
        return resourceClassFolder;
      }
    }

    return null;
  }

  // TODO: This should be provided as part of the model!
  @NotNull
  private static String getIncrementalDexTask(@NotNull AndroidGradleModel model) {
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

  private void afterBuild(AndroidGradleModel model, AndroidFacet facet, boolean forceRestart,
                          Collection<VirtualFile> files) {
    List<ApplicationPatch> changes = new ArrayList<ApplicationPatch>(4);

    if (REBUILD_CODE_WITH_GRADLE) {
      gatherGradleCodeChanges(model, changes);
    } else {
      computeCodeChangesDirectly(model, changes, facet, files);
    }
    if (REBUILD_RESOURCES_WITH_GRADLE) {
      gatherGradleResourceChanges(model, facet, changes);
    } else {
      computeResourceChangesDirectly(facet, files, changes);
    }

    push(facet.getModule().getProject(), changes, forceRestart);
  }

  @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"}) // won't be as soon as it really calls Gradle
  private void gatherGradleResourceChanges(AndroidGradleModel model, AndroidFacet facet,
                                           List<ApplicationPatch> changes) {
    assert REBUILD_RESOURCES_WITH_GRADLE;
    throw new UnsupportedOperationException("Not yet implemented");
  }

  private void computeResourceChangesDirectly(AndroidFacet facet, Collection<VirtualFile> files, List<ApplicationPatch> changes) {
    File res = findMergedResFolder(facet);
    if (res != null) {
      List<VirtualFile> resourceFiles = Lists.newArrayList();
      List<VirtualFile> sourceFiles = Lists.newArrayList();

      for (VirtualFile file : files) {
        if (file.getFileType() == StdFileTypes.JAVA) {
          sourceFiles.add(file);
        } else {
          ResourceFolderType folderType = ResourceHelper.getFolderType(file);
          if (folderType != null || file.getName().equals(ANDROID_MANIFEST_XML)) {
            resourceFiles.add(file);
          }
        }
      }

      if (!resourceFiles.isEmpty()) {
        try {
          // Attempt to just push the whole arsc envelope over
          File intermediates = findIntermediatesFolder(facet);
          if (intermediates == null) {
            LOG.warn("Couldn't find intermediates folder in the project for module " + facet.getModule());
            return;
          }

          File generated = findGeneratedFolder(facet);
          if (generated == null) {
            LOG.warn("Couldn't find generated source folder in the project for module " + facet.getModule());
            return;
          }

          File resourceDir = findMergedResourceFolder(facet);
          if (resourceDir == null) {
            LOG.warn("Couldn't find merged resource folder in the project for module " + facet.getModule());
            return;
          }

          ListIterator<VirtualFile> iterator = resourceFiles.listIterator();
          while (iterator.hasNext()) {
            VirtualFile file = iterator.next();
            ResourceFolderType folderType = ResourceHelper.getFolderType(file);
            File oldFile;
            if (folderType == ResourceFolderType.VALUES) {
              oldFile = new File(res, file.getParent().getName() + separator + file.getParent().getName() + DOT_XML);
            }
            else if (folderType != null) {
              // File resources
              oldFile = new File(res, file.getParent().getName() + separator + file.getName());
              if (!oldFile.exists() || !isDifferent(facet.getModule().getProject(), oldFile, file)) {
                iterator.remove();
                continue;
              }

            }
            else {
              // Manifest file
              // TODO: Handle manifest files! I'll need to run the manifest merger again here!
              // This is probably not worth doing until we merge this functionality into the
              // Gradle plugin
              LOG.warn("Skipping manifest files for now");
              iterator.remove();
              continue;
            }

            boolean ok = updateMergedResourceFile(resourceDir, oldFile, file, folderType);
            if (!ok) {
              LOG.warn("Failed to merge updates to file " + file);
            }
          }

          if (resourceFiles.isEmpty()) {
            // No files changed: nothing to do.
            return;
          }

          // Next compile all the resources with a single aapt run
          File binaryXml = compileResources(facet, resourceDir);
          if (binaryXml != null) {
            // Extract all the files
            for (VirtualFile file : resourceFiles) {
              ResourceFolderType folderType = ResourceHelper.getFolderType(file);
              if (folderType == null) {
                // TODO: Handle manifest files!
                continue;
              }
              byte[] bytes = extractFile(binaryXml, file, folderType);
              if (bytes != null) {
                if (folderType == ResourceFolderType.VALUES ) {
                  changes.add(new ApplicationPatch(RESOURCE_FILE_NAME, bytes));
                }
                else {
                  // TODO: If it's a PNG file, perform aapt crunching and nine patch processing:
                  //
                  //     aapt c[runch] [-v] -S resource-sources ... -C output-folder ...
                  //      Do PNG preprocessing on one or several resource folders
                  //      and store the results in the output folder.
                  //
                  //    aapt s[ingleCrunch] [-v] -i input-file -o outputfile
                  //      Do PNG preprocessing on a single file.

                  // TODO: Consider sending across the whole .ap_

                  @SuppressWarnings("ConstantConditions") String path =
                    // NOTE: *Not* using File.separator in Android file paths
                    SEND_WHOLE_AP_FILE ? RESOURCE_FILE_NAME : "res/" + file.getParent().getName() + "/" + file.getName();
                  changes.add(new ApplicationPatch(path, bytes));
                }
              }
            }

            FileUtil.delete(binaryXml);
          }

        } catch (IOException ioe) {
          LOG.warn(ioe);
        }
      }
    }
  }

  private static void gatherGradleCodeChanges(AndroidGradleModel model, List<ApplicationPatch> changes) {
    assert REBUILD_CODE_WITH_GRADLE;

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
        }
      } catch (Throwable t) {
        Logger.getInstance(FastDeployManager.class).error("Couldn't generate dex", t);
      }
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

  @SuppressWarnings("UnusedParameters")
  private boolean computeCodeChangesDirectly(AndroidGradleModel model, List<ApplicationPatch> changes, AndroidFacet facet,
                                             Collection<VirtualFile> files) {
    assert !REBUILD_CODE_WITH_GRADLE;

    // Could be a mixture of resources and java files: extract the JAva files
    List<VirtualFile> sources = Lists.newArrayListWithExpectedSize(files.size());
    for (VirtualFile file : files) {
      if (file.getFileType() == StdFileTypes.JAVA) {
        sources.add(file);
      }
    }
    if (sources.isEmpty()) {
      return false;
    }

    // Recompile & redex just these files. I really just want to call
    //    ./gradlew :app:preDexDebug :app:dexDebug
    // here and have that do the bare minimum (e.g. compile just the changed java files
    // and just dex the changed files) but this currently seems to depend on many other things,
    // e.g. it runs mergeDebugResources (because it needs the R class?) etc.
    // So perform optimized stage here
    //GradleInvoker.getInstance(module.getProject()).compileJava(new Module[] { module }, GradleInvoker.TestCompileType.NONE);
    try {
      compileJavaFiles(changes, facet, sources);
    }
    catch (IOException e) {
      Logger.getInstance(FastDeployManager.class).error("Couldn't compile/dex Java", e);
    }
    catch (InterruptedException e) {
      Logger.getInstance(FastDeployManager.class).error("Couldn't compile/dex Java", e);
    }

    return false;
  }

  private void compileJavaFiles(List<ApplicationPatch> changes, AndroidFacet facet, List<VirtualFile> sources)
      throws IOException, InterruptedException {
    List<String> args = Lists.newArrayList();
    File jdkPath = IdeSdks.getJdkPath();
    if (jdkPath == null) {
      throw new IOException("No jdk");
    }
    File javac = new File(jdkPath, "bin" + File.separator + "javac"); // TODO: Windows
    if (!(javac.exists())) {
      throw new IOException("No javac found in " + jdkPath);
    }
    args.add(javac.getPath());
    // Compute class path
    StringBuilder sb = new StringBuilder(1000);

    for (OrderEntry orderEntry : ModuleRootManager.getInstance(facet.getModule()).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrSdkOrderEntry) {
        VirtualFile[] rootFiles = ((LibraryOrSdkOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
        for (VirtualFile rootFile : rootFiles) {
          final File root = VfsUtilCore.virtualToIoFile(rootFile);
          if (root.exists()) {
            if (sb.length() != 0) {
              sb.append(File.pathSeparatorChar);
            }
            sb.append(root.getPath());
          }
        }
      } else if (orderEntry instanceof ModuleSourceOrderEntry) {
        final VirtualFile[] rootFiles = orderEntry.getFiles(OrderRootType.SOURCES);
        for (VirtualFile rootFile : rootFiles) {
          final File root = VfsUtilCore.virtualToIoFile(rootFile);
          if (root.exists()) {
            if (sb.length() != 0) {
              sb.append(File.pathSeparatorChar);
            }
            sb.append(root.getPath());
          }
        }
      } else if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;
        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          //final VirtualFile[] rootFiles = orderEntry.getFiles(OrderRootType.CLASSES);
          // TODO: This includes sources in libraries too. Redundant. I should instead point to the -d folder
          final VirtualFile[] rootFiles = orderEntry.getFiles(OrderRootType.SOURCES);
          for (VirtualFile rootFile : rootFiles) {
            final File root = VfsUtilCore.virtualToIoFile(rootFile);
            if (root.exists()) {
              if (sb.length() != 0) {
                sb.append(File.pathSeparatorChar);
              }
              sb.append(root.getPath());
            }
          }
        }
      }
    }

    args.add("-source");
    args.add("1.7"); // TODO: Fetch from project
    args.add("-target");
    args.add("1.7");
    args.add("-classpath");
    args.add(sb.toString());

    // Add in the source files
    for (VirtualFile source : sources) {
      File file = VfsUtilCore.virtualToIoFile(source);
      args.add(file.getPath());
    }

    // Output directory
    args.add("-d");
    File classFolder = Files.createTempDir();
    // TODO: Set the output directory
    args.add(classFolder.getPath());
    // Actually I don't need to point to the sources - just point to the current output directory too, right?
    // Add the files to compile

    String[] argv = ArrayUtil.toStringArray(args);
    final Process process = Runtime.getRuntime().exec(argv);
    int code = process.waitFor();
    if (code == 0) {
      compileDexFiles(changes, facet, classFolder);
    }
    else {
      dumpProcessOutput(process, "javac");
      postBalloon(MessageType.WARNING, "There were javac compilation errors; could not send code diffs to app");
    }
  }

  private static void dumpProcessOutput(Process process, String processName) {
    try {
      InputStream stdoutStream = process.getInputStream();
      InputStream stderrStream = process.getErrorStream();
      String stdout = new String(ByteStreams.toByteArray(stdoutStream), Charsets.UTF_8);
      String stderr = new String(ByteStreams.toByteArray(stderrStream), Charsets.UTF_8);
      if (!stdout.isEmpty()) {
        LOG.warn(processName + " stdout:\n" + stdout + "\n");
      }
      if (!stderr.isEmpty()) {
        LOG.warn(processName + " stderr:\n" + stderr + "\n");
      }
    } catch (IOException ignore) {
    }
  }

  private void compileDexFiles(List<ApplicationPatch> changes, AndroidFacet facet, File classFolder)
      throws IOException, InterruptedException {
    AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
    if (platform == null) {
      return;
    }
    AndroidSdkData sdkData = facet.getSdkData();
    if (sdkData == null) {
      return;
    }
    LocalSdk localSdk = sdkData.getLocalSdk();

    // TODO: Use version used by the project instead?
    BuildToolInfo latestBuildTool = localSdk.getLatestBuildTool();
    if (latestBuildTool == null) {
      return;
    }
    File dex = new File(latestBuildTool.getPath(BuildToolInfo.PathId.DX));
    if (!dex.exists()) {
      throw new IOException("Dex not found: " + dex);
    }

    List<String> args = Lists.newArrayList();
    args.add(dex.getPath());

    args.add("--dex");
    args.add("--output");

    File output = FileUtil.createTempFile("classes", ".dex");
    args.add(output.getPath());
    args.add(classFolder.getPath());

    // Actually I don't need to point to the sources - just point to the current output directory too, right?
    // Add the files to compile

    // TODO: Set the output directory


    String[] argv = ArrayUtil.toStringArray(args);
    final Process process = Runtime.getRuntime().exec(argv);
    int code = process.waitFor();
    if (code == 0) {
      byte[] bytes = Files.toByteArray(output);
      changes.add(new ApplicationPatch("classes.dex", bytes));
      postBalloon(MessageType.INFO, "Pushed code changes to app");

    }
    else {
      dumpProcessOutput(process, "dex");
      postBalloon(MessageType.WARNING, "There were dex errors; could not send code diffs to app");
    }
  }

  private static boolean updateMergedResourceFile(@NotNull File resourceDir, @NotNull File oldFile, @NotNull VirtualFile file,
                                                  @NotNull ResourceFolderType folderType) throws IOException {
    String parentName = file.getParent().getName();
    if (folderType == ResourceFolderType.VALUES) {
      // Nope - turns out it's now using the parent name + xml - e.g.
      //   we have values/values.xml and values-21/values-21.xml, not values-21/values.xml !
      File targetFile = new File(resourceDir, parentName + separator + parentName + DOT_XML);
      // This isn't quite right: we might be rewriting resources into values that were overridden by higher priority overlays!

      Document allDoc = XmlUtils.parseDocumentSilently(Files.toString(targetFile, Charsets.UTF_8), true);
      Document newDoc = XmlUtils.parseDocumentSilently(new String(file.contentsToByteArray(), Charsets.UTF_8), true);
      if (newDoc == null || allDoc == null) {
        return false;
      }
      Element allRoot = allDoc.getDocumentElement();
      Element documentElement = newDoc.getDocumentElement();
      if (documentElement == null || allRoot == null) {
        return false;
      }
      Map<String, Element> insertMap = Maps.newHashMap();
      for (Element element : LintUtils.getChildren(documentElement)) {
        // Can't just stash elements by name: the same file can contain multiple identical names separated
        // by different types (e.g. in the I/O app we have both <string name="social_extended"> and <color name="social_extended">)
        String key = getKey(element);
        if (key != null) {
          insertMap.put(key, element);
        }
      }
      for (Element element : LintUtils.getChildren(allRoot)) {
        String key = getKey(element);
        if (key != null) {
          Element replacement = insertMap.get(key);
          if (replacement != null) {
            Node imported = duplicateNode(allDoc, replacement);
            allRoot.replaceChild(imported, element);
            insertMap.remove(key);
          }
        }
      }

      for (Element remaining : insertMap.values()) {
        Node imported = duplicateNode(allDoc, remaining);
        allRoot.appendChild(imported);
      }

      String xml = XmlUtils.toXml(allDoc);
      Files.write(xml, targetFile, Charsets.UTF_8);

      // I don't actually need to copy if I'm going to do it this way!
      Files.write(xml, oldFile, Charsets.UTF_8);
    }
    else {
      // Non-value resources: much simpler, just replace the file
      byte[] bytes = file.contentsToByteArray();
      FileUtil.writeToFile(oldFile, bytes);
    }

    return true;
  }

  private static boolean isDifferent(Project project, File oldFile, VirtualFile file) {
    if (oldFile.length() != file.getLength()) {
      return true;
    }

    long oldStamp = oldFile.lastModified();
    File newFile = VfsUtilCore.virtualToIoFile(file);
    if (newFile.lastModified() > oldStamp) {
      return true;
    }


    if (oldFile.getName().equals(file.getName())) {
      return true;
    }

    if (SdkUtils.endsWithIgnoreCase(oldFile.getPath(), DOT_XML)) {
      try {
        String oldContents = Files.toString(oldFile, Charsets.UTF_8);
        String newContents = TemplateUtils.readTextFile(project, file);
        return oldContents.equals(newContents);
      } catch (IOException ignore) {
      }
    }

    return false;
  }

  /**
   * Create an aapt binary packaged version of the given file.
   * <p/>
   * We should port this to Java, but for now perform a (very inefficient) aapt packaging task instead
   */
  @Nullable
  private File compileResources(@NotNull AndroidFacet facet, @NotNull File resourceDir) throws IOException {
    AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
    if (platform == null) {
      return null;
    }
    AndroidSdkData sdkData = facet.getSdkData();
    if (sdkData == null) {
      return null;
    }
    LocalSdk localSdk = sdkData.getLocalSdk();

    File binaryXml = FileUtil.createTempFile("binaryXml", ".ap_");

    // Try to repackage the XML files
    try {
      // TODO: Use version used by the project instead?
      BuildToolInfo latestBuildTool = localSdk.getLatestBuildTool();
      if (latestBuildTool == null) {
        return null;
      }
      File aapt = new File(latestBuildTool.getPath(BuildToolInfo.PathId.AAPT));

      List<String> args = Lists.newArrayList();
      args.add(aapt.getPath());
      args.add("package");
      args.add("-f");
      args.add("--no-crunch");
      args.add("-I");

      // Find android.jar
      File androidJar = platform.getTarget().getFile(IAndroidTarget.ANDROID_JAR);
      args.add(androidJar.getPath());

      File manifest = findMergedManifestFile(facet);
      if (manifest != null) {
        args.add("-M");
        args.add(manifest.getPath());

      }
      args.add("-S");
      args.add(resourceDir.getPath());

      File assetsFolder = findAssetsFolder(facet);
      if (assetsFolder != null) {
        // TODO: Copy this asset folder to a different directory and override with current versions!
        args.add("-A");
        args.add(assetsFolder.getPath()); // TODO: We probably don't need to include this if we're just packaging resources?
      }

      args.add("-m");

      File resourceClassFolder = findResourceClassFolder(facet);
      if (resourceClassFolder != null) {
        args.add("-J");
        args.add(resourceClassFolder.getPath());
      }
      args.add("-F");
      args.add(binaryXml.getPath());
      args.add("--debug-mode");
      args.add("--custom-package");
      String pkg = AndroidModuleInfo.get(facet).getPackage();
      if (pkg == null) {
        return null;
      }
      args.add(pkg);
      args.add("-0");
      args.add("apk");

      String[] argv = ArrayUtil.toStringArray(args);
      final Process process = Runtime.getRuntime().exec(argv);
      int code = process.waitFor();
      if (code == 0) {
        return binaryXml;
      }
      else {
        dumpProcessOutput(process, "Incremental aapt");
        postBalloon(MessageType.WARNING, "There were errors computing resources; could not send resource diffs to app");
      }
    }
    catch (InterruptedException e) {
      LOG.warn(e);
    }

    FileUtil.delete(binaryXml);
    return null;
  }

  /**
   * Create an aapt binary packaged version of the given file.
   * <p/>
   * We should port this to Java, but for now perform a (very inefficient) aapt packaging task instead
   */
  @Nullable
  private static byte[] extractFile(@NotNull File binaryXml, @NotNull VirtualFile file, @NotNull ResourceFolderType folderType)
      throws IOException {
    if (folderType == ResourceFolderType.VALUES || SEND_WHOLE_AP_FILE) {
      return Files.toByteArray(binaryXml);
    }
    else {
      // Look in the generated .ap_ file for the target resource
      InputStream inputStream = new FileInputStream(binaryXml);
      JarInputStream jarInputStream = new JarInputStream(inputStream);
      try {
        // Always using / rather than File.separator in ZipEntry paths
        String parentName = file.getParent().getName();
        String target = FD_RES + '/' + parentName + '/' + file.getName();

        ZipEntry entry = jarInputStream.getNextEntry();
        while (entry != null) {
          String name = entry.getName();
          if (name.equals(target)) {
            return ByteStreams.toByteArray(jarInputStream);
          }
          entry = jarInputStream.getNextEntry();
        }
      }
      finally {
        jarInputStream.close();
      }

      return null;
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

  private void postBalloon(MessageType type, String message) {
    JFrame frame = WindowManager.getInstance().getFrame(myProject.isDefault() ? null : myProject);
    if (frame != null) {
      JComponent component = frame.getRootPane();
      if (component != null) {
        Rectangle rect = component.getVisibleRect();
        Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
        RelativePoint point = new RelativePoint(component, p);
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
          message,
          type.getDefaultIcon(),
          type.getPopupBackground(), null).setShowCallout(false).setCloseButtonEnabled(true).createBalloon().show(point, Balloon.Position.atLeft);
      }
    }
  }

  // Based on NodeUtils#duplicateNode, but that code is package private and not complete (it doesn
  // duplicate non-element nodes etc). Seems to be optimized for resource manager. Here we need a more
  // complete and accurate way to duplicate an element into another document, adjusting namespaces if necessary.
  public static Node duplicateNode(Document document, Node node) {
    Node newNode;
    if (node.getNamespaceURI() != null) {
      newNode = document.createElementNS(node.getNamespaceURI(), node.getLocalName());
      String prefix = document.lookupPrefix(node.getNamespaceURI());
      if (prefix != null) {
        newNode.setPrefix(prefix);
      }
    } else {
      newNode = document.createElement(node.getNodeName());
    }

    // copy the attributes
    NamedNodeMap attributes = node.getAttributes();
    for (int i = 0 ; i < attributes.getLength(); i++) {
      Attr attr = (Attr) attributes.item(i);

      Attr newAttr;
      if (attr.getNamespaceURI() != null) {
        newAttr = document.createAttributeNS(attr.getNamespaceURI(), attr.getLocalName());
        newNode.getAttributes().setNamedItemNS(newAttr);
      } else {
        newAttr = document.createAttribute(attr.getName());
        newNode.getAttributes().setNamedItem(newAttr);
      }

      newAttr.setValue(attr.getValue());
    }

    // then duplicate the sub-nodes.
    NodeList children = node.getChildNodes();
    for (int i = 0 ; i < children.getLength() ; i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        Node duplicatedChild = document.importNode(child, true);
        newNode.appendChild(duplicatedChild);
        continue;
      }
      Node duplicatedChild = duplicateNode(document, child);
      newNode.appendChild(duplicatedChild);
    }

    return newNode;
  }

  @Nullable
  private static String getKey(@NotNull Element element) {
    String name = element.getAttribute(ATTR_NAME);
    if (name != null && !name.isEmpty()) {
      String type = element.getTagName();
      if (type.equals(TAG_ITEM)) {
        type = element.getAttribute(ATTR_TYPE);
      }
      return name + type;
    }
    return null;
  }

  private void push(@NotNull Project project, @NotNull List<ApplicationPatch> changes, boolean forceRestart) {
    if (changes.isEmpty()) {
      return;
    }

    File adb = AndroidSdkUtils.getAdb(project);
    if (adb != null) {
      try {
        AndroidDebugBridge bridge = AdbService.getInstance().getDebugBridge(adb).get();
        IDevice[] devices = bridge.getDevices();
        for (IDevice device : devices) {
          // TODO: Look to see if this device has this app running:
          //device.getClient(applicationName) != null;
          writeMessage(project, device, changes, forceRestart);
        }
      } catch (InterruptedException ignore) {
      }
      catch (ExecutionException ignore) {
      }
      if (DISPLAY_STATISTICS) {
        notifyEnd(project);
      }
    }
  }

  public void writeMessage(@NotNull Project project, @NotNull IDevice device, @Nullable List<ApplicationPatch> changes,
                           boolean forceRestart) {
    String packageName = getPackageName(project);
    try {
      device.createForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
      write(project, changes, forceRestart);
      device.removeForward(STUDIO_PORT, packageName, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
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

  private static String getPackageName(@NotNull Project project) {
    for (Module m : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(m);
      if (facet == null) {
        continue;
      }

      AndroidModuleInfo info = AndroidModuleInfo.create(facet);
      if (info.getPackage() != null) {
        return info.getPackage();
      }
    }

    return null;
  }

  public void write(@NotNull Project project, @Nullable List<ApplicationPatch> changes, boolean forceRestart) {
    try {
      Socket socket = new Socket("127.0.0.1", STUDIO_PORT);
      try {
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        try {
          ApplicationPatch.write(output, changes, forceRestart);

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
        final JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
        if (frame == null) {
          return;
        }
        final JComponent component = frame.getRootPane();
        if (component == null) {
          LOG.warn(e);
          return;
        }
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Rectangle rect = component.getVisibleRect();
            Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
            RelativePoint point = new RelativePoint(component, p);
            JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("No connection to app; cannot sync resource changes",
                                                                      MessageType.WARNING.getDefaultIcon(),
                                                                      MessageType.WARNING.getPopupBackground(), null)
              .setShowCallout(false).setCloseButtonEnabled(true)
              .createBalloon().show(point, Balloon.Position.atLeft);
          }
        });
        return;
      }
      LOG.warn(e);
    }
    catch (IOException e) {
      LOG.warn(e);
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
    state.executeDeviceCommandAndWriteToConsole(device, "adb shell rm -rf " + getDataFolder(pkg), receiver);
  }

  private static long ourBeginTime;

  public static void notifyBegin() {
    ourBeginTime = System.currentTimeMillis();
  }

  public static void notifyEnd(Project project) {
    long end = System.currentTimeMillis();
    final String message = "Instant Run: " + (end - ourBeginTime) + "ms";
    JFrame frame = WindowManager.getInstance().getFrame(project.isDefault() ? null : project);
    if (frame == null) {
      return;
    }
    final JComponent component = frame.getRootPane();
    if (component == null) {
      LOG.info(message);
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Rectangle rect = component.getVisibleRect();
        Point p = new Point(rect.x + rect.width - 10, rect.y + 10);
        RelativePoint point = new RelativePoint(component, p);
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message,
                                                                  MessageType.WARNING.getDefaultIcon(),
                                                                  MessageType.WARNING.getPopupBackground(), null)
          .setShowCallout(false).setCloseButtonEnabled(true)
          .createBalloon().show(point, Balloon.Position.atLeft);
      }
    });
  }
}
