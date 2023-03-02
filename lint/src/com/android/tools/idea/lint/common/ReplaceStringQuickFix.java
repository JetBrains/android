/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_BEGINNING;
import static com.android.tools.lint.detector.api.LintFix.ReplaceString.INSERT_END;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportList;
import org.jetbrains.kotlin.psi.KtPsiFactoryKt;
import org.jetbrains.kotlin.resolve.ImportPath;

/**
 * Generic lint quickfix which replaces text somewhere in the range from [startElement,endElement] by matching
 * a regular expression and replacing the first group with a specified value. The regular expression can be null
 * in which case the entire text range is replaced (this is used where lint's error range corresponds exactly to
 * the portion we want to replace.)
 */
public class ReplaceStringQuickFix extends DefaultLintQuickFix {
  @RegExp private final String myRegexp;
  private final String myNewValue;
  private boolean myShortenNames;
  private boolean myFormat;
  private List<String> myImports;
  private SmartPsiFileRange myRange;
  private String myExpandedNewValue;
  private String mySelectPattern;

  /**
   * Creates a new lint quickfix which can replace string contents at the given PSI element
   *
   * @param name       the quickfix description, which is optional (if not specified, it will be replaced with X)
   * @param familyName the name to use for this fix <b>if</b> it is safe to apply along with all other fixes of the same family name
   * @param regexp     the regular expression, or {@link ReplaceString#INSERT_BEGINNING} or {@link ReplaceString#INSERT_END}
   * @param newValue   the value to write into the document
   */
  public ReplaceStringQuickFix(@Nullable String name,
                               @Nullable String familyName,
                               @Nullable @RegExp String regexp,
                               @NotNull String newValue) {
    super(name, familyName);
    myNewValue = newValue;
    if (regexp != null && regexp.indexOf('(') == -1 && !regexp.equals(INSERT_BEGINNING) && !regexp.equals(INSERT_END)) {
      regexp = "(" + Pattern.quote(regexp) + ")";
    }
    myRegexp = regexp;
  }

  @NonNull
  public static DefaultLintQuickFix create(@Nullable PsiFile file, @NonNull ReplaceString lintFix) {
    @RegExp String regexp;
    @RegExp String pattern = lintFix.getOldPattern();
    String oldString = lintFix.getOldString();
    if (pattern != null) {
      regexp = pattern;
    }
    else if (oldString != null) {
      if (INSERT_BEGINNING.equals(oldString) || INSERT_END.equals(oldString)) {
        //noinspection LanguageMismatch
        regexp = oldString;
      }
      else {
        regexp = "(" + Pattern.quote(oldString) + ")";
      }
    }
    else {
      regexp = null;
    }
    String displayName = lintFix.getDisplayName();
    String familyName = lintFix.getFamilyName();
    String replacement = lintFix.getReplacement();
    String selectPattern = lintFix.getSelectPattern();
    Location range = lintFix.getRange();
    ReplaceStringQuickFix fix = new ReplaceStringQuickFix(displayName, familyName, regexp, replacement);
    fix.myShortenNames = lintFix.getShortenNames();
    fix.myFormat = lintFix.getReformat();
    fix.mySelectPattern = selectPattern;
    fix.myImports = lintFix.getImports();
    if (range != null && file != null) {
      PsiFile rangeFile = file;
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(range.getFile(), false);
      if (virtualFile != null) {
        PsiFile psiFile = file.getManager().findFile(virtualFile);
        if (psiFile != null) {
          rangeFile = psiFile;
        }
      } else {
        // Creating a new file
        // Can't use the normal replace action, which works on top of PSI and virtual
        // files (which we don't have here). Creation is simple so we use a custom
        // quickfix instead.
        File path = range.getFile();
        return new CreateFileQuickFix(path, replacement, null,
                                      null, false, lintFix.getDisplayName(), familyName);
      }
      Position start = range.getStart();
      Position end = range.getEnd();
      if (start != null && end != null) {
        SmartPointerManager manager = SmartPointerManager.getInstance(rangeFile.getProject());
        int startOffset = start.getOffset();
        int endOffset = end.getOffset();
        if (endOffset >= startOffset) {
          TextRange textRange = TextRange.create(startOffset, endOffset);
          fix.myRange = manager.createSmartPsiFileRangePointer(rangeFile, textRange);
        }
      }
    }
    return fix;
  }

