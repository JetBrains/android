/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.util;

import com.android.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.TargetSelectionMode;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.tree.java.IKeywordElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.intellij.openapi.application.ApplicationManager.getApplication;

/**
 * @author yole, coyote
 */
public class AndroidUtils {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.util.AndroidUtils");

  @NonNls public static final String NAMESPACE_KEY = "android";
  @NonNls public static final String SYSTEM_RESOURCE_PACKAGE = "android";

  // Classes and constants
  @NonNls public static final String VIEW_CLASS_NAME = SdkConstants.CLASS_VIEW;
  @NonNls public static final String APPLICATION_CLASS_NAME = SdkConstants.CLASS_APPLICATION;
  @NonNls public static final String ACTIVITY_BASE_CLASS_NAME = SdkConstants.CLASS_ACTIVITY;
  @NonNls public static final String R_CLASS_NAME = SdkConstants.R_CLASS;
  @NonNls public static final String MANIFEST_CLASS_NAME = SdkConstants.FN_MANIFEST_BASE;

  @NonNls public static final String LAUNCH_ACTION_NAME = "android.intent.action.MAIN";
  @NonNls public static final String WALLPAPER_SERVICE_ACTION_NAME = "android.service.wallpaper.WallpaperService";

  @NonNls public static final String LAUNCH_CATEGORY_NAME = "android.intent.category.LAUNCHER";
  @NonNls public static final String LEANBACK_LAUNCH_CATEGORY_NAME = "android.intent.category.LEANBACK_LAUNCHER";
  @NonNls public static final String DEFAULT_CATEGORY_NAME = "android.intent.category.DEFAULT";
  @NonNls public static final String WATCHFACE_CATEGORY_NAME = "com.google.android.wearable.watchface.category.WATCH_FACE";

  @NonNls public static final String INSTRUMENTATION_RUNNER_BASE_CLASS = SdkConstants.CLASS_INSTRUMENTATION;
  @NonNls public static final String SERVICE_CLASS_NAME = SdkConstants.CLASS_SERVICE;
  @NonNls public static final String RECEIVER_CLASS_NAME = SdkConstants.CLASS_BROADCASTRECEIVER;
  @NonNls public static final String PROVIDER_CLASS_NAME = SdkConstants.CLASS_CONTENTPROVIDER;

  // Properties
  @NonNls public static final String ANDROID_LIBRARY_PROPERTY = SdkConstants.ANDROID_LIBRARY;
  @NonNls public static final String ANDROID_PROJECT_TYPE_PROPERTY = "project.type";
  @NonNls public static final String ANDROID_MANIFEST_MERGER_PROPERTY = "manifestmerger.enabled";
  @NonNls public static final String ANDROID_DEX_DISABLE_MERGER = "dex.disable.merger";
  @NonNls public static final String ANDROID_DEX_FORCE_JUMBO_PROPERTY = "dex.force.jumbo";
  @NonNls public static final String ANDROID_TARGET_PROPERTY = ProjectProperties.PROPERTY_TARGET;
  @NonNls public static final String ANDROID_LIBRARY_REFERENCE_PROPERTY_PREFIX = "android.library.reference.";
  @NonNls public static final String TAG_LINEAR_LAYOUT = SdkConstants.LINEAR_LAYOUT;
  private static final String[] ANDROID_COMPONENT_CLASSES = new String[]{ACTIVITY_BASE_CLASS_NAME,
    SERVICE_CLASS_NAME, RECEIVER_CLASS_NAME, PROVIDER_CLASS_NAME};

