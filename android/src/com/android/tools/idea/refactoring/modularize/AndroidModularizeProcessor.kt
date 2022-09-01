/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.android.SdkConstants
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_MANIFEST
import com.android.SdkConstants.TAG_RESOURCES
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceFolderType
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.res.ResourceFolderRegistry
import com.android.tools.idea.res.ResourceFolderRepository
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getItemPsiFile
import com.android.tools.idea.res.getItemTag
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiBinaryFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.refactoring.util.RefactoringUIUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.CommonJavaRefactoringUtil
import org.jetbrains.android.AndroidFileTemplateProvider
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.ResourceFolderManager
import org.jetbrains.android.facet.SourceProviderManager
import java.util.Locale
import javax.swing.JComponent


class AndroidModularizeProcessor(project: Project,
                                 private val myRoots: Array<PsiElement>,
                                 private val myClasses: Set<PsiClass>,
                                 private val myResources: Set<ResourceItem>,
                                 val myManifestEntries: Set<PsiElement>,
                                 @VisibleForTesting
                                 val referenceGraph: AndroidCodeAndResourcesGraph)
  : BaseRefactoringProcessor(project) {

  private lateinit var myTargetModule: Module

  @VisibleForTesting
  var shouldSelectAllReferences = false
    private set

  fun setTargetModule(module: Module) {
    myTargetModule = module

    // Tune default selection behavior: it's safe to select all references only if the target module is depended on (downstream dependency).
    shouldSelectAllReferences = myRoots.all { root ->
      AndroidFacet.getInstance(root)?.let { facet ->
        collectModulesClosure(facet.module, Sets.newHashSet()).contains(myTargetModule)
      } ?: true
    }
  }

  val classesCount: Int get() = myClasses.size

  val resourcesCount: Int get() = myResources.size

  override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
    object : UsageViewDescriptor {
      override fun getElements(): Array<PsiElement?> = (usages.map { it.element }).toTypedArray()
      override fun getProcessedElementsHeader() = "Items to be moved"
      override fun getCodeReferencesText(usagesCount: Int, filesCount: Int) =
        String.format(Locale.US, "%1\$d resources in %2\$d files", usagesCount, filesCount)
    }

  override fun findUsages(): Array<UsageInfo> {
    val result = mutableListOf<UsageInfo>()

    myClasses.forEach { result.add(UsageInfo(it)) }
    myManifestEntries.forEach { result.add(UsageInfo(it)) }

    for (resource in myResources) {
      val psiFile = getItemPsiFile(myProject, resource)
      if (getFolderType(psiFile) == ResourceFolderType.VALUES) {
        // This is just a value, so we won't move the entire file, just its corresponding XmlTag
        getItemTag(myProject, resource)?.let { result.add(ResourceXmlUsageInfo(it, resource)) }
      }
      else if (psiFile is PsiBinaryFile) {
        // The usage view doesn't handle binaries at all. Work around this (for example,
        // the UsageInfo class asserts in the constructor if the element doesn't have
        // a text range.)

        // The usage view doesn't handle binaries at all. Work around this (for example,
        // the UsageInfo class asserts in the constructor if the element doesn't have
        // a text range.)
        val smartPointerManager = SmartPointerManager.getInstance(myProject)
        val smartPointer = smartPointerManager.createSmartPsiElementPointer<PsiElement>(psiFile)
        val smartFileRange = smartPointerManager.createSmartPsiFileRangePointer(psiFile, TextRange.EMPTY_RANGE)
        result.add(object : ResourceXmlUsageInfo(smartPointer, smartFileRange, resource) {
          override fun isValid(): Boolean = true
          override fun getSegment(): Segment? = null
        })
      }
      else if (psiFile != null) {
        result.add(ResourceXmlUsageInfo(psiFile, resource))
      }
    }

    return UsageViewUtil.removeDuplicatedUsages(result.toTypedArray())
  }

  override fun previewRefactoring(usages: Array<UsageInfo>) {
    val previewDialog = PreviewDialog(myProject, referenceGraph, usages, shouldSelectAllReferences)
    if (previewDialog.showAndGet()) {
      TransactionGuard.getInstance().submitTransactionAndWait {
        execute(previewDialog.selectedUsages)
      }
    }
  }

  override fun performRefactoring(usages: Array<UsageInfo>) {
    val facet = AndroidFacet.getInstance(myTargetModule)!! // We know this has to be an Android module

    val sources: IdeaSourceProvider = SourceProviderManager.getInstance(facet).sources
    val sourceFolders = Iterables.concat(sources.javaDirectories, sources.kotlinDirectories)
    val javaTargetDir = Iterables.getFirst(sourceFolders, null)

    val resDir = ResourceFolderManager.getInstance(facet).folders[0]
    val repo = ResourceFolderRegistry.getInstance(myProject)[facet, resDir]

    val touchedXmlFiles = HashSet<XmlFile>()

    for (usage in usages) {
      val element = usage.element

      if (usage is ResourceXmlUsageInfo) {
        val resource = usage.resourceItem

        if (element is PsiFile) {
          getOrCreateTargetDirectory(repo, resource)?.let { targetDir ->
            targetDir.findFile(element.name) ?: MoveFilesOrDirectoriesUtil.doMoveFile(element, targetDir)
          }
        }
        else if (element is XmlTag) {
          // We only move stuff if we can find the destination resource file
          (getOrCreateTargetValueFile(repo, resource) as XmlFile?)?.let { resourceFile ->
            resourceFile.rootTag?.let { rootTag ->
              if (TAG_RESOURCES == rootTag.name) {
                rootTag.addSubTag(element.copy() as XmlTag, false)
                element.delete()
                touchedXmlFiles.add(resourceFile)
              }
            }
          }
        }
      }
      else if (element is XmlTag) { // This has to be a manifest entry
        (getOrCreateTargetManifestFile(facet) as XmlFile?)?.let {
          it.acceptChildren(object : XmlRecursiveElementWalkingVisitor() {
            override fun visitXmlTag(tag: XmlTag) {
              var applicationTag: XmlTag? = null
              if (tag.name == TAG_MANIFEST) {
                for (child in tag.children) {
                  if (child is XmlTag && child.name == TAG_APPLICATION) {
                    applicationTag = child
                    applicationTag.addSubTag(element.copy() as XmlTag, false)
                    element.delete()
                    break
                  }
                }
                if (applicationTag == null) { // We need to create one; this happens with manifests created by the new module wizard.
                  applicationTag = XmlElementFactory.getInstance(myProject).createTagFromText("<$TAG_APPLICATION/>")
                  applicationTag.addSubTag(element.copy() as XmlTag, false)
                  element.delete()
                  tag.addSubTag(applicationTag, true)
                }
                touchedXmlFiles.add(it)
              }
              else {
                super.visitXmlTag(tag)
              }
            }
          })
        }
      }
      else if (element is PsiClass) {
        val packageName = (element.containingFile as PsiJavaFile).packageName

        MoveClassesOrPackagesUtil.doMoveClass(
          element,
          CommonJavaRefactoringUtil
            .createPackageDirectoryInSourceRoot(PackageWrapper(PsiManager.getInstance(myProject), packageName), javaTargetDir!!),
          true)
      }
    }

    // Reformat the XML files we edited via PSI operations.
    for (touchedFile in touchedXmlFiles) {
      CodeStyleManager.getInstance(myProject).reformat(touchedFile)
    }
  }

  private fun getOrCreateTargetDirectory(base: ResourceFolderRepository, resourceItem: ResourceItem): PsiDirectory? {
    val manager = PsiManager.getInstance(myProject)
    resourceItem.source?.let { itemFile ->
      ResourceFolderType.getFolderType(itemFile.parentFileName!!)?.let { folderType ->
        try {
          return manager.findDirectory(
            VfsUtil.createDirectoryIfMissing(base.resourceDir, resourceItem.configuration.getFolderName(folderType)))
        }
        catch (ex: Exception) {
          LOGGER.debug(ex)
        }
      }
    }
    LOGGER.warn("Couldn't determine target folder for resource $resourceItem")
    return null
  }

  private fun getOrCreateTargetValueFile(base: ResourceFolderRepository, resourceItem: ResourceItem): PsiFile? {
    val itemFile = resourceItem.source
    if (itemFile != null) {
      try {
        val name = itemFile.fileName
        val dir = getOrCreateTargetDirectory(base, resourceItem)
        if (dir != null) {
          val result = dir.findFile(name)
          return result
                 ?: AndroidFileTemplateProvider
                   .createFromTemplate(AndroidFileTemplateProvider.VALUE_RESOURCE_FILE_TEMPLATE, name, dir) as PsiFile

          // TODO: How do we make sure the custom templates are applied for new files (license, author, etc) ?
        }
      }
      catch (ex: Exception) {
        LOGGER.debug(ex)
      }
    }
    LOGGER.warn("Couldn't determine target file for resource $resourceItem")
    return null
  }

  private fun getOrCreateTargetManifestFile(facet: AndroidFacet): PsiFile? {
    if (facet.isDisposed) return null
    val manager = PsiManager.getInstance(myProject)
    Iterables.getFirst(SourceProviderManager.getInstance(facet).sources.manifestFileUrls, null)?.let { manifestUrl ->
      VirtualFileManager.getInstance().findFileByUrl(manifestUrl)?.let { manifestFile -> return manager.findFile(manifestFile) }
      VfsUtil.getParentDir(manifestUrl)?.let { parentUrl ->
        VirtualFileManager.getInstance().findFileByUrl(parentUrl)?.let { parentDir ->
          manager.findDirectory(parentDir)?.let { psiParentDir ->
            try {
              return AndroidFileTemplateProvider
                .createFromTemplate(AndroidFileTemplateProvider.ANDROID_MANIFEST_TEMPLATE, SdkConstants.FN_ANDROID_MANIFEST_XML,
                                    psiParentDir) as PsiFile
            }
            catch (ex: Exception) {
              LOGGER.debug(ex)
            }
          }
        }
      }
    }
    LOGGER.warn("Couldn't determine manifest file for module $myTargetModule")
    return null
  }

  override fun getCommandName(): String =
    "Moving ${RefactoringUIUtil.calculatePsiElementDescriptionList(myRoots)}"

  companion object {
    val LOGGER = Logger.getInstance(AndroidModularizeProcessor::class.java)

    private fun collectModulesClosure(module: Module, result: MutableSet<Module>): Set<Module> {
      if (result.add(module)) {
        ModuleRootManager.getInstance(module).dependencies.forEach { it: Module ->
          collectModulesClosure(it, result)
        }
      }
      return result
    }
  }

}

open class ResourceXmlUsageInfo : UsageInfo {
  val resourceItem: ResourceItem

  constructor (element: PsiElement, resourceItem: ResourceItem) : super(element) {
    this.resourceItem = resourceItem
  }

  constructor (smartPointer: SmartPsiElementPointer<*>,
               psiFileRange: SmartPsiFileRange,
               resourceItem: ResourceItem) : super(smartPointer, psiFileRange, false, false) {
    this.resourceItem = resourceItem
  }
}

class PreviewDialog(project: Project?,
                    graph: AndroidCodeAndResourcesGraph,
                    infos: Array<UsageInfo>,
                    shouldSelectAllReferences: Boolean) : DialogWrapper(project, true) {

  private val myPanel = AndroidModularizePreviewPanel(graph, infos, shouldSelectAllReferences)

  init {
    title = "Modularize: Preview Classes and Resources to Be Moved"
    init()
  }

  override fun createCenterPanel(): JComponent = myPanel.panel

  val selectedUsages: Array<UsageInfo> get() = myPanel.selectedUsages
}