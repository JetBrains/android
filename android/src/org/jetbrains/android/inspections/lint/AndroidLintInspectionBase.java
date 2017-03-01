package org.jetbrains.android.inspections.lint;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.lint.SuppressLintIntentionAction;
import com.android.tools.lint.detector.api.*;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileKt;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

import static com.android.tools.lint.detector.api.TextFormat.*;
import static com.intellij.xml.CommonXmlStrings.HTML_END;
import static com.intellij.xml.CommonXmlStrings.HTML_START;

public abstract class AndroidLintInspectionBase extends GlobalInspectionTool {
  /** Prefix used by the comment suppress mechanism in Studio/IntelliJ */
  public static final String LINT_INSPECTION_PREFIX = "AndroidLint";

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintInspectionBase");

  private static final Object ISSUE_MAP_LOCK = new Object();

  @GuardedBy("ISSUE_MAP_LOCK")
  private static volatile Map<Issue, String> ourIssue2InspectionShortName;

  protected final Issue myIssue;
  private String[] myGroupPath;
  private final String myDisplayName;

  protected AndroidLintInspectionBase(@NotNull String displayName, @NotNull Issue issue) {
    myIssue = issue;
    myDisplayName = displayName;
  }

  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message,
                                             @Nullable Object extraData) {
    return getQuickFixes(startElement, endElement, message);
  }

  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    return getQuickFixes(message);
  }

  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  @NotNull
  public IntentionAction[] getIntentions(@NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    return IntentionAction.EMPTY_ARRAY;
  }

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @NotNull
  private LocalQuickFix[] getLocalQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message,
                                             @Nullable Object extraData) {
    final AndroidLintQuickFix[] fixes = getQuickFixes(startElement, endElement, message, extraData);
    final List<LocalQuickFix> result = new ArrayList<>(fixes.length);

    for (AndroidLintQuickFix fix : fixes) {
      if (fix.isApplicable(startElement, endElement, AndroidQuickfixContexts.BatchContext.TYPE)) {
        result.add(new MyLocalQuickFix(fix));
      }
    }
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope,
                            @NotNull final InspectionManager manager,
                            @NotNull final GlobalInspectionContext globalContext,
                            @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    final AndroidLintGlobalInspectionContext androidLintContext = globalContext.getExtension(AndroidLintGlobalInspectionContext.ID);
    if (androidLintContext == null) {
      return;
    }

    final Map<Issue, Map<File, List<ProblemData>>> problemMap = androidLintContext.getResults();
    if (problemMap == null) {
      return;
    }

    final Map<File, List<ProblemData>> file2ProblemList = problemMap.get(myIssue);
    if (file2ProblemList == null) {
      return;
    }

    for (final Map.Entry<File, List<ProblemData>> entry : file2ProblemList.entrySet()) {
      final File file = entry.getKey();
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

      if (vFile == null) {
        continue;
      }
      ApplicationManager.getApplication().runReadAction(() -> {
        final PsiManager psiManager = PsiManager.getInstance(globalContext.getProject());
        final PsiFile psiFile = psiManager.findFile(vFile);

        if (psiFile != null) {
          final ProblemDescriptor[] descriptors = computeProblemDescriptors(psiFile, manager, entry.getValue());

          if (descriptors.length > 0) {
            problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiFile), descriptors);
          }
        } else if (vFile.isDirectory()) {
          final PsiDirectory psiDirectory = psiManager.findDirectory(vFile);

          if (psiDirectory != null) {
            final ProblemDescriptor[] descriptors = computeProblemDescriptors(psiDirectory, manager, entry.getValue());

            if (descriptors.length > 0) {
              problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiDirectory), descriptors);
            }
          }
        }
      });
    }
  }

  @NotNull
  private ProblemDescriptor[] computeProblemDescriptors(@NotNull PsiElement psiFile,
                                                        @NotNull InspectionManager manager,
                                                        @NotNull List<ProblemData> problems) {
    final List<ProblemDescriptor> result = new ArrayList<>();

    for (ProblemData problemData : problems) {
      final String originalMessage = problemData.getMessage();

      // We need to have explicit <html> and </html> tags around the text; inspection infrastructure
      // such as the {@link com.intellij.codeInspection.ex.DescriptorComposer} will call
      // {@link com.intellij.xml.util.XmlStringUtil.isWrappedInHtml}. See issue 177283 for uses.
      // Note that we also need to use HTML with unicode characters here, since the HTML display
      // in the inspections view does not appear to support numeric code character entities.
      String formattedMessage = HTML_START + RAW.convertTo(originalMessage, HTML_WITH_UNICODE) + HTML_END;
      Object quickfixData = problemData.getQuickfixData();

      // The inspections UI does not correctly handle

      final TextRange range = problemData.getTextRange();

      if (range.getStartOffset() == range.getEndOffset()) {

        if (psiFile instanceof PsiBinaryFile || psiFile instanceof PsiDirectory) {
          final LocalQuickFix[] fixes = getLocalQuickFixes(psiFile, psiFile, originalMessage, quickfixData);
          result.add(new NonTextFileProblemDescriptor((PsiFileSystemItem)psiFile, formattedMessage, fixes));
        } else if (!isSuppressedFor(psiFile)) {
          result.add(manager.createProblemDescriptor(psiFile, formattedMessage, false,
                                                     getLocalQuickFixes(psiFile, psiFile, originalMessage, quickfixData),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else {
        final PsiElement startElement = psiFile.findElementAt(range.getStartOffset());
        final PsiElement endElement = psiFile.findElementAt(range.getEndOffset() - 1);

        if (startElement != null && endElement != null && !isSuppressedFor(startElement)) {
          result.add(manager.createProblemDescriptor(startElement, endElement, formattedMessage,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                     getLocalQuickFixes(startElement, endElement, originalMessage, quickfixData)));
        }
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    SuppressLintQuickFix suppressLintQuickFix = new SuppressLintQuickFix(myIssue);
    if (AndroidLintExternalAnnotator.INCLUDE_IDEA_SUPPRESS_ACTIONS) {
      final List<SuppressQuickFix> result = new ArrayList<>();
      result.add(suppressLintQuickFix);
      result.addAll(Arrays.asList(BatchSuppressManager.SERVICE.getInstance().createBatchSuppressActions(HighlightDisplayKey.find(getShortName()))));
      result.addAll(Arrays.asList(new XmlSuppressableInspectionTool.SuppressTagStatic(getShortName()),
                                  new XmlSuppressableInspectionTool.SuppressForFile(getShortName())));
      return result.toArray(new SuppressQuickFix[result.size()]);
    } else {
      return new SuppressQuickFix[] { suppressLintQuickFix };
    }
  }

  private static class SuppressLintQuickFix implements SuppressQuickFix {
    private Issue myIssue;

    private SuppressLintQuickFix(Issue issue) {
      myIssue = issue;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, @NotNull PsiElement context) {
      return true;
    }

    @Override
    public boolean isSuppressAll() {
      return false;
    }

    @NotNull
    @Override
    public String getName() {
      return "Suppress with @SuppressLint (Java) or tools:ignore (XML) or lint.xml";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Suppress";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement myElement = descriptor.getPsiElement();
      PsiFile file = PsiTreeUtil.getParentOfType(myElement, PsiFile.class, false);
      if (file != null) {
        new SuppressLintIntentionAction(myIssue, myElement).invoke(project, null, file);
      }
    }
  }

  @TestOnly
  public static void invalidateInspectionShortName2IssueMap() {
    ourIssue2InspectionShortName = null;
  }

  public static String getInspectionShortNameByIssue(@NotNull Project project, @NotNull Issue issue) {
    synchronized (ISSUE_MAP_LOCK) {
      if (ourIssue2InspectionShortName == null) {
        ourIssue2InspectionShortName = new HashMap<>();

        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();

        for (InspectionToolWrapper e : profile.getInspectionTools(null)) {
          final String shortName = e.getShortName();

          if (shortName.startsWith(LINT_INSPECTION_PREFIX)) {
            final InspectionProfileEntry entry = e.getTool();
            if (entry instanceof AndroidLintInspectionBase) {
              final Issue s = ((AndroidLintInspectionBase)entry).getIssue();
              ourIssue2InspectionShortName.put(s, shortName);
            }
          }
        }
      }

      String name = ourIssue2InspectionShortName.get(issue);
      if (name == null) {
        AndroidLintInspectionBase tool = createInspection(issue);
        LintInspectionFactory factory = new LintInspectionFactory(tool);
        // We have to add the tool both to the current and the base profile; otherwise, bringing up
        // the profile settings will show all these issues as modified (blue) because
        // InspectionProfileModifiableModel#isProperSetting returns true if the tool is found
        // only in the current profile, not the base profile (and returning true from that method
        // shows the setting as modified, even though the name seems totally unrelated)
        InspectionProfileImpl base = InspectionProfileKt.getBASE_PROFILE();
        InspectionProfileImpl current = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        base.addTool(project, factory, new THashMap<>());
        current.addTool(project, factory, new THashMap<>());

        name = tool.getShortName();
        ourIssue2InspectionShortName.put(issue, name);
      }
      return name;
    }
  }

  private static AndroidLintInspectionBase createInspection(Issue issue) {
    return new AndroidLintInspectionBase(issue.getBriefDescription(TEXT), issue) {};
  }

  private static class LintInspectionFactory extends GlobalInspectionToolWrapper {
    private final AndroidLintInspectionBase myInspection;

    private LintInspectionFactory(AndroidLintInspectionBase inspection) {
      super(inspection);
      myInspection = inspection;
    }

    @Override
    public boolean isEnabledByDefault() {
      return myInspection.isEnabledByDefault();
    }

    @NotNull
    @Override
    public GlobalInspectionToolWrapper createCopy() {
      return new LintInspectionFactory(createInspection(myInspection.myIssue));
    }
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    // Use root category (inspections window doesn't do nesting the way the preference window does)
    Category category = myIssue.getCategory();
    while (category.getParent() != null) {
      category = category.getParent();
    }

    return AndroidBundle.message("android.lint.inspections.group.name") + ": " + category.getName();
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
    if (myGroupPath == null) {
      Category category = myIssue.getCategory();

      int count = 2; // "Android", "Lint"
      Category curr = category;
      while (curr != null) {
        count++;
        curr = curr.getParent();
      }

      String[] path = new String[count];
      while (category != null) {
        path[--count] = category.getName();
        category = category.getParent();
      }
      assert count == 2;

      path[0] = AndroidBundle.message("android.inspections.group.name");
      path[1] = AndroidBundle.message("android.lint.inspections.subgroup.name");

      myGroupPath = path;
    }

    return myGroupPath;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @SuppressWarnings("deprecation")
  @Override
  public String getStaticDescription() {
    StringBuilder sb = new StringBuilder(1000);
    sb.append("<html><body>");
    sb.append(myIssue.getBriefDescription(HTML));
    sb.append("<br><br>");
    sb.append(myIssue.getExplanation(HTML));
    List<String> urls = myIssue.getMoreInfo();
    if (!urls.isEmpty()) {
      boolean separated = false;
      for (String url : urls) {
        if (!myIssue.getExplanation(RAW).contains(url)) {
          if (!separated) {
            sb.append("<br><br>");
            separated = true;
          } else {
            sb.append("<br>");
          }
          sb.append("<a href=\"");
          sb.append(url);
          sb.append("\">");
          sb.append(url);
          sb.append("</a>");
        }
      }
    }
    sb.append("</body></html>");

    return sb.toString();
  }

  @Override
  public boolean isEnabledByDefault() {
    return myIssue.isEnabledByDefault();
  }

  @NotNull
  @Override
  public String getShortName() {
    return LINT_INSPECTION_PREFIX + myIssue.getId();
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    final Severity defaultSeverity = myIssue.getDefaultSeverity();
    final HighlightDisplayLevel displayLevel = toHighlightDisplayLevel(defaultSeverity);
    return displayLevel != null ? displayLevel : HighlightDisplayLevel.WARNING;
  }

  @Nullable
  static HighlightDisplayLevel toHighlightDisplayLevel(@NotNull Severity severity) {
    switch (severity) {
      case ERROR:
        return HighlightDisplayLevel.ERROR;
      case FATAL:
        return HighlightDisplayLevel.ERROR;
      case WARNING:
        return HighlightDisplayLevel.WARNING;
      case INFORMATIONAL:
        return HighlightDisplayLevel.WEAK_WARNING;
      case IGNORE:
        return null;
      default:
        LOG.error("Unknown severity " + severity);
        return null;
    }
  }

  /** Returns true if the given analysis scope is adequate for single-file analysis */
  private static boolean isSingleFileScope(EnumSet<Scope> scopes) {
    if (scopes.size() != 1) {
      return false;
    }
    final Scope scope = scopes.iterator().next();
    return scope == Scope.JAVA_FILE || scope == Scope.RESOURCE_FILE || scope == Scope.MANIFEST
           || scope == Scope.PROGUARD_FILE || scope == Scope.OTHER;
  }

  @Override
  public boolean worksInBatchModeOnly() {
    Implementation implementation = myIssue.getImplementation();
    if (isSingleFileScope(implementation.getScope())) {
      return false;
    }
    for (EnumSet<Scope> scopes : implementation.getAnalysisScopes()) {
      if (isSingleFileScope(scopes)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  public Issue getIssue() {
    return myIssue;
  }

  static class MyLocalQuickFix implements LocalQuickFix {
    private final AndroidLintQuickFix myLintQuickFix;

    MyLocalQuickFix(@NotNull AndroidLintQuickFix lintQuickFix) {
      myLintQuickFix = lintQuickFix;
    }

    @NotNull
    @Override
    public String getName() {
      return myLintQuickFix.getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return AndroidBundle.message("android.lint.quickfixes.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myLintQuickFix.apply(descriptor.getStartElement(), descriptor.getEndElement(), AndroidQuickfixContexts.BatchContext.getInstance());
    }
  }

  /**
   * A {@link ProblemDescriptor} for image and directory files. This is
   * necessary because the {@link InspectionManager}'s createProblemDescriptor methods
   * all use {@link ProblemDescriptorBase} where in the constructor
   * it insists that the start and end {@link PsiElement} instances must have a valid
   * <b>text</b> range, which does not apply for images.
   * <p>
   * This custom descriptor allows the batch lint analysis to correctly handle lint errors
   * associated with image files (such as the various {@link com.android.tools.lint.checks.IconDetector}
   * warnings), as well as directory errors (such as incorrect locale folders),
   * and clicking on them will navigate to the correct icon.
   */
  private static class NonTextFileProblemDescriptor implements ProblemDescriptor {
    private final PsiFileSystemItem myFile;
    private final String myMessage;
    private final LocalQuickFix[] myFixes;
    private ProblemGroup myGroup;

    public NonTextFileProblemDescriptor(@NotNull PsiFileSystemItem file, @NotNull String message, @NotNull LocalQuickFix[] fixes) {
      myFile = file;
      myMessage = message;
      myFixes = fixes;
    }

    @Override
    public PsiElement getPsiElement() {
      return myFile;
    }

    @Override
    public PsiElement getStartElement() {
      return myFile;
    }

    @Override
    public PsiElement getEndElement() {
      return myFile;
    }

    @Override
    public TextRange getTextRangeInElement() {
      return new TextRange(0, 0);
    }

    @Override
    public int getLineNumber() {
      return 0;
    }

    @NotNull
    @Override
    public ProblemHighlightType getHighlightType() {
      return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    @Override
    public boolean isAfterEndOfLine() {
      return false;
    }

    @Override
    public void setTextAttributes(TextAttributesKey key) {
    }

    @Nullable
    @Override
    public ProblemGroup getProblemGroup() {
      return myGroup;
    }

    @Override
    public void setProblemGroup(@Nullable ProblemGroup problemGroup) {
      myGroup = problemGroup;
    }

    @Override
    public boolean showTooltip() {
      return false;
    }

    @NotNull
    @Override
    public String getDescriptionTemplate() {
      return myMessage;
    }

    @Nullable
    @Override
    public QuickFix[] getFixes() {
      return myFixes;
    }
  }
}