  private static final Lexer JAVA_LEXER = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);

  private AndroidUtils() {
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull final Module module,
                                                        @NotNull final VirtualFile file,
                                                        @NotNull final Class<T> aClass) {
    return loadDomElement(module.getProject(), file, aClass);
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull final Project project,
                                                        @NotNull final VirtualFile file,
                                                        @NotNull final Class<T> aClass) {
    return getApplication().runReadAction(new Computable<T>() {
      @Override
      @Nullable
      public T compute() {
        if (project.isDisposed()) return null;
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile instanceof XmlFile) {
          return loadDomElementWithReadPermission(project, (XmlFile)psiFile, aClass);
        }
        else {
          return null;
        }
      }
    });
  }

  /** This method should be called under a read action. */
  @Nullable
  public static <T extends DomElement> T loadDomElementWithReadPermission(@NotNull Project project,
                                                                          @NotNull XmlFile xmlFile,
                                                                          @NotNull Class<T> aClass) {
    getApplication().assertReadAccessAllowed();
    DomManager domManager = DomManager.getDomManager(project);
    DomFileElement<T> element = domManager.getFileElement(xmlFile, aClass);
    if (element == null) return null;
    return element.getRootElement();
  }

  @Nullable
  public static VirtualFile findSourceRoot(@NotNull Module module, VirtualFile file) {
    final Set<VirtualFile> sourceRoots = new HashSet<>();
    Collections.addAll(sourceRoots, ModuleRootManager.getInstance(module).getSourceRoots());

    while (file != null) {
      if (sourceRoots.contains(file)) {
        return file;
      }
      file = file.getParent();
    }
    return null;
  }

  @Nullable
  public static String computePackageName(@NotNull Module module, VirtualFile file) {
    final Set<VirtualFile> sourceRoots = new HashSet<>();
    Collections.addAll(sourceRoots, ModuleRootManager.getInstance(module).getSourceRoots());

    final VirtualFile projectDir = module.getProject().getBaseDir();
    final List<String> packages = new ArrayList<>();
    file = file.getParent();

    while (file != null && !Comparing.equal(projectDir, file) && !sourceRoots.contains(file)) {
      packages.add(file.getName());
      file = file.getParent();
    }

    if (file != null && sourceRoots.contains(file)) {
      final StringBuilder packageName = new StringBuilder();

      for (int i = packages.size() - 1; i >= 0; i--) {
        packageName.append(packages.get(i));
        if (i > 0) packageName.append('.');
      }
      return packageName.toString();
    }
    return null;
  }

  public static boolean isAbstract(@NotNull PsiClass c) {
    return (c.isInterface() || c.hasModifierProperty(PsiModifier.ABSTRACT));
  }

  @Nullable
  public static Module getAndroidModule(ConfigurationContext context) {
    Module module = context.getModule();
    if (module == null || AndroidFacet.getInstance(module) == null) {
      return null;
    }
    return module;
  }

  public static VirtualFile createChildDirectoryIfNotExist(Project project, VirtualFile parent, String name) throws IOException {
    final VirtualFile child = parent.findChild(name);
    return child == null ? parent.createChildDirectory(project, name) : child;
  }

  @Nullable
  public static PsiFile getContainingFile(@NotNull PsiElement element) {
    return element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
  }

  public static void navigateTo(@NotNull PsiElement[] targets, @Nullable RelativePoint pointToShowPopup) {
    if (targets.length == 0) {
      final JComponent renderer = HintUtil.createErrorLabel("Empty text");
      final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(renderer, renderer).createPopup();
      if (pointToShowPopup != null) {
        popup.show(pointToShowPopup);
      }
      return;
    }
    if (targets.length == 1 || pointToShowPopup == null) {
      PsiNavigateUtil.navigate(targets[0]);
    }
    else {
      DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer() {
        @Override
        public String getElementText(PsiElement element) {
          final PsiFile file = getContainingFile(element);
          return file != null ? file.getName() : super.getElementText(element);
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
          final PsiFile file = getContainingFile(element);
          final PsiDirectory dir = file != null ? file.getContainingDirectory() : null;
          return dir == null ? "" : '(' + dir.getName() + ')';
        }
      };
      final JBPopup popup = NavigationUtil.getPsiElementPopup(targets, renderer, null);
      popup.show(pointToShowPopup);
    }
  }

  @NotNull
  public static ExecutionStatus executeCommand(@NotNull GeneralCommandLine commandLine,
                                               @Nullable final OutputProcessor processor,
                                               @Nullable WaitingStrategies.Strategy strategy) throws ExecutionException {
    LOG.info(commandLine.getCommandLineString());
    OSProcessHandler handler = new OSProcessHandler(commandLine);

    final ProcessAdapter listener = new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
        if (processor != null) {
          final String message = event.getText();
          processor.onTextAvailable(message);
        }
      }
    };

    if (!(strategy instanceof WaitingStrategies.DoNotWait)) {
      handler.addProcessListener(listener);
    }

    handler.startNotify();
    try {
      if (!(strategy instanceof WaitingStrategies.WaitForever)) {
        if (strategy instanceof WaitingStrategies.WaitForTime) {
          handler.waitFor(((WaitingStrategies.WaitForTime)strategy).getTimeMs());
        }
      }
      else {
        handler.waitFor();
      }
    }
    catch (ProcessCanceledException e) {
      return ExecutionStatus.ERROR;
    }

    if (!handler.isProcessTerminated()) {
      return ExecutionStatus.TIMEOUT;
    }

    if (!(strategy instanceof WaitingStrategies.DoNotWait)) {
      handler.removeProcessListener(listener);
    }
    int exitCode = handler.getProcess().exitValue();
    return exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.ERROR;
  }

  @NotNull
  public static String getSimpleNameByRelativePath(@NotNull String relativePath) {
    relativePath = FileUtil.toSystemIndependentName(relativePath);
    int index = relativePath.lastIndexOf('/');
    if (index < 0) {
      return relativePath;
    }
    return relativePath.substring(index + 1);
  }

  /**
   * Return a suffix of passed string after the last dot, if at least one dot is present and
   * resulting suffix is non-empty.
   */
  @Nullable
  public static String getUnqualifiedName(@NotNull String qualifiedName) {
    int start = qualifiedName.lastIndexOf('.');
    if (start == -1 || start + 1 == qualifiedName.length()) {
      return null;
    }

    return qualifiedName.substring(start + 1);
  }

  @NotNull
  public static AndroidFacet addAndroidFacetInWriteAction(@NotNull final Module module,
                                                          @NotNull final VirtualFile contentRoot,
                                                          final boolean library) {
    return getApplication().runWriteAction(new Computable<AndroidFacet>() {
      @Override
      public AndroidFacet compute() {
        return addAndroidFacet(module, contentRoot, library);
      }
    });
  }

  @NotNull
  public static AndroidFacet addAndroidFacet(final Module module, @NotNull VirtualFile contentRoot,
                                             boolean library) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    AndroidFacet facet = model.getFacetByType(AndroidFacet.ID);

    if (facet == null) {
      facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
      AndroidFacetConfiguration configuration = facet.getConfiguration();
      configuration.init(module, contentRoot);
      if (library) {
        facet.setProjectType(PROJECT_TYPE_LIBRARY);
      }
      model.addFacet(facet);
    }
    model.commit();

    return facet;
  }

  @Nullable
  public static VirtualFile findFileByAbsoluteOrRelativePath(@Nullable VirtualFile baseDir, @NotNull String path) {
    VirtualFile libDir = LocalFileSystem.getInstance().findFileByPath(path);
    if (libDir != null) {
      return libDir;
    }
    else if (baseDir != null) {
      return LocalFileSystem.getInstance().findFileByPath(baseDir.getPath() + '/' + path);
    }
    return null;
  }

  @Nullable
  public static TargetSelectionMode getDefaultTargetSelectionMode(@NotNull Module module,
                                                                  @NotNull ConfigurationType type,
                                                                  @NonNls ConfigurationType alternativeType) {
    final RunManager runManager = RunManager.getInstance(module.getProject());
    List<RunConfiguration> configurations = runManager.getConfigurationsList(type);

    TargetSelectionMode alternative = null;

    if (!configurations.isEmpty()) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase) {
          final AndroidRunConfigurationBase runConfig = (AndroidRunConfigurationBase)configuration;
          final TargetSelectionMode targetMode = runConfig.getDeployTargetContext().getTargetSelectionMode();

          //noinspection IfStatementWithIdenticalBranches - branches are only identical for final iteration of outer loop
          if (runConfig.getConfigurationModule() == module) {
            return targetMode;
          }
          else {
            alternative = targetMode;
          }
        }
      }
    }

    if (alternative != null) {
      return alternative;
    }
    configurations = runManager.getConfigurationsList(alternativeType);

    if (!configurations.isEmpty()) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase) {
          return ((AndroidRunConfigurationBase)configuration).getDeployTargetContext().getTargetSelectionMode();
        }
      }
    }
    return null;
  }

  public static boolean equal(@Nullable String s1, @Nullable String s2, boolean distinguishDelimiters) {
    if (s1 == null || s2 == null) {
      return false;
    }
    if (s1.length() != s2.length()) return false;
    for (int i = 0, n = s1.length(); i < n; i++) {
      char c1 = s1.charAt(i);
      char c2 = s2.charAt(i);
      if (distinguishDelimiters || (Character.isLetterOrDigit(c1) && Character.isLetterOrDigit(c2))) {
        if (c1 != c2) return false;
      }
    }
    return true;
  }

  @NotNull
  public static List<AndroidFacet> getApplicationFacets(@NotNull Project project) {
    final List<AndroidFacet> result = new ArrayList<>();

    for (AndroidFacet facet : ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID)) {
      if (facet.isAppProject()) {
        result.add(facet);
      }
    }
    return result;
  }

  @NotNull
  public static List<AndroidFacet> getAndroidLibraryDependencies(@NotNull Module module) {
    final List<AndroidFacet> depFacets = new ArrayList<>();

    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          final Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            final AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null && depFacet.canBeDependency()) {
              depFacets.add(depFacet);
            }
          }
        }
      }
    }
    return depFacets;
  }

  @NotNull
  public static List<AndroidFacet> getAllAndroidDependencies(@NotNull Module module, boolean androidLibrariesOnly) {
    return AndroidDependenciesCache.getInstance(module).getAllAndroidDependencies(androidLibrariesOnly);
  }

  @NotNull
  public static Set<String> getDepLibsPackages(Module module) {
    final Set<String> result = new HashSet<>();
    final HashSet<Module> visited = new HashSet<>();

    if (visited.add(module)) {
      for (AndroidFacet depFacet : getAllAndroidDependencies(module, true)) {
        final Manifest manifest = depFacet.getManifest();

        if (manifest != null) {
          String aPackage = manifest.getPackage().getValue();
          if (aPackage != null) {
            result.add(aPackage);
          }
        }
      }
    }
    return result;
  }

  public static void checkNewPassword(JPasswordField passwordField, JPasswordField confirmedPasswordField) throws CommitStepException {
    char[] password = passwordField.getPassword();
    char[] confirmedPassword = confirmedPasswordField.getPassword();
    try {
      checkPassword(password);
      if (password.length < 6) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.incorrect.password.length"));
      }
      if (!Arrays.equals(password, confirmedPassword)) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.passwords.not.match.error"));
      }
    }
    finally {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmedPassword, '\0');
    }
  }

  public static void checkPassword(char[] password) throws CommitStepException {
    if (password.length == 0) {
      throw new CommitStepException(AndroidBundle.message("android.export.package.specify.password.error"));
    }
  }

  @NotNull
  public static <T> List<T> toList(@NotNull Enumeration<T> enumeration) {
    return ContainerUtil.toList(enumeration);
  }

  public static void reportError(@NotNull Project project, @NotNull String message) {
    reportError(project, message, CommonBundle.getErrorTitle());
  }

  public static void reportError(@NotNull Project project, @NotNull String message, @NotNull String title) {
    if (getApplication().isUnitTestMode()) {
      throw new IncorrectOperationException(message);
    }
    else {
      Messages.showErrorDialog(project, message, title);
    }
  }

  public static void showStackStace(@Nullable final Project project, @NotNull Throwable[] throwables) {
    final StringBuilder messageBuilder = new StringBuilder();

    for (Throwable t : throwables) {
      if (messageBuilder.length() > 0) {
        messageBuilder.append("\n\n");
      }
      messageBuilder.append(AndroidCommonUtils.getStackTrace(t));
    }

    final DialogWrapper wrapper = new DialogWrapper(project, false) {

      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout());
        final JTextArea textArea = new JTextArea(messageBuilder.toString());
        textArea.setEditable(false);
        textArea.setRows(40);
        textArea.setColumns(70);
        panel.add(ScrollPaneFactory.createScrollPane(textArea));
        return panel;
      }
    };
    wrapper.setTitle("Stack Trace");
    wrapper.show();
  }

  /**
   * Checks if the given name is a valid Android application package (which has
   * additional requirements beyond a normal Java package)
   *
   * @see #validateAndroidPackageName(String)
   */
  public static boolean isValidAndroidPackageName(@NotNull String name) {
    return validateAndroidPackageName(name) == null;
  }

  /**
   * Checks if the given name is a valid general Java package name.
   * <p>
   * If validating the Android package name, use {@link #validateAndroidPackageName(String)} instead!
   */
  public static boolean isValidJavaPackageName(@NotNull String name) {
    int index = 0;
    while (true) {
      int index1 = name.indexOf('.', index);
      if (index1 < 0) index1 = name.length();
      if (!isIdentifier(name.substring(index, index1))) return false;
      if (index1 == name.length()) return true;
      index = index1 + 1;
    }
  }

  /**
   * Validates a potential package name and returns null if the package name is valid, and otherwise
   * returns a description for why it is not valid.
   * <p>
   * Note that Android package names are more restrictive than general Java package names;
   * we require at least two segments, limit the character set to [a-zA-Z0-9_] (Java allows any
   * {@link Character#isLetter(char)} and require that each segment start with a letter (Java allows
   * underscores at the beginning).
   * <p>
   * For details, see core/java/android/content/pm/PackageParser.java#validateName
   *
   * @param name the package name
   * @return null if the package is valid as an Android package name, and otherwise a description for why not
   */
  @Nullable
  public static String validateAndroidPackageName(@NotNull String name) {
    if (name.isEmpty()) {
      return "Package name is missing";
    }

    String packageManagerCheck = validateName(name, true);
    if (packageManagerCheck != null) {
      return packageManagerCheck;
    }

    // In addition, we have to check that none of the segments are Java identifiers, since
    // that will lead to compilation errors, which the package manager doesn't need to worry about
    // (the code wouldn't have compiled)

    getApplication().assertReadAccessAllowed();
    int index = 0;
    while (true) {
      int index1 = name.indexOf('.', index);
      if (index1 < 0) {
        index1 = name.length();
      }
      String error = isReservedKeyword(name.substring(index, index1));
      if (error != null) return error;
      if (index1 == name.length()) {
        break;
      }
      index = index1 + 1;
    }

    return null;
  }

  @Nullable
  public static String isReservedKeyword(@NotNull String string) {
    Lexer lexer = JAVA_LEXER;
    lexer.start(string);
    if (lexer.getTokenType() != JavaTokenType.IDENTIFIER) {
      if (lexer.getTokenType() instanceof IKeywordElementType) {
        return "Package names cannot contain Java keywords like '" + string + "'";
      }
      if (string.isEmpty()) {
        return "Package segments must be of non-zero length";
      }
      return string + " is not a valid identifier";
    }
    return null;
  }

  // This method is a copy of android.content.pm.PackageParser#validateName with the
  // error messages tweaked
  @Nullable
  private static String validateName(String name, boolean requiresSeparator) {
    final int N = name.length();
    boolean hasSep = false;
    boolean front = true;
    for (int i=0; i<N; i++) {
      final char c = name.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
        front = false;
        continue;
      }
      if ((c >= '0' && c <= '9') || c == '_') {
        if (!front) {
          continue;
        } else {
          if (c == '_') {
            return "The character '_' cannot be the first character in a package segment";
          } else {
            return "A digit cannot be the first character in a package segment";
          }
        }
      }
      if (c == '.') {
        hasSep = true;
        front = true;
        continue;
      }
      return "The character '" + c + "' is not allowed in Android application package names";
    }
    return hasSep || !requiresSeparator ? null : "The package must have at least one '.' separator";
  }

  public static boolean isIdentifier(@NotNull String candidate) {
    return StringUtil.isJavaIdentifier(candidate) && !JavaLexer.isKeyword(candidate, LanguageLevel.JDK_1_5);
  }

  public static void reportImportErrorToEventLog(String message, String modName, Project project) {
    reportImportErrorToEventLog(message, modName, project, null);
  }

  public static void reportImportErrorToEventLog(String message, String modName, Project project, NotificationListener listener) {
    Notifications.Bus.notify(new Notification(AndroidBundle.message("android.facet.importing.notification.group"),
                                              AndroidBundle.message("android.facet.importing.title", modName),
                                              message, NotificationType.ERROR, listener), project);
    LOG.debug(message);
  }

  public static boolean isPackagePrefix(@NotNull String prefix, @NotNull String name) {
    return name.equals(prefix) || name.startsWith(prefix + ".");
  }

  @NotNull
  public static Set<Module> getSetWithBackwardDependencies(@NotNull Collection<Module> modules) {
    if (modules.isEmpty()) return Collections.emptySet();
    Module next = modules.iterator().next();
    Graph<Module> graph = ModuleManager.getInstance(next.getProject()).moduleGraph();
    final Set<Module> set = new HashSet<>();
    for (Module module : modules) {
      GraphAlgorithms.getInstance().collectOutsRecursively(graph, module, set);
    }
    return set;
  }

  @NotNull
  public static List<String> urlsToOsPaths(@NotNull List<String> urls, @Nullable String sdkHomeCanonicalPath) {
    if (urls.isEmpty()) {
      return Collections.emptyList();
    }
    final List<String> result = new ArrayList<>(urls.size());

    for (String url : urls) {
      if (sdkHomeCanonicalPath != null) {
        url = StringUtil.replace(url, AndroidCommonUtils.SDK_HOME_MACRO, sdkHomeCanonicalPath);
      }
      result.add(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(url)));
    }
    return result;
  }

  @NotNull
  public static String getAndroidSystemDirectoryOsPath() {
    return PathManager.getSystemPath() + File.separator + "android";
  }

  public static boolean isAndroidComponent(@NotNull PsiClass c) {
    final Project project = c.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (String componentClassName : ANDROID_COMPONENT_CLASSES) {
      final PsiClass componentClass = facade.findClass(componentClassName, ProjectScope.getAllScope(project));
      if (componentClass != null && c.isInheritor(componentClass, true)) {
        return true;
      }
    }
    return false;
  }
}
