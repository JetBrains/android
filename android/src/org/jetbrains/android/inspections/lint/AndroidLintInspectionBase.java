package org.jetbrains.android.inspections.lint;

import com.android.annotations.concurrency.GuardedBy;
import com.android.tools.idea.lint.ReplaceStringQuickFix;
import com.android.tools.idea.lint.SuppressLintIntentionAction;
import com.android.tools.lint.detector.api.*;
import com.android.tools.lint.detector.api.LintFix.LintFixGroup;
import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.LintFix.SetAttribute;
import com.google.common.collect.Lists;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.util.PsiElementPointer;
import gnu.trove.THashMap;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

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

  @GuardedBy("ISSUE_MAP_LOCK")
  private static volatile List<Tools> ourDynamicTools;

  private static boolean ourRegisterDynamicToolsFromTests;

  protected final Issue myIssue;
  private String[] myGroupPath;
  private final String myDisplayName;

  protected AndroidLintInspectionBase(@NotNull String displayName, @NotNull Issue issue) {
    myIssue = issue;
    myDisplayName = displayName;
  }

  @NotNull
  public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message,
                                             @Nullable LintFix fixData) {
    AndroidLintQuickFix[] fixes = getQuickFixes(startElement, endElement, message);

    if (fixData != null && fixes.length == 0) {
      return createFixes(startElement.getContainingFile(), fixData);
    }

    return fixes;
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

  /**
   * Returns all the fixes for this element and message.
   * This doesn't just call the direct fix provider methods on this inspection,
   * but also consults any {@link AndroidLintQuickFixProvider} implementations
   * to have their say.
   */
  @NotNull
  public AndroidLintQuickFix[] getAllFixes(@NotNull PsiElement startElement,
                                     @NotNull PsiElement endElement,
                                     @NotNull String message,
                                     @Nullable LintFix fixData,
                                     @NotNull AndroidLintQuickFixProvider[] fixProviders,
                                     @NotNull Issue issue) {
    List<AndroidLintQuickFix> result = new ArrayList<>(4);

    // The AndroidLintQuickFixProvider interface is a generic mechanism, but currently
    // only used by the Kotlin plugin - but it currently adds in Suppress actions for
    // all file types so limit its lookup to Kotlin files for now
    PsiFile file = startElement.getContainingFile();
      if (file != null && AndroidLintExternalAnnotator.isKotlin(file.getFileType())) {
        for (AndroidLintQuickFixProvider provider : fixProviders) {
          AndroidLintQuickFix[] fixes = provider.getQuickFixes(issue, startElement, endElement, message, fixData);
          Collections.addAll(result, fixes);
        }

        if (!result.isEmpty() &&
            (fixData == null || result.size() != 1 ||
             !result.get(0).getName().startsWith("Suppress: "))) {
          // If one or more fixes were registered by quickfix providers, and this is a Kotlin file,
          // it's likely that the Kotlin plugin provided an alternative quickfix for this problem,
          // and we don't want to include the Java-centric quickfixes from the Android plugin
          return result.toArray(AndroidLintQuickFix.EMPTY_ARRAY);
        }
      }

    AndroidLintQuickFix[] fixes = getQuickFixes(startElement, endElement, message, fixData);
    Collections.addAll(result, fixes);

    return result.toArray(AndroidLintQuickFix.EMPTY_ARRAY);
  }

  @NotNull
  public LocalQuickFix[] getLocalQuickFixes(@NotNull PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message,
                                             @Nullable LintFix fixData,
                                             @NotNull AndroidLintQuickFixProvider[] fixProviders,
                                             @NotNull Issue issue) {
    AndroidLintQuickFix[] fixes = getAllFixes(startElement, endElement, message, fixData, fixProviders, issue);
    if (fixes.length == 0) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    List<LocalQuickFix> result = new ArrayList<>(fixes.length);
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

    AndroidLintQuickFixProvider[] fixProviders = AndroidLintQuickFixProvider.EP_NAME.getExtensions();

    for (ProblemData problemData : problems) {
      final String originalMessage = problemData.getMessage();

      // We need to have explicit <html> and </html> tags around the text; inspection infrastructure
      // such as the {@link com.intellij.codeInspection.ex.DescriptorComposer} will call
      // {@link com.intellij.xml.util.XmlStringUtil.isWrappedInHtml}. See issue 177283 for uses.
      // Note that we also need to use HTML with unicode characters here, since the HTML display
      // in the inspections view does not appear to support numeric code character entities.
      String formattedMessage = HTML_START + RAW.convertTo(originalMessage, HTML_WITH_UNICODE) + HTML_END;
      LintFix quickfixData = problemData.getQuickfixData();

      // The inspections UI does not correctly handle

      final TextRange range = problemData.getTextRange();
      Issue issue = problemData.getIssue();

      if (range.getStartOffset() == range.getEndOffset()) {

        if (psiFile instanceof PsiBinaryFile || psiFile instanceof PsiDirectory) {
          final LocalQuickFix[] fixes = getLocalQuickFixes(psiFile, psiFile, originalMessage, quickfixData, fixProviders, issue);
          result.add(new NonTextFileProblemDescriptor((PsiFileSystemItem)psiFile, formattedMessage, fixes));
        } else if (!isSuppressedFor(psiFile)) {
          result.add(manager.createProblemDescriptor(psiFile, formattedMessage, false,
                                                     getLocalQuickFixes(psiFile, psiFile, originalMessage, quickfixData, fixProviders,
                                                                        issue), ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }
      else {
        final PsiElement startElement = psiFile.findElementAt(range.getStartOffset());
        final PsiElement endElement = psiFile.findElementAt(range.getEndOffset() - 1);

        if (startElement != null && endElement != null && !isSuppressedFor(startElement)) {
          result.add(manager.createProblemDescriptor(startElement, endElement, formattedMessage,
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false,
                                                     getLocalQuickFixes(startElement, endElement, originalMessage, quickfixData,
                                                                        fixProviders, issue)));
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
      // This action doesn't work for Kotlin files
      PsiFile file = context.getContainingFile();
      return file == null || !AndroidLintExternalAnnotator.isKotlin(file.getFileType());
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
  @SuppressWarnings("GuardedBy")
  public static void invalidateInspectionShortName2IssueMap() {
    //noinspection FieldAccessNotGuarded  // TestOnly method
    ourIssue2InspectionShortName = null;
    //noinspection FieldAccessNotGuarded  // TestOnly method
    ourDynamicTools = null;
  }

  @TestOnly
  public static void setRegisterDynamicToolsFromTests(boolean registerDynamicToolsFromTests) {
    ourRegisterDynamicToolsFromTests = registerDynamicToolsFromTests;
  }

  @Nullable
  public static List<Tools> getDynamicTools() {
    synchronized (ISSUE_MAP_LOCK) {
      return ourDynamicTools;
    }
  }

  public static void resetDynamicTools() {
    synchronized (ISSUE_MAP_LOCK) {
      ourDynamicTools = null;
    }
  }

  @Nullable
  public static Issue findIssueByShortName(@Nullable Project project, @NotNull String name) {
    // Look up issue by inspections (for third-party issues)
    String inspectionName = name.startsWith(LINT_INSPECTION_PREFIX) ? name : LINT_INSPECTION_PREFIX + name;

    Issue issue = null;
    List<Tools> tools = getDynamicTools();
    if (tools != null) {
      for (Tools tool : tools) {
        if (inspectionName.equals(tool.getShortName())) {
          InspectionProfileEntry e = tool.getTool().getTool();
          if (e instanceof AndroidLintInspectionBase) {
            issue = ((AndroidLintInspectionBase)e).getIssue();
            break;
          }
        }
      }
    }

    if (issue == null && project != null) {
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      for (InspectionToolWrapper e : profile.getInspectionTools(null)) {
        if (inspectionName.equals(e.getShortName())) {
          InspectionProfileEntry entry = e.getTool();
          if (entry instanceof AndroidLintInspectionBase) {
            issue = ((AndroidLintInspectionBase)entry).getIssue();
          }
        }
      }
    }

    return issue;
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

      // Third party lint check? If so register it dynamically, but
      // not during unit tests (where we typically end up with empty
      // inspection profiles containing only the to-be-tested inspections
      // and we don't want random other inspections to show up)
      String name = ourIssue2InspectionShortName.get(issue);
      if (name == null &&
          (!ApplicationManager.getApplication().isUnitTestMode() || ourRegisterDynamicToolsFromTests)) {
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

        ToolsImpl tools = current.getToolsOrNull(name, project);
        if (tools != null) {
          if (ourDynamicTools == null) {
            ourDynamicTools = new ArrayList<>();
          }
          ourDynamicTools.add(tools);
        }
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

  /** Wraps quickfixes from {@link LintFix} with default implementations */
  public static AndroidLintQuickFix[] createFixes(@Nullable PsiFile file, @Nullable LintFix lintFix) {
    if (lintFix instanceof ReplaceString) {
      ReplaceString data = (ReplaceString)lintFix;
      String regexp;
      if (data.oldPattern != null) {
        regexp = data.oldPattern;
      } else if (data.oldString != null) {
        regexp = "(" + Pattern.quote(data.oldString) + ")";
      } else {
        regexp = null;
      }
      ReplaceStringQuickFix fix = new ReplaceStringQuickFix(data.getDisplayName(), regexp, data.replacement);
      if (data.shortenNames) {
        fix.setShortenNames(true);
      }
      if (data.reformat) {
        fix.setFormat(true);
      }
      if (data.range != null && file != null) {
        Position start = data.range.getStart();
        Position end = data.range.getEnd();
        if (start != null && end != null) {
          SmartPointerManager manager = SmartPointerManager.getInstance(file.getProject());
          int startOffset = start.getOffset();
          int endOffset = end.getOffset();
          if (endOffset > startOffset) {
            TextRange textRange = TextRange.create(startOffset, Math.max(startOffset, endOffset));
            SmartPsiFileRange smartRange = manager.createSmartPsiFileRangePointer(file, textRange);
            fix.setRange(smartRange);
          }
        }
      }
      return new AndroidLintQuickFix[]{fix};
    } else if (lintFix instanceof SetAttribute) {
      SetAttribute data = (SetAttribute)lintFix;
      if (data.value == null) {
        return new AndroidLintQuickFix[]{ new RemoteAttributeFix(data) };
      }

      return new AndroidLintQuickFix[]{new SetAttributeQuickFix(data.getDisplayName(), data.attribute,
                                                                data.namespace, data.value,
                                                                data.dot, data.mark)};
    } else if (lintFix instanceof LintFixGroup) {
      LintFixGroup group = (LintFixGroup)lintFix;
      List<AndroidLintQuickFix> fixList = Lists.newArrayList();
      for (LintFix fix : group.fixes) {
        Collections.addAll(fixList, createFixes(file, fix));
      }
      AndroidLintQuickFix[] fixes = fixList.toArray(AndroidLintQuickFix.EMPTY_ARRAY);

      switch (group.type) {
        case COMPOSITE:
          return new AndroidLintQuickFix[] { new CompositeLintFix(lintFix.getDisplayName(), fixes) };
        case ALTERNATIVES:
          return fixes;
      }
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  static class CompositeLintFix implements AndroidLintQuickFix {
    private final String myDisplayName;
    private final AndroidLintQuickFix[] myFixes;

    public CompositeLintFix(String displayName, AndroidLintQuickFix[] myFixes) {
      myDisplayName = displayName;
      this.myFixes = myFixes;
    }

    @Override
    public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
      for (AndroidLintQuickFix fix : myFixes) {
        fix.apply(startElement, endElement, context);
      }
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      for (AndroidLintQuickFix fix : myFixes) {
        if (!fix.isApplicable(startElement, endElement, contextType)) {
          return false;
        }
      }
      return true;
    }

    @NotNull
    @Override
    public String getName() {
      return myDisplayName != null ? myDisplayName : "Fix";
    }
  }

  static class RemoteAttributeFix implements AndroidLintQuickFix {
    private final SetAttribute myData;

    public RemoteAttributeFix(SetAttribute data) {
      myData = data;
    }

    @Override
    public void apply(@NotNull PsiElement startElement,
                      @NotNull PsiElement endElement,
                      @NotNull AndroidQuickfixContexts.Context context) {
      XmlAttribute attribute = findAttribute(startElement);
      if(attribute != null && attribute.isValid()) {
        attribute.delete();
      }
    }

    @Override
    public boolean isApplicable(@NotNull PsiElement startElement,
                                @NotNull PsiElement endElement,
                                @NotNull AndroidQuickfixContexts.ContextType contextType) {
      XmlAttribute attribute = findAttribute(startElement);
      return attribute != null && attribute.isValid();
    }

    @Nullable
    private XmlAttribute findAttribute(@NotNull PsiElement startElement) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);

      if (tag == null) {
        return null;
      }

      return myData.namespace != null ? tag.getAttribute(myData.attribute, myData.namespace) :
             tag.getAttribute(myData.attribute);
    }

    @NotNull
    @Override
    public String getName() {
      return myData.getDisplayName();
    }
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
      // Ensure that we use different family names so actions are not collapsed into a single button in
      // the inspections UI (and then *all* processed when the user invokes the action); see
      // https://code.google.com/p/android/issues/detail?id=235641
      return myLintQuickFix.getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myLintQuickFix.apply(descriptor.getStartElement(), descriptor.getEndElement(), AndroidQuickfixContexts.BatchContext.getInstance());
    }

    @Override
    public boolean startInWriteAction() {
      return myLintQuickFix.startInWriteAction();
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