  /**
   * Sets whether the replace operation should attempt to shorten class names after the replacement
   */
  public ReplaceStringQuickFix setShortenNames(boolean shortenNames) {
    myShortenNames = shortenNames;
    return this;
  }

  /**
   * Sets whether the replace operation should attempt to shorten class names after the replacement
   */
  public ReplaceStringQuickFix setFormat(boolean format) {
    myFormat = format;
    return this;
  }

  /**
   * Sets a pattern to select; if it contains parentheses, group(1) will be selected.
   * To just set the caret, use an empty group.
   */
  public ReplaceStringQuickFix setSelectPattern(String selectPattern) {
    mySelectPattern = selectPattern;
    return this;
  }

  /**
   * Sets a range override to use when searching for replacement text
   */
  public void setRange(SmartPsiFileRange range) {
    myRange = range;
  }

  @Nullable
  @Override
  public SmartPsiFileRange getRange() {
    return myRange;
  }

  @NotNull
  @Override
  public String getName() {
    if (myName == null) {
      if (myNewValue.isEmpty()) {
        return "Delete";
      }
      return "Replace with " + myNewValue;
    }
    return myName;
  }

  @Nullable
  @Override
  public String getFamilyName() {
    return myFamilyName;
  }

  @Nullable
  protected String getNewValue() {
    return myExpandedNewValue != null ? myExpandedNewValue : myNewValue;
  }

  @SuppressWarnings("EmptyMethod")
  protected void editBefore(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    PsiFile file = startElement.getContainingFile();
    if (myRange != null) {
      file = myRange.getContainingFile();
      if (file == null) {
        return;
      }
    }
    Project project = startElement.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);

