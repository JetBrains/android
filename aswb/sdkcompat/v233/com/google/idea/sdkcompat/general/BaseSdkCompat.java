package com.google.idea.sdkcompat.general;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.fileChooser.ex.FileLookup;
import com.intellij.openapi.fileChooser.ex.LocalFsFinder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.Restarter;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for base plugin API classes, available to all IDEs. */
public final class BaseSdkCompat {
  private BaseSdkCompat() {}

  /** #api213: inline this method */
  @Nullable
  public static String getIdeRestarterPath() {
    Path startFilePath = Restarter.getIdeStarter();
    return startFilePath == null ? null : startFilePath.toString();
  }

  /** #api213: inline into IndexingLogger */
  public static JsonDuration getTotalIndexingTime(
      JsonFileProviderIndexStatistics providerStatisticInput) {
    return providerStatisticInput.getTotalIndexingVisibleTime();
  }

  /** #api213: inline this method. */
  public static String getLibraryNameFromLibraryOrigin(LibraryOrigin libraryOrigin) {
    // TODO(b/230430213): adapt getLibraryNameFromLibraryOrigin to work in 221
    return "";
  }

  /** #api213: Inline into KytheRenameProcessor. */
  public static RenamePsiElementProcessor[] renamePsiElementProcessorsList() {
    ArrayList<RenamePsiElementProcessor> result = new ArrayList<>();
    for (RenamePsiElementProcessorBase processor :
        RenamePsiElementProcessor.EP_NAME.getExtensions()) {
      if (processor instanceof RenamePsiElementProcessor) {
        result.add((RenamePsiElementProcessor) processor);
      }
    }
    return result.toArray(new RenamePsiElementProcessor[0]);
  }

  /** #api213: Inline into WorkspaceFileTextField . */
  public static LocalFsFinder.VfsFile getVfsFile(VirtualFile file) {
    return new LocalFsFinder.VfsFile(file);
  }

  /** #api213: Inline into WorkspaceFileTextField . */
  public static FileLookup.LookupFile getIoFile(Path path) {
    return new LocalFsFinder.IoFile(path);
  }

  /** #api213: Inline into BlazeProjectCreator. */
  public static OpenProjectTask createOpenProjectTask(Project project) {
    return OpenProjectTask.build().withProject(project);
  }

  /* #api213: Inline into usages. */
  public static void registerEditorNotificationProvider(
      Project project, EditorNotificationProvider provider) {
    EditorNotificationProvider.EP_NAME.getPoint(project).registerExtension(provider);
  }

  /* #api213: Inline into usages. */
  public static void unregisterEditorNotificationProvider(
      Project project, Class<? extends EditorNotificationProvider> providerClass) {
    new ProjectExtensionPointName<>("com.intellij.editorNotificationProvider")
        .getPoint(project)
        .unregisterExtension(providerClass);
  }

  /* #api213: Inline into usages. */
  public static void unregisterEditorNotificationProviders(
      Project project, Predicate<EditorNotificationProvider> filter) {
    unregisterExtensions(EditorNotificationProvider.EP_NAME.getPoint(project), filter);
  }

  private static <T> void unregisterExtensions(
      ExtensionPoint<T> extensionPoint, Predicate<T> filter) {
    for (T extension : extensionPoint.getExtensions()) {
      if (filter.test(extension)) {
        extensionPoint.unregisterExtension(extension);
      }
    }
  }

  public static String getX11WindowManagerName() {
    // TODO(b/266782325): Investigate if i3 still crashes for system notifications.
    return "";
  }
}
