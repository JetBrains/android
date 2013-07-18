package org.jetbrains.android;

import com.intellij.openapi.module.impl.scopes.JdkScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SdkResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkResolveScopeProvider extends SdkResolveScopeProvider {
  @Override
  public GlobalSearchScope getScope(@NotNull Project project, @NotNull JdkOrderEntry entry) {
    final Sdk sdk = entry.getJdk();

    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }

    if (sdk.getRootProvider().getFiles(OrderRootType.SOURCES).length == 0) {
      return null;
    }
    return new JdkScope(project, entry) {
      @Nullable
      @Override
      protected VirtualFile getFileRoot(VirtualFile file) {
        return myIndex.isInLibrarySource(file)
               ? myIndex.getSourceRootForFile(file)
               : null;
      }

      @Override
      public boolean isForceSearchingInLibrarySources() {
        return true;
      }
    };
  }
}
