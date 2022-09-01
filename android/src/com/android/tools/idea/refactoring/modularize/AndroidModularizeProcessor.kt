/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_MANIFEST;
import static com.android.SdkConstants.TAG_RESOURCES;

import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceFolderRegistry;
import com.android.tools.idea.res.ResourceFolderRepository;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JComponent;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.android.AndroidFileTemplateProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: Should we eventually plug into MoveClassHandler.EP_NAME extensions? Offer a QuickFix at any point?
public class
AndroidModularizeProcessor extends BaseRefactoringProcessor {

  private static final Logger LOGGER = Logger.getInstance(AndroidModularizeProcessor.class);

  private final PsiElement[] myRoots;
  private final Set<PsiClass> myClasses;
  private final Set<ResourceItem> myResources;
  private final Set<PsiElement> myManifestEntries;
  private final AndroidCodeAndResourcesGraph myReferenceGraph;
  private Module myTargetModule;
  private boolean myShouldSelectAllReferences;

  protected AndroidModularizeProcessor(@NotNull Project project,
                                       @NotNull PsiElement[] roots,
                                       @NotNull Set<PsiClass> classes,
                                       @NotNull Set<ResourceItem> resources,
                                       @NotNull Set<PsiElement> manifestEntries,
                                       @NotNull AndroidCodeAndResourcesGraph referenceGraph) {
    super(project);
    myRoots = roots;
    myClasses = classes;
    myResources = resources;
    myManifestEntries = manifestEntries;
    myReferenceGraph = referenceGraph;
  }

  public void setTargetModule(@NotNull Module module) {
    myTargetModule = module;

    // Tune default selection behavior: it's safe to select all references only if the target module is depended on (downstream dependency).
    myShouldSelectAllReferences = true;
    for (PsiElement root : myRoots) {
      AndroidFacet facet = AndroidFacet.getInstance(root);
      if (facet != null) {
        if (!collectModulesClosure(facet.getModule(), Sets.newHashSet()).contains(myTargetModule)) {
          myShouldSelectAllReferences = false;
          break;
        }
      }
    }
  }

  private static Set<Module> collectModulesClosure(@NotNull Module module, Set<Module> result) {
    if (result.add(module)) {
      for (Module depModule : ModuleRootManager.getInstance(module).getDependencies()) {
        collectModulesClosure(depModule, result);
      }
    }
    return result;
  }

  public int getClassesCount() {
    return myClasses.size();
  }

  public int getResourcesCount() {
    return myResources.size();
  }

  @VisibleForTesting
  AndroidCodeAndResourcesGraph getReferenceGraph() {
    return myReferenceGraph;
  }

  @VisibleForTesting
  boolean shouldSelectAllReferences() {
    return myShouldSelectAllReferences;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        PsiElement[] result = new PsiElement[usages.length];
        for (int i = 0; i < usages.length; i++) {
          result[i] = usages[i].getElement();
        }
        return result;
      }

      @Override
      public String getProcessedElementsHeader() {
        return "Items to be moved";
      }

      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return String.format(Locale.US, "%1$d resources in %2$d files", usagesCount, filesCount);
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> result = new ArrayList<>();

    for (PsiElement clazz : myClasses) {
      result.add(new UsageInfo(clazz));
    }

    for (PsiElement tag : myManifestEntries) {
      result.add(new UsageInfo(tag));
    }

    for (ResourceItem resource : myResources) {
      PsiFile psiFile = IdeResourcesUtil.getItemPsiFile(myProject, resource);
      if (IdeResourcesUtil.getFolderType(psiFile) == ResourceFolderType.VALUES) {
        // This is just a value, so we won't move the entire file, just its corresponding XmlTag
        XmlTag xmlTag = IdeResourcesUtil.getItemTag(myProject, resource);
        if (xmlTag != null) {
          result.add(new ResourceXmlUsageInfo(xmlTag, resource));
        }
      }
      else if (psiFile instanceof PsiBinaryFile) {
        // The usage view doesn't handle binaries at all. Work around this (for example,
        // the UsageInfo class asserts in the constructor if the element doesn't have
        // a text range.)
        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(myProject);
        SmartPsiElementPointer<PsiElement> smartPointer = smartPointerManager.createSmartPsiElementPointer(psiFile);
        SmartPsiFileRange smartFileRange =
          smartPointerManager.createSmartPsiFileRangePointer(psiFile, TextRange.EMPTY_RANGE);
        result.add(new ResourceXmlUsageInfo(smartPointer, smartFileRange, resource) {
          @Override
          public boolean isValid() {
            return true;
          }

          @Override
          @Nullable
          public Segment getSegment() {
            return null;
          }
        });
      }
      else if (psiFile != null) {
        result.add(new ResourceXmlUsageInfo(psiFile, resource));
      }
      // TODO: What about references from build.gradle files?
    }

    return UsageViewUtil.removeDuplicatedUsages(result.toArray(UsageInfo.EMPTY_ARRAY));
  }

  @Override
  protected void previewRefactoring(@NotNull UsageInfo[] usages) {
    PreviewDialog previewDialog = new PreviewDialog(myProject, myReferenceGraph, usages, myShouldSelectAllReferences);
    if (previewDialog.showAndGet()) {
      TransactionGuard.getInstance().submitTransactionAndWait(() -> execute(previewDialog.getSelectedUsages()));
    }
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    AndroidFacet facet = AndroidFacet.getInstance(myTargetModule);
    assert facet != null; // We know this has to be an Android module

    IdeaSourceProvider sources = SourceProviderManager.getInstance(facet).getSources();
    Iterable<VirtualFile> sourceFolders = Iterables.concat(sources.getJavaDirectories(), sources.getKotlinDirectories());
    VirtualFile javaTargetDir = Iterables.getFirst(sourceFolders, null);

    VirtualFile resDir = ResourceFolderManager.getInstance(facet).getFolders().get(0);
    ResourceFolderRepository repo = ResourceFolderRegistry.getInstance(myProject).get(facet, resDir);

    Set<XmlFile> touchedXmlFiles = new HashSet<>();

    for (UsageInfo usage : usages) {
      PsiElement element = usage.getElement();

      if (usage instanceof ResourceXmlUsageInfo) {
        ResourceItem resource = ((ResourceXmlUsageInfo)usage).getResourceItem();

        if (element instanceof PsiFile) {
          PsiDirectory targetDir = getOrCreateTargetDirectory(repo, resource);
          if (targetDir != null && targetDir.findFile(((PsiFile)element).getName()) == null) {
            MoveFilesOrDirectoriesUtil.doMoveFile((PsiFile)element, targetDir);
          }
          // TODO: What if a file with that name exists?
        }
        else if (element instanceof XmlTag) {
          XmlFile resourceFile = (XmlFile)getOrCreateTargetValueFile(repo, resource);
          if (resourceFile != null) {
            XmlTag rootTag = resourceFile.getRootTag();
            if (rootTag != null && TAG_RESOURCES.equals(rootTag.getName())) {
              rootTag.addSubTag((XmlTag)element.copy(), false);
              element.delete();
              touchedXmlFiles.add(resourceFile);
            }
          }
          else {
            // We don't move stuff if we can't find the destination resource file
          }
        }
      }
      else if (element instanceof XmlTag) { // This has to be a manifest entry
        XmlFile manifest = (XmlFile)getOrCreateTargetManifestFile(facet);
        if (manifest != null) {
          // TODO: More generally we should recreate the parent chain of tags. For now, we assume the destination is always an
          // <application> tag inside a <manifest>.
          manifest.acceptChildren(new XmlRecursiveElementWalkingVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              if (TAG_MANIFEST.equals(tag.getName())) {
                XmlTag applicationTag = null;
                for (PsiElement child : tag.getChildren()) {
                  if (child instanceof XmlTag && TAG_APPLICATION.equals(((XmlTag)child).getName())) {
                    applicationTag = (XmlTag)child;
                    applicationTag.addSubTag((XmlTag)element.copy(), false);
                    element.delete();
                    break;
                  }
                }
                if (applicationTag == null) { // We need to create one; this happens with manifests created by the new module wizard.
                  applicationTag = XmlElementFactory.getInstance(myProject).createTagFromText("<" + TAG_APPLICATION + "/>");
                  applicationTag.addSubTag((XmlTag)element.copy(), false);
                  element.delete();
                  tag.addSubTag(applicationTag, true);
                }
                touchedXmlFiles.add(manifest);
              }
              else {
                super.visitXmlTag(tag);
              }
            }
          });
        }
      }
      else if (element instanceof PsiClass) {
        String packageName = ((PsiJavaFile)(element).getContainingFile()).getPackageName();

        MoveClassesOrPackagesUtil.doMoveClass(
          (PsiClass)element,
          CommonJavaRefactoringUtil
            .createPackageDirectoryInSourceRoot(new PackageWrapper(PsiManager.getInstance(myProject), packageName), javaTargetDir),
          true);
      }
    }

    // Reformat the XML files we edited via PSI operations.
    for (XmlFile touchedFile : touchedXmlFiles) {
      CodeStyleManager.getInstance(myProject).reformat(touchedFile);
    }
  }

  @Nullable
  private PsiDirectory getOrCreateTargetDirectory(ResourceFolderRepository base, ResourceItem resourceItem) {
    PsiManager manager = PsiManager.getInstance(myProject);
    PathString itemFile = resourceItem.getSource();
    if (itemFile != null) {
      ResourceFolderType folderType = ResourceFolderType.getFolderType(itemFile.getParentFileName());
      if (folderType != null) {
        try {
          return manager.findDirectory(
            VfsUtil.createDirectoryIfMissing(base.getResourceDir(), resourceItem.getConfiguration().getFolderName(folderType)));
        }
        catch (Exception ex) {
          LOGGER.debug(ex);
        }
      }
    }
    LOGGER.warn("Couldn't determine target folder for resource " + resourceItem);
    return null;
  }

  @Nullable
  private PsiFile getOrCreateTargetValueFile(ResourceFolderRepository base, ResourceItem resourceItem) {
    PathString itemFile = resourceItem.getSource();
    if (itemFile != null) {
      try {
        String name = itemFile.getFileName();
        PsiDirectory dir = getOrCreateTargetDirectory(base, resourceItem);
        if (dir != null) {
          PsiFile result = dir.findFile(name);
          if (result != null) {
            return result;
          }

          // TODO: How do we make sure the custom templates are applied for new files (license, author, etc) ?
          return (PsiFile)AndroidFileTemplateProvider
            .createFromTemplate(AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, name, dir);
        }
      }
      catch (Exception ex) {
        LOGGER.debug(ex);
      }
    }
    LOGGER.warn("Couldn't determine target file for resource " + resourceItem);
    return null;
  }

  @Nullable
  private PsiFile getOrCreateTargetManifestFile(AndroidFacet facet) {
    if (facet.isDisposed()) return null;
    PsiManager manager = PsiManager.getInstance(myProject);
    String manifestFileUrl = Iterables.getFirst(SourceProviderManager.getInstance(facet).getSources().getManifestFileUrls(), null);
    if (manifestFileUrl != null) {
      VirtualFile manifestFile = VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl);

      if (manifestFile != null) {
        return manager.findFile(manifestFile);
      }
      else {
        String parentDir = VfsUtil.getParentDir(manifestFileUrl);
        if (parentDir != null) {
          VirtualFile directory = VirtualFileManager.getInstance().findFileByUrl(parentDir);
          if (directory != null) {
            PsiDirectory targetDirectory = manager.findDirectory(directory);
            if (targetDirectory != null) {
              try {
                return (PsiFile)AndroidFileTemplateProvider
                  .createFromTemplate(AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE, FN_ANDROID_MANIFEST_XML, targetDirectory);
              }
              catch (Exception ex) {
                LOGGER.debug(ex);
              }
            }
          }
        }
      }
    }
    LOGGER.warn("Couldn't determine manifest file for module " + myTargetModule);
    return null;
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return "Moving " + RefactoringUIUtil.calculatePsiElementDescriptionList(myRoots);
  }


  public static class ResourceXmlUsageInfo extends UsageInfo {

    private final ResourceItem myResourceItem;

    public ResourceXmlUsageInfo(@NotNull PsiElement element, @NotNull ResourceItem resourceItem) {
      super(element);
      myResourceItem = resourceItem;
    }

    public ResourceXmlUsageInfo(@NotNull SmartPsiElementPointer<?> smartPointer,
                                @Nullable SmartPsiFileRange psiFileRange,
                                @NotNull ResourceItem resourceItem) {
      super(smartPointer, psiFileRange, false, false);
      myResourceItem = resourceItem;
    }

    @NotNull
    public ResourceItem getResourceItem() {
      return myResourceItem;
    }
  }

  static class PreviewDialog extends DialogWrapper {

    private final AndroidModularizePreviewPanel myPanel;

    protected PreviewDialog(@Nullable Project project, @NotNull AndroidCodeAndResourcesGraph graph, @NotNull UsageInfo[] infos,
                            boolean shouldSelectAllReferences) {
      super(project, true);

      myPanel = new AndroidModularizePreviewPanel(graph, infos, shouldSelectAllReferences);
      setTitle("Modularize: Preview Classes and Resources to Be Moved");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myPanel.getPanel();
    }

    @NotNull
    public UsageInfo[] getSelectedUsages() {
      return myPanel.getSelectedUsages();
    }
  }
}
