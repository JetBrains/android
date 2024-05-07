/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:Suppress("UnstableApiUsage")

package com.android.tools.idea.lint.common

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintFixPerformer
import com.android.tools.lint.client.api.LintFixPerformer.PendingEdit
import com.android.tools.lint.client.api.LintFixPerformer.PendingEditFile
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.DataMap
import com.android.tools.lint.detector.api.LintFix.LintFixGroup
import com.android.tools.lint.detector.api.LintFix.ShowUrl
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.copySafe
import com.android.utils.PositionXmlParser
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.toArray
import java.io.IOException
import java.util.Collections
import javax.xml.parsers.ParserConfigurationException
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.ImportPath
import org.w3c.dom.Document
import org.xml.sax.SAXException

/**
 * IDE-side fix performer; delegates most work to the lint performer to create all the editing
 * operations, but performs the edits on IDE documents instead of directly on files, and taps into
 * IDE facilities like reference shortening and code formatting using the user's preferred code
 * style.
 */
class LintIdeFixPerformer(client: LintClient, private val project: Project) :
  LintFixPerformer(client) {

  override fun log(severity: Severity, message: String) {
    val log = LintIdeClient.LOG
    when {
      severity.isError -> log.error(message)
      severity == Severity.WARNING -> log.warn(message)
      else -> log.info(message)
    }
  }

  override fun applyEdits(
    fileProvider: FileProvider,
    fileData: PendingEditFile,
    edits: List<PendingEdit>,
  ) {
    // In the IDE we should always be passing in a lambda when we call
    // applyEdits since here we want to pass in additional context (such as the
    // PsiElement to be operated on -- since we may use the same fix both in a
    // preview context and in the real context and so on)
    throw UnsupportedOperationException()
  }

  var context: AndroidQuickfixContexts.Context? = null
  var deltas: MutableMap<PendingEditFile, Int>? = null

  companion object {
    fun createIdeFixes(
      project: Project,
      file: PsiFile?,
      incident: Incident,
      fix: LintFix,
      isTopLevel: Boolean = true,
    ): Array<LintIdeQuickFix> {
      when (fix) {
        is LintFix.AnnotateFix -> {
          if (isTopLevel) {
            // If you're *just* applying a single annotation, use the PSI-based
            // `AnnotateQuickFix`. This directly calls IntelliJ's support for adding an
            // annotation. It's more or less identical to the built-in LintFixPerformer
            // implementation (and this is asserted by `AnnotateQuickFixTest` which
            // runs all the tests using both implementations), but possibly handles
            // some corner cases better such as applying a specific ordering to
            // the annotations. **In 100% of cases of the built-in checks, the
            // annotations are not combined with other fixes, so it will be using the
            // `AnnotateQuickFix` path.**
            return arrayOf(
              ModCommandLintQuickFix(
                AnnotateQuickFix(
                  project,
                  fix.getDisplayName(),
                  fix.getFamilyName(),
                  fix.annotation,
                  fix.replace,
                  fix.range,
                )
              )
            )
          }
        }
        is LintFixGroup -> {
          if (fix.type == LintFix.GroupType.ALTERNATIVES) {
            val fixList = mutableListOf<LintIdeQuickFix>()
            for (nested in fix.fixes) {
              Collections.addAll(fixList, *createIdeFixes(project, file, incident, nested, false))
            }
            val fixes = fixList.toArray<LintIdeQuickFix>(LintIdeQuickFix.EMPTY_ARRAY)
            if (fixes.size > 1) {
              // IntelliJ will sort fixes alphabetically -- it will NOT preserve the
              // order fixes are registered in. The only way to work around this is to
              // arrange for the labels have the same alphabetical ordering as their
              // priorities, which can be tricky.
              //
              // However, IntelliJ later introduced a PriorityAction interface.
              // Unfortunately, it is NOT a general priority where we can just assign
              // priorities as ordinals; instead, there are a few enums. So we'll use
              // these priorities to at a minimum force the first (presumably default)
              // option to have the highest priority.
              //
              // We don't use the full complement of priorities because we don't want
              // these actions to be sorted lower than some of the fallback/default
              // intention actions, such as suppression, and it turns out these actions
              // are already using HIGH priority so the best we can do is to boost one of
              // them up to TOP.
              fixes[0].priority = PriorityAction.Priority.TOP
            }
            return fixes
          }

          return arrayOf(ModCommandLintQuickFix(LintIdeFixPerformerFix(project, incident, fix)))
        }
        is ShowUrl -> {
          return arrayOf(ShowUrlQuickFix(fix))
        }
        is DataMap -> {
          error(
            "A DataMap fix should only be used with inspections that override `getQuickFixes` (issue id: ${incident.issue.id})"
          )
        }
      }

      return arrayOf(ModCommandLintQuickFix(LintIdeFixPerformerFix(project, incident, fix)))
    }

    fun addJavaImports(javaFile: PsiJavaFile, imports: List<String>) {
      val importList = javaFile.importList ?: return // Always non-null in well-formed Java sources.
      val project = javaFile.project
      val psiFacade = JavaPsiFacade.getInstance(project)
      val psiSearchScope = GlobalSearchScope.allScope(project)
      val psiFactory = psiFacade.elementFactory
      for (symbol in imports) {
        if (alreadyImported(importList, symbol)) {
          continue
        }
        val cls = psiFacade.findClass(symbol, psiSearchScope)
        if (cls != null) {
          // Normal class import.
          importList.add(psiFactory.createImportStatement(cls))
        } else {
          // Static member import.
          val lastDot = symbol.lastIndexOf('.')
          if (lastDot == -1) continue
          val containingClassName = symbol.substring(0, lastDot)
          val containingClass = psiFacade.findClass(containingClassName, psiSearchScope)
          if (containingClass != null) {
            val memberName = symbol.substring(lastDot + 1)
            importList.add(psiFactory.createImportStaticStatement(containingClass, memberName))
          }
        }
      }
    }

    fun addKotlinImports(ktFile: KtFile, imports: List<String>) {
      val importList = ktFile.importList ?: return // Always non-null in well-formed Kotlin sources.
      val psiFactory = KtPsiFactory(ktFile.project, markGenerated = true)
      for (symbol in imports) {
        if (alreadyImported(importList, symbol)) {
          continue
        }
        val importDirective = psiFactory.createImportDirective(ImportPath(FqName(symbol), false))
        importList.add(importDirective)
      }
    }

    private fun alreadyImported(imports: PsiImportList, candidate: String): Boolean =
      imports.allImportStatements.any { candidate == it.importReference?.qualifiedName }

    private fun alreadyImported(imports: KtImportList, candidate: String): Boolean =
      imports.imports.any { candidate == it.importedFqName?.asString() }

    fun shortenJavaReferencesInRange(file: PsiFile, range: TextRange) {
      // We'd really prefer to use
      // JavaCodeStyleManager.shortenClassReferences(file, startOffset,
      // startOffset), but unfortunately it hard-codes the 'incompleteCode' flag
      // to true, which breaks reference shortening for static method calls. So
      // instead we visit in-range PSI elements ourselves.

      // Find the parent PSI element covering the entire range.

      val startPsi = file.findElementAt(range.startOffset)
      val endPsi = file.findElementAt(range.endOffset - 1)
      if (startPsi == null || endPsi == null) return
      val commonParent = PsiTreeUtil.findCommonParent(startPsi, endPsi) ?: return

      // Process constituent PSI elements inside the target range.
      val psiInRange = ArrayList<PsiElement>()
      collectDisjointDescendantsCoveringRange(commonParent, range, psiInRange)
      val javaCodeStyleManager = JavaCodeStyleManager.getInstance(file.project)
      for (psiElement in psiInRange) {
        if (psiElement.isValid) {
          javaCodeStyleManager.shortenClassReferences(psiElement)
        }
      }
    }

    private fun collectDisjointDescendantsCoveringRange(
      parent: PsiElement,
      fileRange: TextRange,
      out: MutableList<PsiElement>,
    ) {
      var child = parent.firstChild
      while (child != null) {
        val childRange = child.textRange
        if (childRange == null || childRange.isEmpty) {
          child = child.nextSibling
          continue
        }
        if (fileRange.contains(childRange)) {
          out.add(child)
        } else if (fileRange.intersectsStrict(childRange)) {
          collectDisjointDescendantsCoveringRange(child, fileRange, out)
        }
        child = child.nextSibling
      }
    }

    fun getRangePointer(project: Project, range: Location?): SmartPsiFileRange? {
      if (range != null) {
        val start = range.start
        val end = range.end
        if (start != null && end != null) {
          val psiFile = range.file.toVirtualFile()?.toPsiFile(project)
          if (psiFile != null) {
            val manager = SmartPointerManager.getInstance(project)
            val textRange = TextRange.create(start.offset, end.offset)
            return manager.createSmartPsiFileRangePointer(psiFile, textRange)
          }
        }
      }
      return null
    }
  }
}

