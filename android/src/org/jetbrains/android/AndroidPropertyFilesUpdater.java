// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.SingleAlarm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// This class supports JPS projects and relies on APIs which should not be used in AS otherwise. We suppress related warnings to
// avoid cluttering of the build output.
@SuppressWarnings("deprecation")
@Service
public final class AndroidPropertyFilesUpdater implements Disposable {
  private static final NotificationGroup PROPERTY_FILES_UPDATING_NOTIFICATION =
    NotificationGroup.balloonGroup("Android Property Files Updating", PluginId.getId("org.jetbrains.android"));
  private static final Key<List<Object>> ANDROID_PROPERTIES_STATE_KEY = Key.create("ANDROID_PROPERTIES_STATE");
  private Notification myNotification;
  private final SingleAlarm myAlarm;
  private final Project myProject;

  public static class ModuleRootListenerImpl implements ModuleRootListener {
    @Override
    public void rootsChanged(@NotNull final ModuleRootEvent event) {
      Project project = event.getProject();
      if (!project.isDefault()) {
        project.getService(AndroidPropertyFilesUpdater.class).onRootsChanged();
      }
    }
  }

  private AndroidPropertyFilesUpdater(Project project) {
    myAlarm = new SingleAlarm(this::updatePropertyFilesIfNecessary, 50, this);
    myProject = project;
  }

  @Override
  public void dispose() {
    if (myNotification != null && !myNotification.isExpired()) {
      myNotification.expire();
    }
    myAlarm.cancel();
  }

  private void onRootsChanged() {
    if (!ApplicationManager.getApplication().isUnitTestMode() &&
        !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(myAlarm::cancelAndRequest);
    }
  }

