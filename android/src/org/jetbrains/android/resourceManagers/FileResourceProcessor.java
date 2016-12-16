package org.jetbrains.android.resourceManagers;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public interface FileResourceProcessor {
  boolean process(@NotNull VirtualFile resFile, @NotNull String resName, @Nullable String libraryName);
}
