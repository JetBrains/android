package org.jetbrains.android.inspections.lint;

import com.android.tools.lint.detector.api.*;
import com.android.utils.XmlUtils;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

import static com.android.tools.lint.detector.api.Issue.OutputFormat;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidLintInspectionBase extends GlobalInspectionTool implements BatchSuppressableTool {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.inspections.lint.AndroidLintInspectionBase");

  private static volatile Map<Issue, String> ourIssue2InspectionShortName;

  protected final Issue myIssue;
  private final String[] myGroupPath;
  private final String myDisplayName;

  protected AndroidLintInspectionBase(@NotNull String displayName, @NotNull Issue issue) {
    myIssue = issue;

    final Category category = issue.getCategory();
    final String[] categoryNames = category != null
                                   ? computeAllNames(category)
                                   : ArrayUtil.EMPTY_STRING_ARRAY;

    myGroupPath = ArrayUtil.mergeArrays(new String[]{AndroidBundle.message("android.inspections.group.name"),
      AndroidBundle.message("android.lint.inspections.subgroup.name")}, categoryNames);
    myDisplayName = displayName;
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
  private LocalQuickFix[] getLocalQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
    final AndroidLintQuickFix[] fixes = getQuickFixes(message);
    final LocalQuickFix[] result = new LocalQuickFix[fixes.length];

    for (int i = 0; i < fixes.length; i++) {
      if (fixes[i].isApplicable(startElement, endElement, AndroidQuickfixContexts.BatchContext.TYPE)) {
        result[i] = new MyLocalQuickFix(fixes[i]);
      }
    }
    return result;
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
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          final PsiManager psiManager = PsiManager.getInstance(globalContext.getProject());
          final PsiFile psiFile = psiManager.findFile(vFile);

          if (psiFile != null) {
            final ProblemDescriptor[] descriptors = computeProblemDescriptors(psiFile, manager, entry.getValue());

            if (descriptors.length > 0) {
              problemDescriptionsProcessor.addProblemElement(globalContext.getRefManager().getReference(psiFile), descriptors);
            }
          }
        }
      });
    }
  }

  @NotNull
  private ProblemDescriptor[] computeProblemDescriptors(@NotNull PsiFile psiFile,
                                                        @NotNull InspectionManager manager,
                                                        @NotNull List<ProblemData> problems) {
    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();

    for (ProblemData problemData : problems) {
      final String s = problemData.getMessage();
      final String message = XmlUtils.toXmlTextValue(s);
      final TextRange range = problemData.getTextRange();

      if (range.getStartOffset() == range.getEndOffset()) {
        PsiFile f = psiFile;

        if (f instanceof PsiBinaryFile) {
          // todo: show inspection in binary file (fix NPE)!
          final Module module = ModuleUtil.findModuleForPsiElement(f);

          if (module != null) {
            final AndroidFacet facet = AndroidFacet.getInstance(module);
            final VirtualFile manifestFile = facet != null ? AndroidRootUtil.getManifestFile(facet) : null;

            if (manifestFile != null) {
              f = f.getManager().findFile(manifestFile);
            }
          }
        }

        if (f != null && !isSuppressedFor(f)) {
          result.add(manager.createProblemDescriptor(f, message, false, getLocalQuickFixes(f, f, message),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else {
        final PsiElement startElement = psiFile.findElementAt(range.getStartOffset());
        final PsiElement endElement = psiFile.findElementAt(range.getEndOffset() - 1);

        if (startElement != null && endElement != null && !isSuppressedFor(startElement)) {
          result.add(manager.createProblemDescriptor(startElement, endElement, message,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                     getLocalQuickFixes(startElement, endElement, message)));
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
      final List<SuppressQuickFix> result = new ArrayList<SuppressQuickFix>();
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

    @NotNull
    @Override
    public String getName() {
      return "Suppress with @SuppressLint (Java) or tools:ignore (XML)";
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
        new SuppressLintIntentionAction(myIssue.getId(), myElement).invoke(project, null, file);
      }
    }
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    if (element.getLanguage() == JavaLanguage.INSTANCE) {
      return SuppressManager.getInstance().isSuppressedFor(element, getShortName());
    }
    if (element.getLanguage() == XMLLanguage.INSTANCE) {
      return XmlSuppressionProvider.isSuppressed(element, getShortName());
    }
    return false;
  }

  @TestOnly
  public static void invalidateInspectionShortName2IssueMap() {
    ourIssue2InspectionShortName = null;
  }

  public synchronized static String getInspectionShortNameByIssue(@NotNull Project project, @NotNull Issue issue) {
    if (ourIssue2InspectionShortName == null) {
      ourIssue2InspectionShortName = new HashMap<Issue, String>();

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();

      for (InspectionToolWrapper e : (InspectionToolWrapper[])profile.getInspectionTools(null)) {
        final String shortName = e.getShortName();

        if (shortName.startsWith("AndroidLint")) {
          final InspectionProfileEntry entry = e.getTool();
          if (entry instanceof AndroidLintInspectionBase) {
            final Issue s = ((AndroidLintInspectionBase)entry).getIssue();
            ourIssue2InspectionShortName.put(s, shortName);
          }
        }
      }
    }
    return ourIssue2InspectionShortName.get(issue);
  }

  @NotNull
  private static String[] computeAllNames(@NotNull Category category) {
    final List<String> result = new ArrayList<String>();

    Category c = category;

    while (c != null) {
      final String name = c.getName();

      if (name == null) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
      result.add(name);
      c = c.getParent();
    }
    return ArrayUtil.reverseArray(ArrayUtil.toStringArray(result));
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.lint.inspections.group.name");
  }

  @NotNull
  @Override
  public String[] getGroupPath() {
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
    sb.append(myIssue.getDescription(OutputFormat.HTML));
    sb.append("<br><br>");
    sb.append(myIssue.getExplanation(OutputFormat.HTML));
    List<String> urls = myIssue.getMoreInfo();
    if (!urls.isEmpty()) {
      boolean separated = false;
      for (String url : urls) {
        if (!myIssue.getExplanation(OutputFormat.RAW).contains(url)) {
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
    return StringUtil.trimEnd(getClass().getSimpleName(), "Inspection");
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    final Severity defaultSeverity = myIssue.getDefaultSeverity();
    if (defaultSeverity == null) {
      return HighlightDisplayLevel.WARNING;
    }
    final HighlightDisplayLevel displayLevel = toHighlightDisplayLevel(defaultSeverity);
    return displayLevel != null ? displayLevel : HighlightDisplayLevel.WARNING;
  }

  @Nullable
  private static HighlightDisplayLevel toHighlightDisplayLevel(@NotNull Severity severity) {
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
}