  private void updatePropertyFilesIfNecessary() {
    if (myProject.isDisposed()) return;
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final List<VirtualFile> toAskFiles = new ArrayList<>();
    final List<AndroidFacet> toAskFacets = new ArrayList<>();
    final List<Runnable> toAskChanges = new ArrayList<>();

    final List<VirtualFile> files = new ArrayList<>();
    final List<Runnable> changes = new ArrayList<>();

    for (AndroidFacet facet : ProjectFacetManager.getInstance(myProject).getFacets(AndroidFacet.ID)) {
      if (!AndroidModel.isRequired(facet)) {
        final String updatePropertyFiles = facet.getProperties().UPDATE_PROPERTY_FILES;
        final boolean ask = updatePropertyFiles.isEmpty();

        if (!ask && !Boolean.parseBoolean(updatePropertyFiles)) {
          continue;
        }
        final Pair<VirtualFile, List<Runnable>> pair = updateProjectPropertiesIfNecessary(facet);

        if (pair != null) {
          if (ask) {
            toAskFacets.add(facet);
            toAskFiles.add(pair.getFirst());
            toAskChanges.addAll(pair.getSecond());
          }
          else {
            files.add(pair.getFirst());
            changes.addAll(pair.getSecond());
          }
        }
      }
    }

    /* We should expire old notification even if there are no properties to update in current event.
     For example, user changed "is library" setting to 'true', the notification was shown, but user ignored it.
     Then he changed the setting to 'false' again. New notification won't be shown, because the value of
     "android.library" in project.properties is correct. However if the old notification was not expired,
     user may press on it, and "android.library" property will be changed to 'false'. */
    if (myNotification != null && !myNotification.isExpired()) {
      myNotification.expire();
    }

    if (!changes.isEmpty() || !toAskChanges.isEmpty()) {
      if (!toAskChanges.isEmpty()) {
        askUserIfUpdatePropertyFile(myProject, toAskFacets, new Processor<MyResult>() {
          @Override
          public boolean process(MyResult result) {
            if (result == MyResult.NEVER) {
              for (AndroidFacet facet : toAskFacets) {
                facet.getProperties().UPDATE_PROPERTY_FILES = Boolean.FALSE.toString();
              }
              return true;
            }
            else if (result == MyResult.ALWAYS) {
              for (AndroidFacet facet : toAskFacets) {
                facet.getProperties().UPDATE_PROPERTY_FILES = Boolean.TRUE.toString();
              }
            }
            if (ReadonlyStatusHandler.ensureFilesWritable(myProject, toAskFiles.toArray(VirtualFile.EMPTY_ARRAY))) {
              CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
                @Override
                public void run() {
                  ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                      for (Runnable change : toAskChanges) {
                        change.run();
                      }
                    }
                  });
                }
              }, "Update Android property files", null);
            }
            return true;
          }
        });
      }

      if (!changes.isEmpty() && ReadonlyStatusHandler.ensureFilesWritable(
        myProject, files.toArray(VirtualFile.EMPTY_ARRAY))) {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                for (Runnable change : changes) {
                  change.run();
                }
              }
            });
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
          }
        }, "Update Android property files", null);
      }
    }
  }

  @Nullable
  private static Pair<VirtualFile, List<Runnable>> updateProjectPropertiesIfNecessary(@NotNull AndroidFacet facet) {
    if (facet.isDisposed()) {
      return null;
    }
    final Module module = facet.getModule();
    final Pair<PropertiesFile, VirtualFile> pair =
      AndroidRootUtil.findPropertyFile(module, SdkConstants.FN_PROJECT_PROPERTIES);

    if (pair == null) {
      return null;
    }
    final PropertiesFile projectProperties = pair.getFirst();
    final VirtualFile projectPropertiesVFile = pair.getSecond();

    final Pair<Properties, VirtualFile> localProperties =
      AndroidRootUtil.readPropertyFile(module, SdkConstants.FN_LOCAL_PROPERTIES);
    final List<Runnable> changes = new ArrayList<>();

    final AndroidPlatform androidPlatform = AndroidPlatform.getInstance(facet.getModule());
    final IAndroidTarget androidTarget = androidPlatform == null ? null : androidPlatform.getTarget();
    final String androidTargetHashString = androidTarget != null ? androidTarget.hashString() : null;
    final VirtualFile[] dependencies = collectDependencies(module);
    final String[] dependencyPaths = toSortedPaths(dependencies);

    final List<Object> newState = Arrays.asList(
      androidTargetHashString,
      facet.getConfiguration().getProjectType(),
      Arrays.asList(dependencyPaths),
      facet.getProperties().ENABLE_MANIFEST_MERGING,
      facet.getProperties().ENABLE_PRE_DEXING);
    final List<Object> state = facet.getUserData(ANDROID_PROPERTIES_STATE_KEY);

    if (state == null || !Objects.equals(state, newState)) {
      updateTargetProperty(facet, projectProperties, changes);
      updateProjectTypeProperty(facet, projectProperties, changes);
      updateManifestMergerProperty(facet, projectProperties, changes);
      updateDependenciesInPropertyFile(projectProperties, localProperties, dependencies, changes);

      facet.putUserData(ANDROID_PROPERTIES_STATE_KEY, newState);
    }
    return !changes.isEmpty() ? Pair.create(projectPropertiesVFile, changes) : null;
  }

  private static void updateDependenciesInPropertyFile(@NotNull final PropertiesFile projectProperties,
                                                       @Nullable final Pair<Properties, VirtualFile> localProperties,
                                                       @NotNull final VirtualFile[] dependencies,
                                                       @NotNull List<Runnable> changes) {
    final VirtualFile vFile = projectProperties.getVirtualFile();
    if (vFile == null) {
      return;
    }
    final Set<VirtualFile> localDependencies = localProperties != null
                                               ? ImportDependenciesUtil.getLibDirs(localProperties)
                                               : Collections.emptySet();
    final VirtualFile baseDir = vFile.getParent();
    final String baseDirPath = baseDir.getPath();
    final List<String> newDepValues = new ArrayList<>();

    for (VirtualFile dependency : dependencies) {
      if (!localDependencies.contains(dependency)) {
        final String relPath = FileUtil.getRelativePath(baseDirPath, dependency.getPath(), '/');
        final String value = relPath != null ? relPath : dependency.getPath();
        newDepValues.add(value);
      }
    }
    final Set<String> oldDepValues = new HashSet<>();

    for (IProperty property : projectProperties.getProperties()) {
      final String name = property.getName();
      if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
        oldDepValues.add(property.getValue());
      }
    }

    if (!new HashSet<>(newDepValues).equals(oldDepValues)) {
      changes.add(new Runnable() {
        @Override
        public void run() {
          for (IProperty property : projectProperties.getProperties()) {
            final String name = property.getName();
            if (name != null && name.startsWith(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX)) {
              property.getPsiElement().delete();
            }
          }

          for (int i = 0; i < newDepValues.size(); i++) {
            final String value = newDepValues.get(i);
            projectProperties.addProperty(AndroidUtils.ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX + (i + 1), value);
          }
        }
      });
    }
  }

  @NotNull
  private static VirtualFile[] collectDependencies(@NotNull Module module) {
    final List<VirtualFile> dependenciesList = new ArrayList<>();

    for (AndroidFacet depFacet : AndroidUtils.getAndroidLibraryDependencies(module)) {
      final Module depModule = depFacet.getModule();
      final VirtualFile libDir = getBaseAndroidContentRoot(depModule);
      if (libDir != null) {
        dependenciesList.add(libDir);
      }
    }
    return dependenciesList.toArray(VirtualFile.EMPTY_ARRAY);
  }

  private static void updateTargetProperty(@NotNull AndroidFacet facet,
                                           @NotNull final PropertiesFile propertiesFile,
                                           @NotNull List<Runnable> changes) {
    final Project project = facet.getModule().getProject();
    final AndroidPlatform androidPlatform = AndroidPlatform.getInstance(facet.getModule());
    final IAndroidTarget androidTarget = androidPlatform == null ? null : androidPlatform.getTarget();

    if (androidTarget != null) {
      final String targetPropertyValue = androidTarget.hashString();
      final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_TARGET_PROPERTY);


      if (property == null) {
        changes.add(new Runnable() {
          @Override
          public void run() {
            propertiesFile.addProperty(createProperty(project, targetPropertyValue));
          }
        });
      }
      else {
        if (!Objects.equals(property.getValue(), targetPropertyValue)) {
          final PsiElement element = property.getPsiElement();
          changes.add(new Runnable() {
            @Override
            public void run() {
              element.replace(createProperty(project, targetPropertyValue).getPsiElement());
            }
          });
        }
      }
    }
  }

  public static void updateProjectTypeProperty(@NotNull AndroidFacet facet,
                                               @NotNull final PropertiesFile propertiesFile,
                                               @NotNull List<Runnable> changes) {
    IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_PROJECT_TYPE_PROPERTY);
    String value = Integer.toString(facet.getConfiguration().getProjectType());

    if (property != null) {

      if (!value.equals(property.getValue())) {
        changes.add(() -> property.setValue(value));
      }
    }
    else {
      changes.add(() -> propertiesFile.addProperty(AndroidUtils.ANDROID_PROJECT_TYPE_PROPERTY, value));
    }
  }

  public static void updateManifestMergerProperty(@NotNull AndroidFacet facet,
                                                  @NotNull final PropertiesFile propertiesFile,
                                                  @NotNull List<Runnable> changes) {
    final IProperty property = propertiesFile.findPropertyByKey(AndroidUtils.ANDROID_MANIFEST_MERGER_PROPERTY);

    if (property != null) {
      final String value = Boolean.toString(facet.getProperties().ENABLE_MANIFEST_MERGING);

      if (!value.equals(property.getValue())) {
        changes.add(new Runnable() {
          @Override
          public void run() {
            property.setValue(value);
          }
        });
      }
    }
    else if (facet.getProperties().ENABLE_MANIFEST_MERGING) {
      changes.add(new Runnable() {
        @Override
        public void run() {
          propertiesFile.addProperty(AndroidUtils.ANDROID_MANIFEST_MERGER_PROPERTY, Boolean.TRUE.toString());
        }
      });
    }
    else if (!facet.getProperties().ENABLE_PRE_DEXING) {
      changes.add(new Runnable() {
        @Override
        public void run() {
          propertiesFile.addProperty(AndroidUtils.ANDROID_DEX_DISABLE_MERGER, Boolean.TRUE.toString());
        }
      });
    }
  }

  @Nullable
  private static VirtualFile getBaseAndroidContentRoot(@NotNull Module module) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    final VirtualFile manifestFile = facet != null ? AndroidRootUtil.getPrimaryManifestFile(facet) : null;
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (manifestFile != null) {
      for (VirtualFile contentRoot : contentRoots) {
        if (VfsUtilCore.isAncestor(contentRoot, manifestFile, true)) {
          return contentRoot;
        }
      }
    }
    return contentRoots.length > 0 ? contentRoots[0] : null;
  }

  // workaround for behavior of Android SDK , which uses non-escaped ':' characters
  @NotNull
  private static IProperty createProperty(@NotNull Project project, @NotNull String targetPropertyValue) {
    final String text = AndroidUtils.ANDROID_TARGET_PROPERTY + "=" + targetPropertyValue;
    final PropertiesFile dummyFile = PropertiesElementFactory.createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  @NotNull
  private static String[] toSortedPaths(@NotNull VirtualFile[] files) {
    final String[] result = new String[files.length];

    for (int i = 0; i < files.length; i++) {
      result[i] = files[i].getPath();
    }
    Arrays.sort(result);
    return result;
  }

  private void askUserIfUpdatePropertyFile(@NotNull Project project,
                                           @NotNull Collection<AndroidFacet> facets,
                                           @NotNull final Processor<MyResult> callback) {
    final StringBuilder moduleList = new StringBuilder();

    for (AndroidFacet facet : facets) {
      moduleList.append(facet.getModule().getName()).append("<br>");
    }
    myNotification = PROPERTY_FILES_UPDATING_NOTIFICATION.createNotification(
        AndroidBundle.message("android.update.project.properties.dialog.title"),
        AndroidBundle.message("android.update.project.properties.dialog.text", moduleList.toString()),
        NotificationType.INFORMATION)
      .setListener(new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          final String desc = event.getDescription();
          if ("once".equals(desc)) {
            callback.process(MyResult.ONCE);
          }
          else if ("never".equals(desc)) {
            callback.process(MyResult.NEVER);
          }
          else {
            callback.process(MyResult.ALWAYS);
          }
          notification.expire();
        }
      });
    myNotification.notify(project);
  }

  private enum MyResult {
    ONCE, NEVER, ALWAYS
  }
}