open class LintIdeReadOnlyFileProvider(private val project: Project) :
  LintFixPerformer.FileProvider {
  @RequiresReadLock
  override fun getFileContents(file: PendingEditFile): String {
    return file.file.toPsiFile(project)?.text ?: ""
  }

  override fun getXmlDocument(file: PendingEditFile): Document? {
    try {
      return PositionXmlParser.parse(getFileContents(file))
    } catch (e: Exception) {
      when (e) {
        is ParserConfigurationException,
        is SAXException,
        is IOException -> logger<LintIdeClient>().warn("Ignoring $file: Failed to parse XML: $e", e)
        else -> throw e
      }
    }
    return null
  }

  override fun createBinaryFile(fileData: PendingEditFile, contents: ByteArray) {
    throw UnsupportedOperationException()
  }

  override fun deleteFile(fileData: PendingEditFile) {
    throw UnsupportedOperationException()
  }
}

@Suppress("UnstableApiUsage")
class LintIdeReadWriteFileProvider(
  private val project: Project,
  private val updater: ModPsiUpdater,
) : LintIdeReadOnlyFileProvider(project) {

  override fun createBinaryFile(fileData: PendingEditFile, contents: ByteArray) {
    val file = fileData.file

    // TODO(b/319287252): find a better way when PsiBinaryFiles are supported by ModCreateFile, e.g.
    // this should work:
    //     val psiFile =
    // updater.getWritable(file.parentFile.toPsiDirectory(project))?.createFile(file.name)!!
    //     psiFile.parent?.virtualFile?.findChild(file.name)?.setBinaryContent(contents)
    //     // No formatting or selection, but let's open it if it's an image
    //     if (fileData.open && SdkUtils.hasImageExtension(file.path)) {
    //       updater.moveCaretTo(psiFile)
    //     }
    if (!IntentionPreviewUtils.isIntentionPreviewActive()) {
      file.parentFile?.mkdirs()
      file.writeBytes(contents)
    }
  }

  override fun deleteFile(fileData: PendingEditFile) {
    updater.getWritable(fileData.file.toPsiFile(project))?.delete()
  }
}