    Document document = context.getDocument(file);
    if (IntentionPreviewUtils.isIntentionPreviewActive() && context instanceof AndroidQuickfixContexts.EditorContext) {
      if (((AndroidQuickfixContexts.EditorContext)context).getEditor().getDocument() != document) {
        // This is a composite fix editing other files outside of the preview; ignore those
        return;
      }
    }
    if (document != null) {
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      editBefore(document);
      TextRange replaceRange = getRange(startElement, endElement, true);
      if (replaceRange != null) {
        String newValue = getNewValue();
        if (newValue == null) {
          newValue = "";
        }
        if (whitespaceOnly(newValue)) {
          // If we're replacing a text segment with just whitespace,
          // and the line begins and ends with whitespace after making
          // the adjustment, delete the whole line
          replaceRange = includeFullLineIfOnlySpace(document, replaceRange);
        }
        final int replaceStart = replaceRange.getStartOffset();
        final int replaceEnd = replaceRange.getEndOffset();
        document.replaceString(replaceStart, replaceEnd, newValue);
        editAfter(document);
        documentManager.commitDocument(document);

        if (mySelectPattern != null && context instanceof AndroidQuickfixContexts.EditorContext && file.isPhysical()) {
          Pattern pattern = Pattern.compile(mySelectPattern);
          Matcher matcher = pattern.matcher(document.getText());
          if (matcher.find(replaceStart)) {
            int selectStart;
            int selectEnd;
            if (matcher.groupCount() > 0) {
              selectStart = matcher.start(1);
              selectEnd = matcher.end(1);
            }
            else {
              selectStart = matcher.start();
              selectEnd = matcher.end();
            }
            Editor editor = context.getEditor(file);
            if (editor != null) {
              // Note: the selection model uses smart ranges (RangeMarker), so it will
              // correctly adapt to the post-processing edits below.
              editor.getSelectionModel().setSelection(selectStart, selectEnd);
            }
          }
        }

        // In order to apply multiple transformations in sequence, we use a smart range to keep
        // track of where we are in the file.
        SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
        TextRange resultTextRange = TextRange.from(replaceStart, newValue.length());
        SmartPsiFileRange resultSmartRange = pointerManager.createSmartPsiFileRangePointer(file, resultTextRange);

        if (myImports != null && !myImports.isEmpty()) {
          if (file instanceof PsiJavaFile javaFile) {
            addJavaImports(javaFile, myImports);
          } else if (file instanceof KtFile ktFile) {
            addKotlinImports(ktFile, myImports);
          }
        }

        if (myShortenNames) {
          var range = resultSmartRange.getPsiRange();
          if (range != null) {
            var textRange = new TextRange(range.getStartOffset(), range.getEndOffset());
            if (file instanceof PsiJavaFile) {
              shortenJavaReferencesInRange(file, textRange);
            }
            else if (file instanceof KtFile ktFile) {
              ShortenReferencesFacility.Companion.getInstance().shorten(ktFile, textRange);
            }
          }
        }

        if (myFormat) {
          var range = resultSmartRange.getPsiRange();
          if (range != null) {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            codeStyleManager.reformatRange(file, range.getStartOffset(), range.getEndOffset());
          }
        }
      }
    }
  }

  private static void addJavaImports(PsiJavaFile javaFile, List<String> imports) {
    var importList = javaFile.getImportList();  // Always non-null in well-formed Java sources.
    if (importList == null) return;
    var project = javaFile.getProject();
    var psiFacade = JavaPsiFacade.getInstance(project);
    var psiSearchScope = GlobalSearchScope.allScope(project);
    var psiFactory = psiFacade.getElementFactory();
    for (String symbol : imports) {
      if (alreadyImported(importList, symbol)) {
        continue;
      }
      var cls = psiFacade.findClass(symbol, psiSearchScope);
      if (cls != null) {
        // Normal class import.
        importList.add(psiFactory.createImportStatement(cls));
      } else {
        // Static member import.
        int lastDot = symbol.lastIndexOf('.');
        if (lastDot == -1) continue;
        var containingClassName = symbol.substring(0, lastDot);
        var containingClass = psiFacade.findClass(containingClassName, psiSearchScope);
        if (containingClass != null) {
          var memberName = symbol.substring(lastDot + 1);
          importList.add(psiFactory.createImportStaticStatement(containingClass, memberName));
        }
      }
    }
  }

  private static void addKotlinImports(KtFile ktFile, List<String> imports) {
    var importList = ktFile.getImportList();  // Always non-null in well-formed Kotlin sources.
    if (importList == null) return;
    var psiFactory = KtPsiFactoryKt.KtPsiFactory(ktFile);
    for (String symbol : imports) {
      if (alreadyImported(importList, symbol)) {
        continue;
      }
      var importDirective = psiFactory.createImportDirective(new ImportPath(new FqName(symbol), false));
      importList.add(importDirective);
    }
  }

  private static boolean alreadyImported(PsiImportList imports, String candidate) {
    for (var importStatement : imports.getAllImportStatements()) {
      var ref = importStatement.getImportReference();
      if (ref != null && candidate.equals(ref.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean alreadyImported(KtImportList imports, String candidate) {
    for (var importDirective : imports.getImports()) {
      var ref = importDirective.getImportedFqName();
      if (ref != null && candidate.equals(ref.asString())) {
        return true;
      }
    }
    return false;
  }

  private static void shortenJavaReferencesInRange(PsiFile file, TextRange range) {
    // We'd really prefer to use JavaCodeStyleManager.shortenClassReferences(file, startOffset, startOffset),
    // but unfortunately it hard-codes the 'incompleteCode' flag to true, which breaks reference shortening
    // for static method calls. So instead we visit in-range PSI elements ourselves.

    // Find the parent PSI element covering the entire range.
    var startPsi = file.findElementAt(range.getStartOffset());
    var endPsi = file.findElementAt(range.getEndOffset() - 1);
    if (startPsi == null || endPsi == null) return;
    var commonParent = PsiTreeUtil.findCommonParent(startPsi, endPsi);
    if (commonParent == null) return;

    // Process constituent PSI elements inside the target range.
    var psiInRange = new ArrayList<PsiElement>();
    collectDisjointDescendantsCoveringRange(commonParent, range, psiInRange);
    var javaCodeStyleManager = JavaCodeStyleManager.getInstance(file.getProject());
    for (var psiElement : psiInRange) {
      if (psiElement.isValid()) {
        javaCodeStyleManager.shortenClassReferences(psiElement);
      }
    }
  }

  private static void collectDisjointDescendantsCoveringRange(PsiElement parent, TextRange fileRange, List<PsiElement> out) {
    for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
      var childRange = child.getTextRange();
      if (childRange == null || childRange.isEmpty()) {
        continue;
      }
      if (fileRange.contains(childRange)) {
        out.add(child);
      }
      else if (fileRange.intersectsStrict(childRange)) {
        collectDisjointDescendantsCoveringRange(child, fileRange, out);
      }
    }
  }

  private static boolean whitespaceOnly(@NotNull String text) {
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        return false;
      }
    }

    return true;
  }

  private static TextRange includeFullLineIfOnlySpace(@NotNull Document document, @NotNull TextRange range) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getEndOffset();

    // See if there's nothing left on the line; if so, delete the whole line
    int lineStart = DocumentUtil.getLineStartOffset(startOffset, document);
    int lineEnd = DocumentUtil.getLineEndOffset(startOffset, document);
    if (lineEnd < endOffset) {
      return range;
    }

    String prefix = document.getText(new TextRange(lineStart, startOffset));
    String suffix = document.getText(new TextRange(endOffset, lineEnd));

    if (whitespaceOnly(prefix) && whitespaceOnly(suffix)) {
      return new TextRange(lineStart, lineEnd + 1);
    }
    else {
      return range;
    }
  }

  @Nullable
  private TextRange getRange(PsiElement startElement, PsiElement endElement, boolean computeReplacement) {
    if (!startElement.isValid() || !endElement.isValid()) {
      return null;
    }
    int start;
    int end;
    if (myRange != null) {
      PsiFile file = myRange.getContainingFile();
      if (file == null) {
        return null;
      }
      Segment segment = myRange.getRange();
      if (segment != null) {
        start = segment.getStartOffset();
        end = segment.getEndOffset();
        if (myRegexp != null && !INSERT_BEGINNING.equals(myRegexp) && !INSERT_END.equals(myRegexp)) {
          startElement = file.findElementAt(start);
          endElement = file.findElementAt(end);
          if (startElement == null || endElement == null) {
            return null;
          }
        }
      } else {
        return null;
      }
    } else {
      start = startElement.getTextOffset();
      end = endElement.getTextOffset() + endElement.getTextLength();
    }
    if (myRegexp != null) {
      if (INSERT_BEGINNING.equals(myRegexp)) {
        return new TextRange(start, start);
      }
      else if (INSERT_END.equals(myRegexp)) {
        return new TextRange(end, end);
      }
      try {
        Pattern pattern = Pattern.compile(myRegexp, Pattern.MULTILINE);
        String sequence;
        PsiElement parent = PsiTreeUtil.findCommonParent(startElement, endElement);
        if (parent != null && parent.getTextRange().containsRange(start, end)) {
          TextRange parentRange = parent.getTextRange();
          int offset = parentRange.getStartOffset();
          sequence = parent.getText().substring(start - offset, end - offset);
        }
        else {
          String text = startElement.getContainingFile().getText();
          sequence = text.substring(start, end);
        }
        Matcher matcher = pattern.matcher(sequence);
        if (matcher.find()) {

          end = start;

          if (matcher.groupCount() > 0) {
            if (myRegexp.contains("target")) {
              try {
                start += matcher.start("target");
                end += matcher.end("target");
              }
              catch (IllegalArgumentException ignore) {
                // Occurrence of "target" not actually a named group
                start += matcher.start(1);
                end += matcher.end(1);
              }
            }
            else {
              start += matcher.start(1);
              end += matcher.end(1);
            }
          }
          else {
            start += matcher.start();
            end += matcher.end();
          }

          if (computeReplacement && myExpandedNewValue == null) {
            myExpandedNewValue = ReplaceString.expandBackReferences(myNewValue, matcher);
          }
        }
        else {
          return null;
        }
      }
      catch (Exception e) {
        // Invalid regexp
        Logger.getInstance(ReplaceStringQuickFix.class).warn("Invalid regular expression " + myRegexp);
        return null;
      }
    }
    return new TextRange(start, end);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return getRange(startElement, endElement, false) != null;
  }
}
