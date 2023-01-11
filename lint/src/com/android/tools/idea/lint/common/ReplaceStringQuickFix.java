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
import static kotlin.text.StringsKt.removePrefix;
import static kotlin.text.StringsKt.removeSuffix;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.DocumentUtil;
import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.classes.KtLightClass;
import org.jetbrains.kotlin.asJava.elements.KtLightField;
import org.jetbrains.kotlin.asJava.elements.KtLightMethod;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.caches.resolve.util.JavaResolutionUtils;
import org.jetbrains.kotlin.idea.core.PsiModificationUtilsKt;
import org.jetbrains.kotlin.idea.core.ShortenReferences;
import org.jetbrains.kotlin.idea.util.ImportInsertHelper;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.kotlin.psi.KtImportList;

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
        if (endOffset > startOffset) {
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
    return super.getFamilyName();
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
    Document document = documentManager.getDocument(file);
    if (document != null) {
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      editBefore(document);
      TextRange range = getRange(startElement, endElement, true);
      if (range != null) {
        String newValue = getNewValue();
        if (newValue == null) {
          newValue = "";
        }
        if (whitespaceOnly(newValue)) {
          // If we're replacing a text segment with just whitespace,
          // and the line begins and ends with whitespace after making
          // the adjustment, delete the whole line
          range = includeFullLineIfOnlySpace(document, range);
        }
        int startOffset = range.getStartOffset();
        int endOffset = range.getEndOffset();
        document.replaceString(startOffset, endOffset, newValue);
        endOffset = startOffset + newValue.length();
        editAfter(document);

        documentManager.commitDocument(document);
        PsiElement element = file.findElementAt(startOffset);
        if (element == null) {
          return;
        }
        startOffset = element.getTextOffset();
        PsiElement end = file.findElementAt(endOffset);

        SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
        SmartPsiElementPointer<PsiElement> elementPointer = pointerManager.createSmartPsiElementPointer(element);
        SmartPsiElementPointer<PsiElement> endPointer = end != null ? pointerManager.createSmartPsiElementPointer(end) : null;

        if (myImports != null && !myImports.isEmpty()) {
          if (file instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile)file;
            for (String symbol : myImports) {
              if (alreadyImported(javaFile, symbol)) {
                continue;
              }
              PsiElement importSymbol = findSymbol(symbol, javaFile);
              if (importSymbol instanceof PsiClass) {
                ImportHelper importHelper = new ImportHelper(JavaCodeStyleSettings.getInstance(file));
                importHelper.addImport(javaFile, (PsiClass)importSymbol);
              } else if (importSymbol instanceof PsiMember) {
                PsiMember member = (PsiMember)importSymbol;
                AddSingleMemberStaticImportAction.bindAllClassRefs(file, member, member.getName(), member.getContainingClass());
              }
            }
          } else if (file instanceof KtFile) {
            KtFile ktFile = (KtFile)file;
            for (String symbol : myImports) {
              if (alreadyImported(ktFile, symbol)) {
                continue;
              }

              PsiElement importSymbol = findSymbol(symbol, ktFile);
              DeclarationDescriptor descriptor = null;
              if (importSymbol instanceof KtDeclaration) {
                descriptor = PsiModificationUtilsKt.toDescriptor((KtDeclaration)importSymbol);
              } else if (importSymbol instanceof PsiClass) {
                descriptor = JavaResolutionUtils.getJavaClassDescriptor((PsiClass)importSymbol);
              } else if (importSymbol instanceof PsiMember) {
                descriptor = JavaResolutionUtils.getJavaMemberDescriptor((PsiMember)importSymbol);
              }

              if (descriptor != null) {
                ImportInsertHelper.getInstance(project).importDescriptor(ktFile, descriptor, false);
              }
            }
          }
        }

        if (myShortenNames || myFormat) {
          element = elementPointer.getElement();
          if (element != null) {
            end = endPointer != null ? endPointer.getElement() : null;
            PsiElement parent = end != null ? PsiTreeUtil.findCommonParent(element.getParent(), end) : element.getParent();
            if (parent == null) {
              parent = element.getParent();
            }
            if (myShortenNames) {
              if (element.getLanguage() == JavaLanguage.INSTANCE) {
                parent = JavaCodeStyleManager.getInstance(project).shortenClassReferences(parent);
              }
              else if (element.getLanguage() == KotlinLanguage.INSTANCE && parent instanceof KtElement) {
                parent = ShortenReferences.DEFAULT.process((KtElement)parent);
              }
              else {
                parent = null;
              }
            }

            if (myFormat) {
              CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
              if (parent != null) {
                codeStyleManager.reformat(parent);
              }
              else {
                codeStyleManager.reformatRange(element, startOffset, endOffset);
              }
            }
          }
        }

        if (mySelectPattern != null && context instanceof AndroidQuickfixContexts.EditorContext) {
          Pattern pattern = Pattern.compile(mySelectPattern);
          Matcher matcher = pattern.matcher(document.getText());
          if (matcher.find(startOffset)) {
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
            Editor editor = ((AndroidQuickfixContexts.EditorContext)context).getEditor();
            editor.getSelectionModel().setSelection(selectStart, selectEnd);
          }
        }
      }
    }
  }

  private static PsiElement findSymbol(String symbol, PsiFile file) {
    Project project = file.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    PsiClass cls = facade.findClass(symbol, scope);
    if (cls != null) {
      if (cls instanceof KtLightClass) {
        return ((KtLightClass)cls).getKotlinOrigin();
      }
      return cls;
    }
    else {
      // Field or method?
      int index = symbol.lastIndexOf('.');
      String className = symbol.substring(0, index);
      String name = symbol.substring(index + 1);
      cls = facade.findClass(className, scope);
      if (cls != null) {
        return findSymbolMember(cls, name);
      } else {
        // Extension functions where you didn't specify the class name? There's some potential
        // ambiguity here so prefer declaring using the containing class name.
        PsiPackage pkg = facade.findPackage(className);
        if (pkg != null) {
          for (PsiClass pkgCls : pkg.getClasses()) {
            PsiElement member = findSymbolMember(pkgCls, name);
            if (member != null) {
              return member;
            }
          }
        }
      }
    }
    Logger.getInstance(ReplaceStringQuickFix.class).warn("Couldn't find and import " + symbol);
    return null;
  }

  @Nullable
  private static PsiElement findSymbolMember(@NonNull PsiClass cls, @NonNull String name) {
    // When we have a reference like foo.Bar.baz, it's ambiguous where we're
    // referring to a method named "baz" or a field named "baz". We'll look
    // for both and assume it's referring to the method. As far as imports go
    // you cannot distinguish between these anyway.
    PsiMethod[] methods = cls.findMethodsByName(name, true);
    if (methods.length > 0) {
      PsiMethod method = methods[0];
      if (method instanceof KtLightMethod) {
        return ((KtLightMethod)method).getKotlinOrigin();
      }
      return method;
    }
    else {
      PsiField field = cls.findFieldByName(name, false);
      if (field != null) {
        if (field instanceof KtLightField) {
          return ((KtLightField)field).getKotlinOrigin();
        }
        return field;
      }
    }
    return null;
  }

  private static boolean alreadyImported(@NonNull PsiJavaFile javaFile, @NonNull String symbol) {
    // Already imported? ImportHelper has a utility method, but it doesn't seem to be working
    // (and there isn't a good one for static imports) so just doing it the simple way here:
    PsiImportList importList = javaFile.getImportList();
    if (importList != null) {
      for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
        if (statement instanceof PsiImportStaticStatement) {
          PsiImportStaticStatement s = (PsiImportStaticStatement)statement;
          PsiClass aClass = s.resolveTargetClass();
          if (aClass == null) {
            String text = statement.getText();
            if (!text.contains(symbol)) {
              continue;
            }
            // Drop "import static" prefix and ";", with flexible handling of unnecessary whitespace tokens
            String t = removeSuffix(removePrefix(removePrefix(text, "import").trim(), "static").trim(), ";").trim();
            if (t.equals(symbol)) {
              return true;
            }
            continue;
          }
          String referenceName = s.getReferenceName();
          if (symbol.equals(aClass.getQualifiedName() + "." + referenceName)) {
            return true;
          }
        } else if (statement instanceof PsiImportStatement) {
          if (symbol.equals(((PsiImportStatement)statement).getQualifiedName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean alreadyImported(@NonNull KtFile ktFile, @NonNull String symbol) {
    KtImportList importList = ktFile.getImportList();
    if (importList != null) {
      for (KtImportDirective statement : importList.getImports()) {
        FqName fqName = statement.getImportedFqName();
        if (fqName == null) {
          continue;
        }
        String imported = fqName.asString();
        if (symbol.equals(imported)) {
          return true;
        }
      }
    }
    return false;
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
