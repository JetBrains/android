package org.jetbrains.android;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkSourcesStubBuilder implements BinaryFileStubBuilder {
  private static final int VERSION = 0;

  @Override
  public boolean acceptsFile(VirtualFile file) {
    return file.getFileType() == StdFileTypes.JAVA;
  }

  @Nullable
  @Override
  public Stub buildStubTree(FileContent fileContent) {
    if (!isUnderAndroidSdk(fileContent.getProject(), fileContent.getFile())) {
      return null;
    }
    final IFileElementType type = LanguageParserDefinitions.INSTANCE.
      forLanguage(JavaLanguage.INSTANCE).getFileNodeType();
    final PsiFile file = fileContent.getPsiFile();
    file.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, fileContent.getContentAsText());

    try {
      if (type instanceof IStubFileElementType) {
        return ((IStubFileElementType)type).getBuilder().buildStubTree(file);
      }
    }
    finally {
      file.putUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY, null);
    }
    return null;
  }
  
  private static boolean isUnderAndroidSdk(@NotNull Project project, @NotNull VirtualFile file) {
    final List<OrderEntry> orderEntries = ProjectFileIndex.SERVICE.
      getInstance(project).getOrderEntriesForFile(file);

    if (orderEntries.isEmpty()) {
      return false;
    }

    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof JdkOrderEntry) {
        final Sdk sdk = ((JdkOrderEntry)orderEntry).getJdk();

        if (sdk != null && sdk.getSdkType() instanceof AndroidSdkType) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int getStubVersion() {
    return JavaFileElementType.STUB_VERSION + VERSION;
  }
}