/**
 * A [LintIdeQuickFix] implementation which uses the [LintFixPerformer] behind the scenes. When
 * first constructed, it creates a list of [edits], tracking an affected range with a smart element
 * pointer. Whenever the [isApplicable] method is called (for example by the Inspections UI
 * periodically to show whether the fix should still be enabled), it checks that the document
 * fragments are still valid at the current (possibly shifted or edited) locations, and in apply, it
 * goes and performs the edits, again possibly with shifted offsets.
 *
 * While [LintFixPerformer] operates on plain files, here we map to VirtualFiles, PsiFiles and
 * Documents first.
 */
@Suppress("UnstableApiUsage")
private class LintIdeFixPerformerFix(
  private val project: Project,
  incident: Incident,
  val fix: LintFix,
  private val name: String = fix.getDisplayName() ?: "Fix",
  private val familyName: String = fix.getFamilyName() ?: "LintIdeFix",
  private val valueOverride: ((PendingEditFile, PendingEdit) -> String?)? = null,
) : ModCommandAction {

  private val performer = LintIdeFixPerformer(LintIdeSupport.get().createClient(project), project)
  private val edits = LinkedHashMap<PendingEditFile, Pair<SmartPsiFileRange, CharSequence>?>()

  val incident = incident.copySafe()

  init {
    // Collect edits
    val affectedFiles =
      performer.registerFixes(incident, listOf(fix), LintIdeReadOnlyFileProvider(project))
    val manager = SmartPointerManager.getInstance(project)
    for (file in affectedFiles) {
      val psiFile = file.file.toVirtualFile()?.toPsiFile(project)
      if (psiFile != null) {
        val textRange = file.affectedRange()
        val pointer = manager.createSmartPsiFileRangePointer(psiFile, textRange)
        val start = textRange.startOffset
        val end = textRange.endOffset
        val contents =
          PsiDocumentManager.getInstance(project)
            .getCachedDocument(psiFile)
            ?.charsSequence
            ?.subSequence(start, end) ?: psiFile.text.substring(start, end)
        edits[file] = Pair(pointer, contents)
      } else {
        // New file to be created
        edits[file] = null
      }
    }
  }

  override fun getFamilyName(): @IntentionFamilyName String = familyName

  override fun getPresentation(context: ActionContext): Presentation? {
    if (edits.isEmpty()) {
      return null
    }
    performer.deltas?.clear()
    for (entry in edits.entries) {
      val file = entry.key
      val pair: Pair<SmartPsiFileRange, CharSequence>? = entry.value
      val (pointer, contents) = pair ?: continue
      val psiFile = pointer.containingFile ?: return null
      val range = pointer.range ?: return null
      val start = range.startOffset
      val end = range.endOffset

      val delta = start - file.affectedRange().startOffset
      if (delta != 0) {
        val deltas =
          performer.deltas ?: mutableMapOf<PendingEditFile, Int>().also { performer.deltas = it }
        deltas[file] = delta
      }

      val currentContents =
        PsiDocumentManager.getInstance(project)
          .getCachedDocument(psiFile)
          ?.charsSequence
          ?.subSequence(start, end) ?: psiFile.text.substring(start, end)
      if (currentContents != contents) {
        return null
      }
    }

    return Presentation.of(name)
  }

  @Suppress("UnstableApiUsage")
  override fun perform(context: ActionContext): ModCommand {
    return ModCommand.psiUpdate(context) { updater ->
      val fileProvider = LintIdeReadWriteFileProvider(project, updater)
      performer.applyEdits(fileProvider, edits.keys.toList()) { fileData, edits ->
        applyEdits(fileData, edits, updater)
      }
    }
  }

  private fun applyEdits(
    fileData: PendingEditFile,
    edits: List<PendingEdit>,
    updater: ModPsiUpdater,
  ) {
    if (edits.isEmpty()) {
      return
    }

    val originalFile = fileData.file

    var psiFile = originalFile.toPsiFile(project)
    if (psiFile == null) {
      if (fileData.createText) {
        psiFile =
          updater
            .getWritable(originalFile.parentFile.toPsiDirectory(project))
            ?.createFile(originalFile.name)
      }
    } else {
      psiFile = updater.getWritable(psiFile)
      if (fileData.createText) {
        psiFile.fileDocument.setText("")
      }
    }

    val file = psiFile!!

    val delta = performer.deltas?.get(fileData) ?: 0
    val project = file.project
    val documentManager = PsiDocumentManager.getInstance(project)
    val pointerManager = SmartPointerManager.getInstance(project)
    val modifiedRanges = mutableListOf<SmartPsiFileRange>()

    // First selection in the source (edits are sorted in reverse order so pick the last one)
    val firstSelection = edits.lastOrNull { it.selectStart != -1 }
    var selectedRange: RangeMarker? = null

    val document = file.fileDocument
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    val originalLength = document.textLength
    for (edit in edits) {
      val replacement = valueOverride?.invoke(fileData, edit) ?: edit.replacement
      val start = edit.startOffset + delta
      val end = edit.endOffset + delta
      document.replaceString(start, end, replacement)
      modifiedRanges.add(
        pointerManager.createSmartPsiFileRangePointer(
          file,
          TextRange.create(start, start + replacement.length),
        )
      )

      if (firstSelection == edit) {
        // Use a smart RangeMarker to survive document changes such as reformatting etc. The
        // ModCommand machinery will batch all edits and apply navigation (opening a file,
        // selecting or highlighting text) after them, e.g. there will be a ModCompositeCommand
        // consisting of a ModCreateFile (or Mod UpdateFileText), then a ModNavigate.
        assert(firstSelection.selectStart <= firstSelection.selectEnd)
        selectedRange =
          document.createRangeMarker(
            start + firstSelection.selectStart,
            start + firstSelection.selectEnd,
          )
      }
    }
    documentManager.commitDocument(document)

    val replaceStart = edits.last().startOffset
    val replaceEnd = document.textLength - (originalLength - edits.first().endOffset)
    // In order to apply multiple transformations in sequence, we use a smart range to keep
    // track of where we are in the file.
    val resultTextRange = TextRange.from(replaceStart, replaceEnd - replaceStart)
    val resultSmartRange = pointerManager.createSmartPsiFileRangePointer(file, resultTextRange)

    val imports = fileData.imports
    if (!imports.isNullOrEmpty()) {
      when (file) {
        is PsiJavaFile -> LintIdeFixPerformer.addJavaImports(file, imports)
        is KtFile -> LintIdeFixPerformer.addKotlinImports(file, imports)
      }
    }

    if (fileData.shortenReferences) {
      val range = resultSmartRange.psiRange
      if (range != null) {
        val textRange = TextRange(range.startOffset, range.endOffset)
        when (file) {
          is PsiJavaFile -> LintIdeFixPerformer.shortenJavaReferencesInRange(file, textRange)
          is KtFile -> ShortenReferencesFacility.getInstance().shorten(file, textRange)
        }
      }
    }

    if (fileData.reformat) {
      val codeStyleManager = CodeStyleManager.getInstance(project)
      val range = resultSmartRange.psiRange
      if (range != null) {
        codeStyleManager.reformatRange(file, range.startOffset, range.endOffset)
      }
    }

    // Finish with navigation actions: open the file in the current editor, and apply the selection,
    // if available.
    if (fileData.open || (fileData.createText && firstSelection != null)) {
      updater.moveCaretTo(psiFile)
    }
    if (selectedRange != null) {
      val selectionStartOffset = selectedRange.startOffset
      val selectionEndOffset = selectedRange.endOffset
      if (selectionEndOffset <= file.viewProvider.document.textLength) {
        updater.select(TextRange.create(selectionStartOffset, selectionEndOffset))
      }
    }
  }
}

fun LintFix.toIdeFix(project: Project, incident: Incident): LintIdeQuickFix {
  return LintIdeFixPerformer.createIdeFixes(project, null, incident, this).single()
}

fun LintFix.toIdeFix(file: PsiFile): LintIdeQuickFix {
  val location =
    Location.create(
      VfsUtilCore.virtualToIoFile(file.virtualFile),
      DefaultPosition(-1, -1, 0),
      DefaultPosition(-1, -1, file.textLength),
    )
  val incident = Incident().location(location)
  return toIdeFix(file.project, incident)
}

fun LintFix.toIdeFix(
  project: Project,
  incident: Incident,
  valueOverride: ((PendingEditFile, PendingEdit) -> String?),
): LintIdeQuickFix {
  if (this !is LintFix.ReplaceString) {
    error("Cannot only override values on string replacements")
  }
  return ModCommandLintQuickFix(
    LintIdeFixPerformerFix(project, incident, this, valueOverride = valueOverride)
  )
}
