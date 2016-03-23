package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.quickfixes.AcceptWordAsCorrect;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.android.inspections.AndroidElementNotAllowedInspection;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;
import org.jetbrains.android.inspections.AndroidUnknownAttributeInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract class for creating unit tests for XML editor. Contains utility methods for performing things such as
 * <ul>
 * <li>Checking highlighting: {@link #doTestHighlighting()}</li>
 * <li>Checking results of code completion: {@link #doTestCompletionVariants(String, String...)}, {@link #toTestCompletion(String, String)}
 * and {@link #toTestFirstCompletion(String, String)}</li>
 * </ul>
 * Some of these methods use test name to choose a file from testData folder they're going to use, look out for
 * {@link #getTestName(String, boolean)} and similar methods to spot that.
 */
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
abstract class AndroidDomTest extends AndroidTestCase {
  protected final String testFolder;

  protected AndroidDomTest(boolean createManifest, String testFolder) {
    super(createManifest);
    this.testFolder = testFolder;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
    myFixture.enableInspections(AndroidDomInspection.class,
                                AndroidUnknownAttributeInspection.class,
                                AndroidElementNotAllowedInspection.class);
  }

  @Override
  protected String getResDir() {
    return "dom/res";
  }

  protected void doTestJavaCompletion(String aPackage) throws Throwable {
    final String fileName = getTestName(false) + ".java";
    final VirtualFile file = copyFileToProject(fileName, "src/" + aPackage.replace('/', '.') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + getTestName(false) + "_after.java");
  }

  protected void doTestNamespaceCompletion(boolean systemNamespace, boolean customNamespace, boolean toolsNamespace, boolean xliffNamespace)
    throws IOException {
    final VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    final List<String> variants = myFixture.getLookupElementStrings();
    assertNotNull(variants);
    final List<String> expectedVariants = new ArrayList<String>();

    if (systemNamespace) {
      expectedVariants.add(SdkConstants.ANDROID_URI);
    }
    if (customNamespace) {
      expectedVariants.add("http://schemas.android.com/apk/res/p1.p2");
    }
    if (toolsNamespace) {
      expectedVariants.add(SdkConstants.TOOLS_URI);
    }
    if (xliffNamespace) {
      expectedVariants.add(SdkConstants.XLIFF_URI);
    }
    Collections.sort(expectedVariants);
    assertEquals(expectedVariants, variants);
  }

  /**
   * Loads file, invokes code completion at &lt;caret&gt; marker and returns resulting completion variants as strings.
   */
  protected void doTestCompletionVariants(String fileName, String... variants) throws Throwable {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertNotNull(lookupElementStrings);
    UsefulTestCase.assertSameElements(lookupElementStrings, variants);
  }

  protected void doTestCompletionVariantsContains(String fileName, String... variants) throws Throwable {
    List<String> lookupElementStrings = getCompletionElements(fileName);
    assertNotNull(lookupElementStrings);
    assertContainsElements(lookupElementStrings, variants);
  }

  private List<String> getCompletionElements(String fileName) throws IOException {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    return myFixture.getLookupElementStrings();
  }

  protected void doTestHighlighting() throws Throwable {
    doTestHighlighting(getTestName(true) + ".xml");
  }

  /**
   * Creates a virtual file from {@code fileName} and calls {@code doTestHighlighting(VirtualFile virtualFile)} passing it as a parameter
   */
  protected void doTestHighlighting(String fileName) throws Throwable {
    doTestHighlighting(copyFileToProject(fileName));
  }

  /**
   * Loads a virtual file and checks whether result of highlighting correspond to XML-like markers left in it. Format of the markers is best
   * described by an example, check the usages of the function to find out.
   */
  protected void doTestHighlighting(VirtualFile virtualFile) throws Throwable {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doTestJavaHighlighting(String aPackage) throws Throwable {
    final String fileName = getTestName(false) + ".java";
    final VirtualFile virtualFile = copyFileToProject(fileName, "src/" + aPackage.replace('.', '/') + '/' + fileName);
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  protected void doTestCompletion() throws Throwable {
    doTestCompletion(true);
  }

  protected void doTestCompletion(boolean lowercaseFirstLetter) throws Throwable {
    toTestCompletion(getTestName(lowercaseFirstLetter) + ".xml", getTestName(lowercaseFirstLetter) + "_after.xml");
  }

  /**
   * Loads first file, puts caret on the &lt;caret&gt; marker, invokes code completion. If running the code completion results in returning
   * only one completion variant, chooses it to complete code at the caret.
   */
  protected void toTestCompletion(String fileBefore, String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(testFolder + '/' + fileAfter);
  }

  /**
   * Variant of {@link #toTestCompletion(String, String)} that chooses the first completion variant
   * when several possibilities are available.
   */
  protected void toTestFirstCompletion(@NotNull String fileBefore, @NotNull String fileAfter) throws Throwable {
    VirtualFile file = copyFileToProject(fileBefore);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(testFolder + '/' + fileAfter);
  }

  protected void doTestSpellcheckerQuickFixes() throws IOException {
    myFixture.enableInspections(SpellCheckingInspection.class);
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    final List<IntentionAction> fixes = highlightAndFindQuickFixes(null);
    assertEquals(2, fixes.size());
    assertInstanceOf(((QuickFixWrapper)fixes.get(0)).getFix(), RenameTo.class);
    assertInstanceOf(((QuickFixWrapper)fixes.get(1)).getFix(), AcceptWordAsCorrect.class);
  }

  /**
   * Return a destination for files to be copied by {@link #copyFileToProject(String)}
   */
  protected abstract String getPathToCopy(String testFileName);

  protected VirtualFile copyFileToProject(String path) throws IOException {
    return copyFileToProject(path, getPathToCopy(path));
  }

  protected VirtualFile copyFileToProject(String from, String to) throws IOException {
    return myFixture.copyFileToProject(testFolder + '/' + from, to);
  }

  protected void doTestAndroidPrefixCompletion(@Nullable String prefix) throws IOException {
    final VirtualFile f = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.complete(CompletionType.BASIC);
    List<String> strs = myFixture.getLookupElementStrings();
    if (prefix != null) {
      assertNotNull(strs);
      assertEquals(strs.get(0), prefix);
    }
    else if (strs != null && strs.size() > 0) {
      final String first = strs.get(0);
      assertFalse(first.endsWith(":"));
    }
  }

  protected void doTestExternalDoc(String expectedPart) {
    final PsiElement originalElement = myFixture.getFile().findElementAt(
      myFixture.getEditor().getCaretModel().getOffset());
    final PsiElement docTargetElement = DocumentationManager.getInstance(getProject()).
      findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(docTargetElement);
    final List<String> urls = provider.getUrlFor(docTargetElement, originalElement);
    final String doc = ((ExternalDocumentationProvider)provider).fetchExternalDocumentation(myFixture.getProject(), docTargetElement, urls);
    assertNotNull(doc);
    assertTrue("Can't find " + expectedPart + " in " + doc, doc.contains(expectedPart));
  }

  protected List<IntentionAction> highlightAndFindQuickFixes(Class<?> aClass) {
    final List<HighlightInfo> infos = myFixture.doHighlighting();
    final List<IntentionAction> actions = new ArrayList<IntentionAction>();

    for (HighlightInfo info : infos) {
      final List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          final IntentionAction action = pair.getFirst().getAction();
          if (aClass == null || action.getClass() == aClass) {
            actions.add(action);
          }
        }
      }
    }
    return actions;
  }

  protected void doTestOnClickQuickfix(VirtualFile file) {
    myFixture.configureFromExistingVirtualFile(file);
    final List<IntentionAction> actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEquals(1, actions.size());
    final IntentionAction action = actions.get(0);
    assertInstanceOf(action, AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        ((AndroidMissingOnClickHandlerInspection.MyQuickFix)action).doApplyFix(getProject());
      }
    });
    myFixture.checkResultByFile(testFolder + "/onClickIntention.xml");
  }
}

