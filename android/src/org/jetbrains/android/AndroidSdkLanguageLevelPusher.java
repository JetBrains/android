package org.jetbrains.android;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkLanguageLevelPusher extends AbstractProjectComponent {

  private volatile Set<Sdk> myUsedAndroidSdks = Collections.emptySet();

  protected AndroidSdkLanguageLevelPusher(@NotNull Project project, @NotNull final MessageBus messageBus) {
    super(project);

    StartupManager.getInstance(project).registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        updateLanguageLevelForAllUsedSdks();

        messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
          @Override
          public void rootsChanged(final ModuleRootEvent event) {
            updateLanguageLevelForAllUsedSdks();
          }
        });
      }
    });
  }

  private void updateLanguageLevelForAllUsedSdks() {
    Set<Sdk> newUsedSdks = null;

    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

      if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
        if (newUsedSdks == null) {
          newUsedSdks = new HashSet<Sdk>();
        }
        if (newUsedSdks.add(sdk) && !myUsedAndroidSdks.contains(sdk)) {
          updateSdkLanguageLevel(myProject, sdk);
        }
      }
    }
    if (newUsedSdks != null) {
      myUsedAndroidSdks = newUsedSdks;
    }
  }

  private static void updateSdkLanguageLevel(@NotNull final Project project, @NotNull Sdk sdk) {
    final JavaLanguageLevelPusher javaPusher = new JavaLanguageLevelPusher();

    for (VirtualFile root : sdk.getRootProvider().getFiles(OrderRootType.SOURCES)) {
      if (root.isValid()) {
        final FileTypeManager fileTypeManager = FileTypeManager.getInstance();

        VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            if (fileTypeManager.isFileIgnored(file)) {
              return false;
            }
            if (file.isDirectory()) {
              PushedFilePropertiesUpdater.getInstance(project).findAndUpdateValue(file, javaPusher, LanguageLevel.HIGHEST);
            }
            return true;
          }
        });
      }
    }
  }
}
