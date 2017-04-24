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
package com.android.tools.idea.lint;

import com.android.SdkConstants;
import com.android.ide.common.res2.DataFile;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceFolderRegistry;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class AndroidLintObsoleteSdkIntInspection extends AndroidLintInspectionBase {
  public AndroidLintObsoleteSdkIntInspection() {
    super(AndroidBundle.message("android.lint.inspections.obsolete.sdk.int"), ApiDetector.OBSOLETE_SDK);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData) {
    if (fixData instanceof LintFix.DataMap) {
      LintFix.DataMap map = (LintFix.DataMap)fixData;

      Boolean constant = map.get(Boolean.class);
      if (constant != null) {
        PsiBinaryExpression subExpression = PsiTreeUtil.getParentOfType(startElement, PsiBinaryExpression.class, false);
        if (subExpression != null) {
          return new AndroidLintQuickFix[]{
            new AndroidLintQuickFix.LocalFixWrappee(new SimplifyBooleanExpressionFix(subExpression, constant))
          };
        }
      } else {
        // Merge resource folder
        File file = map.get(File.class);
        AndroidVersion minSdkVersion = map.get(AndroidVersion.class);
        String destFolder = map.get(String.class);

        if (file != null && destFolder != null && minSdkVersion != null) {
          AndroidFacet facet = AndroidFacet.getInstance(startElement);
          VirtualFile dir = StandardFileSystems.local().findFileByPath(file.getPath());
          if (facet != null && dir != null) {
            return new AndroidLintQuickFix[]{
              new MergeResourceFolderFix(facet, dir, destFolder, minSdkVersion)
            };
          }
        }
      }
    }

    return super.getQuickFixes(startElement, endElement, message, fixData);
  }

  /**
   * Quickfix which given a directory (such as values-land-v14) and a base folder name (such as values-land)
   * merges all the resources found in the directory into the base folder instead, replacing any existing
   * resources of the same name+type there.
   * <p>
   * TODO: Consider adding this as a refactoring action, or a context menu action on resource folders
   */
  private static class MergeResourceFolderFix implements AndroidLintQuickFix {
    private final AndroidFacet facet;
    private final VirtualFile dir;
    private final String destFolderName;
    private final AndroidVersion minSdkVersion;
    List<VirtualFile> sourceFolders;

    public MergeResourceFolderFix(@NotNull AndroidFacet facet, @NotNull VirtualFile dir, @NotNull String destFolderName,
                                  AndroidVersion minSdkVersion) {
      this.facet = facet;
      this.dir = dir;
      this.destFolderName = destFolderName;
      this.minSdkVersion = minSdkVersion;
    }

    /**
     * I may have to merge *multiple* folders to get to the base folder.
     * For example, if minSdkVersion is 14 and you have values-v5, values-v7, values-v14, and values,
     * we should merge values-v5 into values-v7, then value-v7 into values-v14, and then finally values-v14 into values/
     * @return a list of applicable folders, starting with {@link #dir} and including all similar folders
     * up to the {@link #destFolderName}
     */
    private List<VirtualFile> findSourceFolders() {
      if (sourceFolders == null) {
        List<VirtualFile> folders = Lists.newArrayList();

        int apiLevel = minSdkVersion.getFeatureLevel();
        String dirName = dir.getName();
        FolderConfiguration oldConfig = FolderConfiguration.getConfigForFolder(dirName);
        assert oldConfig != null;
        assert oldConfig.getVersionQualifier() != null; // otherwise it wouldn't have been flagged
        int startingVersion = oldConfig.getVersionQualifier().getVersion();
        oldConfig.setVersionQualifier(null); // Clear version qualifier: we want to make sure candidates equal except for this one qualifier
        ResourceFolderType folderType = ResourceFolderType.getFolderType(dirName);
        for (VirtualFile folder : dir.getParent().getChildren()) {
          // We'll include dir in this list too
          if (!folder.isDirectory()) {
            continue;
          }
          if (folderType != ResourceFolderType.getFolderType(folder.getName())) {
            continue;
          }
          FolderConfiguration siblingConfig = FolderConfiguration.getConfigForFolder(folder.getName());
          if (siblingConfig == null) {
            continue;
          }
          VersionQualifier versionQualifier = siblingConfig.getVersionQualifier();
          if (versionQualifier == null || versionQualifier.hasFakeValue() || versionQualifier.getVersion() > apiLevel ||
              versionQualifier.getVersion() < startingVersion) {
            continue;
          }
          siblingConfig.setVersionQualifier(null);
          if (!siblingConfig.equals(oldConfig)) {
            continue;
          }

          folders.add(folder);
        }

        // Sort numerically, not alphabetically
        Collections.sort(folders, (f1, f2) -> {
          FolderConfiguration configuration1 = FolderConfiguration.getConfigForFolder(f1.getName());
          FolderConfiguration configuration2 = FolderConfiguration.getConfigForFolder(f2.getName());
          assert configuration1 != null; // because otherwise it wouldn't have made it into the folders list above
          assert configuration2 != null;
          // negate comparison: FolderConfiguration sorts higher densities first; we want lower.
          return -configuration1.compareTo(configuration2);
        });
        sourceFolders = folders;
      }

      return sourceFolders;
    }

    /**
     * Use resource repository; move all resource files over to the dest folder, if they don't already exist; if they do; merge.
     * For each resource, replace base folder occurrences with those from the -v5 folder. Apply formatter if there were any
     * edits. Finally delete files and folder.
     */
    @Override
    public void apply(@NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @NotNull AndroidQuickfixContexts.Context context) {
      ResourceFolderRepository repository = ResourceFolderRegistry.get(facet, dir.getParent());
      Project project = facet.getModule().getProject();

      List<VirtualFile> folders = findSourceFolders();
      if (!folders.isEmpty()) {
        // Merge intermediate folders, e.g. if we're going from -v4  and -v7 into values/, first merge -v47 into -v7
        for (int i = 0; i < folders.size() - 1; i++) {
          VirtualFile folder = folders.get(i);
          VirtualFile target = folders.get(i + 1);
          mergeResourceFolder(project, folder, target.getName(), repository);
        }
        // Finally, merge into the base folders
        mergeResourceFolder(project, folders.get(folders.size() - 1), destFolderName, repository);
      }
    }

    private static void mergeResourceFolder(@NotNull Project project, @NotNull VirtualFile dir,
                                            @NotNull String targetDir, @NotNull LocalResourceRepository repository) {
      Object requestor = AndroidLintInspectionBase.class;
      VirtualFileSystem fileSystem = StandardFileSystems.local();

      FolderConfiguration oldConfig = FolderConfiguration.getConfigForFolder(dir.getName());
      FolderConfiguration newConfig = FolderConfiguration.getConfigForFolder(targetDir);
      assert oldConfig != null;
      assert newConfig != null;

      // Find all the applicable resource items
      Multimap<String, ResourceType> destFolderResources = ArrayListMultimap.create(100, 2);
      List<ResourceItem> srcItems = Lists.newArrayList();
      for (ListMultimap<String, ResourceItem> multimap : repository.getItems().values()) {
        for (ResourceItem item : multimap.values()) {
          if (item.getSourceType() == DataFile.FileType.GENERATED_FILES) {
            continue;
          }
          FolderConfiguration configuration = item.getConfiguration();
          if (oldConfig.equals(configuration)) {
            ResourceFile source = item.getSource();
            if (source == null) {
              continue;
            }

            VirtualFile sourceFile = fileSystem.findFileByPath(source.getFile().getPath());
            if (sourceFile != null && dir.equals(sourceFile.getParent())) {
              srcItems.add(item);
            }
          }
          else if (newConfig.equals(configuration)) {
            destFolderResources.put(item.getName(), item.getType());
          }
        }
      }

      VirtualFile res = dir.getParent();
      if (res == null) {
        return;
      }

      try {
        VirtualFile destDir = res.findOrCreateChildData(requestor, targetDir);
        for (ResourceItem item : srcItems) {
          DataFile.FileType sourceType = item.getSourceType();
          if (sourceType == DataFile.FileType.GENERATED_FILES) {
            continue;
          }

          boolean isInDest = destFolderResources.containsEntry(item.getName(), item.getType());

          // Already checked above
          //noinspection ConstantConditions
          if (item.getSource().getFile().getParentFile().getName().startsWith(SdkConstants.FD_RES_VALUES)) {
            // Merge XML values
            String value = StringUtil.notNullize(item.getValueText());

            ResourceFile source = item.getSource();
            String fileName = source != null ? source.getFile().getName() : "values.xml";
            List<String> dirNames = Collections.singletonList(targetDir);
            if (isInDest) {
              AndroidResourceUtil.changeValueResource(project, res, item.getName(), item.getType(), value, fileName, dirNames, false);
            }
            else {
              AndroidResourceUtil.createValueResource(project, res, item.getName(), item.getType(), fileName,
                                                      dirNames, value);
            }
          }
          else {
            VirtualFile source = fileSystem.findFileByPath(item.getFile().getPath());
            if (source != null) {
              VirtualFile existing = destDir.findChild(source.getName());
              if (existing != null) {
                existing.delete(requestor);
              }
              VfsUtilCore.copyFile(requestor, source, destDir);
            } else {
              // Something went wrong; couldn't copy file, so skip out (so we don't delete sources)
              return;
            }
          }
        }

        for (VirtualFile resourceFile : dir.getChildren()) {
          resourceFile.delete(requestor);
        }

        // Finally delete the obsolete -vNN folder. This may not succeed if there are any other
        // files in the directory such as *.DS_Store, *~, etc.
        dir.delete(requestor);
      }
      catch (IOException e) {
        Logger.getInstance(AndroidLintInspectionBase.class).error(e);
      }
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      return dir.isDirectory();
    }

    @NotNull
    @Override
    public String getName() {
      List<VirtualFile> folders = findSourceFolders();
      List<String> names = Lists.newArrayListWithCapacity(folders.size());
      for (VirtualFile file : folders) {
        String name = file.getName();
        int index = name.indexOf('-');
        if (index != -1) {
          name = name.substring(index);
        }
        names.add(name);
      }
      String sourceFolders = Joiner.on(" and ").join(names);
      return String.format("Merge resources from %1$s into %2$s", sourceFolders, destFolderName);
    }
  }
}
