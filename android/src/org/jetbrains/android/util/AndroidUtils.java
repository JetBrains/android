// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.util;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.TOOLS_URI;
import static com.intellij.openapi.application.ApplicationManager.getApplication;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.android.utils.TraceUtils;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.tree.java.IKeywordElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import java.awt.BorderLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidUtils extends CommonAndroidUtil {
  private static final Logger LOG = Logger.getInstance(AndroidUtils.class);

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

  private static final class LazyHolder {
    static final Lexer JAVA_LEXER = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_5);
  }

  /**
   * The package is used to create a directory (eg: MyApplication/app/src/main/java/src/my/package/name)
   * A windows directory path cannot be longer than 250 chars
   * On unix/mac a directory name cannot be longer than 250 chars
   * On all platforms, aapt fails with really cryptic errors if the package name is longer that ~200 chars
   * Having a sane length for the package also seems a good thing
   */
  private static final int PACKAGE_LENGTH_LIMIT = 100;

  private AndroidUtils() {
  }

  @Override
  public boolean isAndroidProject(@NotNull Project project) {
    return hasAndroidFacets(project);
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull Module module,
                                                        @NotNull VirtualFile file,
                                                        @NotNull Class<T> aClass) {
    return loadDomElement(module.getProject(), file, aClass);
  }

  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull Project project,
                                                        @NotNull VirtualFile file,
                                                        @NotNull Class<T> aClass) {
    return getApplication().runReadAction((Computable<T>)() -> {
      if (project.isDisposed() || !file.isValid()) return null;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile instanceof XmlFile) {
        return loadDomElementWithReadPermission(project, (XmlFile)psiFile, aClass);
      }
      else {
        return null;
      }
    });
  }

  /** This method should be called under a read action. */
  @Nullable
  public static <T extends DomElement> T loadDomElementWithReadPermission(@NotNull Project project,
                                                                          @NotNull XmlFile xmlFile,
                                                                          @NotNull Class<T> aClass) {
    getApplication().assertReadAccessAllowed();
    ProgressManager.checkCanceled();
    DomManager domManager = DomManager.getDomManager(project);
    ProgressManager.checkCanceled();
    DomFileElement<T> element = domManager.getFileElement(xmlFile, aClass);
    return element == null ? null : element.getRootElement();
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
    VirtualFile child = parent.findChild(name);
    return child == null ? parent.createChildDirectory(project, name) : child;
  }

  @Nullable
  public static PsiFile getContainingFile(@NotNull PsiElement element) {
    return element instanceof PsiFile ? (PsiFile)element : element.getContainingFile();
  }

  public static void navigateTo(@NotNull PsiElement[] targets, @Nullable RelativePoint pointToShowPopup) {
    if (targets.length == 0) {
      JComponent renderer = HintUtil.createErrorLabel("Empty text");
      JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(renderer, renderer).createPopup();
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
          PsiFile file = getContainingFile(element);
          return file != null ? file.getName() : super.getElementText(element);
        }

        @Override
        public String getContainerText(PsiElement element, String name) {
          PsiFile file = getContainingFile(element);
          PsiDirectory dir = file != null ? file.getContainingDirectory() : null;
          return dir == null ? "" : '(' + dir.getName() + ')';
        }
      };
      JBPopup popup = NavigationUtil.getPsiElementPopup(targets, renderer, null);
      popup.show(pointToShowPopup);
    }
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
  public static AndroidFacet addAndroidFacetInWriteAction(@NotNull Module module,
                                                          @NotNull VirtualFile contentRoot,
                                                          boolean library) {
    return WriteAction.compute(() -> addAndroidFacet(module, contentRoot, library));
  }

  @NotNull
  public static AndroidFacet addAndroidFacet(Module module, @NotNull VirtualFile contentRoot,
                                             boolean library) {
    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = facetManager.createModifiableModel();
    AndroidFacet facet = model.getFacetByType(AndroidFacet.ID);

    if (facet == null) {
      facet = facetManager.createFacet(AndroidFacet.getFacetType(), "Android", null);
      setUpAndroidFacetConfiguration(facet, contentRoot.getPath());
      if (library) {
        facet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);
      }
      model.addFacet(facet);
    }
    model.commit();

    return facet;
  }

  public static void setUpAndroidFacetConfiguration(@NotNull AndroidFacet androidFacet, @NotNull String baseDirectoryPath) {
    setUpAndroidFacetConfiguration(androidFacet.getModule(), androidFacet.getConfiguration(), baseDirectoryPath);
  }

  public static void setUpAndroidFacetConfiguration(@NotNull Module module,
                                                    @NotNull AndroidFacetConfiguration androidFacetConfiguration,
                                                    @NotNull String baseDirectoryPath) {
    String s = AndroidRootUtil.getPathRelativeToModuleDir(module, baseDirectoryPath);
    if (s == null || s.isEmpty()) {
      return;
    }
    AndroidFacetProperties properties = androidFacetConfiguration.getState();
    properties.GEN_FOLDER_RELATIVE_PATH_APT = '/' + s + properties.GEN_FOLDER_RELATIVE_PATH_APT;
    properties.GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + s + properties.GEN_FOLDER_RELATIVE_PATH_AIDL;
    properties.MANIFEST_FILE_RELATIVE_PATH = '/' + s + properties.MANIFEST_FILE_RELATIVE_PATH;
    properties.RES_FOLDER_RELATIVE_PATH = '/' + s + properties.RES_FOLDER_RELATIVE_PATH;
    properties.ASSETS_FOLDER_RELATIVE_PATH = '/' + s + properties.ASSETS_FOLDER_RELATIVE_PATH;
    properties.LIBS_FOLDER_RELATIVE_PATH = '/' + s + properties.LIBS_FOLDER_RELATIVE_PATH;
    properties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH = '/' + s + properties.PROGUARD_LOGS_FOLDER_RELATIVE_PATH;

    properties.RES_OVERLAY_FOLDERS.replaceAll(overlayFolder -> '/' + s + overlayFolder);
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
    RunManager runManager = RunManager.getInstance(module.getProject());
    List<RunConfiguration> configurations = runManager.getConfigurationsList(type);

    TargetSelectionMode alternative = null;

    if (!configurations.isEmpty()) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase) {
          AndroidRunConfigurationBase runConfig = (AndroidRunConfigurationBase)configuration;
          TargetSelectionMode targetMode = runConfig.getDeployTargetContext().getTargetSelectionMode();
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
    return ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).stream()
      .filter(facet -> facet.getConfiguration().isAppProject())
      .sorted(Comparator.comparing(facet -> facet.getModule().getName()))
      .collect(Collectors.toList());
  }

  @NotNull
  public static List<AndroidFacet> getAndroidLibraryDependencies(@NotNull Module module) {
    List<AndroidFacet> depFacets = new ArrayList<>();

    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            AndroidFacet depFacet = AndroidFacet.getInstance(depModule);

            if (depFacet != null && depFacet.getConfiguration().canBeDependency()) {
              depFacets.add(depFacet);
            }
          }
        }
      }
    }
    return depFacets;
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

  public static void showStackStace(@Nullable Project project, @NotNull Throwable[] throwables) {
    StringBuilder messageBuilder = new StringBuilder();

    for (Throwable t : throwables) {
      if (messageBuilder.length() > 0) {
        messageBuilder.append("\n\n");
      }
      messageBuilder.append(TraceUtils.getStackTrace(t));
    }

    DialogWrapper wrapper = new DialogWrapper(project, false) {
      {
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea textArea = new JTextArea(messageBuilder.toString());
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

    String packageManagerCheck = validateName(name);
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
  public static String validatePackageName(@Nullable String packageName) {
    packageName = (packageName == null) ? "" : packageName;
    if (packageName.length() >= PACKAGE_LENGTH_LIMIT) {
      return AndroidBundle.message("android.wizard.module.package.too.long");
    }
    return AndroidUtils.validateAndroidPackageName(packageName);
  }

  @Nullable
  public static String isReservedKeyword(@NotNull String string) {
    Lexer lexer = LazyHolder.JAVA_LEXER;
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
  private static String validateName(String name) {
    int N = name.length();
    boolean hasSep = false;
    boolean front = true;
    for (int i=0; i<N; i++) {
      char c = name.charAt(i);
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
    return hasSep ? null : "The package must have at least one '.' separator";
  }

  public static boolean isIdentifier(@NotNull String candidate) {
    return StringUtil.isJavaIdentifier(candidate) && !JavaLexer.isKeyword(candidate, LanguageLevel.JDK_1_5);
  }

  public static void reportImportErrorToEventLog(String message, String modName, Project project, NotificationListener listener) {
    Notification notification = new Notification(NotificationGroup.createIdWithTitle(
      "Importing Error", AndroidBundle.message("android.facet.importing.notification.group")),
                                              AndroidBundle.message("android.facet.importing.title", modName),
                                              message, NotificationType.ERROR);
    if (listener != null) notification.setListener(listener);
    notification.notify(project);
    LOG.debug(message);
  }

  public static boolean isPackagePrefix(@NotNull String prefix, @NotNull String name) {
    return name.equals(prefix) || name.startsWith(prefix + ".");
  }

  @NotNull
  public static Set<Module> getSetWithBackwardDependencies(@NotNull Module module) {
    Graph<Module> graph = ModuleManager.getInstance(module.getProject()).moduleGraph();
    Set<Module> set = new HashSet<>();
    GraphAlgorithms.getInstance().collectOutsRecursively(graph, module, set);
    return set;
  }

  @NotNull
  public static List<String> urlsToOsPaths(@NotNull List<String> urls, @Nullable String sdkHomeCanonicalPath) {
    if (urls.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>(urls.size());

    for (String url : urls) {
      if (sdkHomeCanonicalPath != null) {
        url = StringUtil.replace(url, AndroidFacetProperties.SDK_HOME_MACRO, sdkHomeCanonicalPath);
      }
      result.add(FileUtilRt.toSystemDependentName(VfsUtilCore.urlToPath(url)));
    }
    return result;
  }

  public static boolean isAndroidComponent(@NotNull PsiClass c) {
    Project project = c.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (String componentClassName : ANDROID_COMPONENT_CLASSES) {
      PsiClass componentClass = facade.findClass(componentClassName, ProjectScope.getAllScope(project));
      if (componentClass != null && c.isInheritor(componentClass, true)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the project contains a module with an Android, an Apk or a Gradle facet.
   * See also {@link com.android.tools.idea.FacetUtils#hasAndroidOrApkFacet}.
   */
  public static boolean hasAndroidFacets(@NotNull Project project) {
    ProjectFacetManager facetManager = ProjectFacetManager.getInstance(project);
    return facetManager.hasFacets(AndroidFacet.ID) ||
           facetManager.hasFacets(ApkFacet.ID) ||
           facetManager.hasFacets(GradleFacet.getFacetTypeId());
  }

  /**
   * Looks up the declared associated context/activity for the given XML file and
   * returns the resolved fully qualified name if found
   *
   * @param module module containing the XML file
   * @param xmlFile the XML file
   * @return the associated fully qualified name, or null
   */
  @Nullable
  public static String getDeclaredContextFqcn(@NotNull Module module, @NotNull XmlFile xmlFile) {
    String context = AndroidPsiUtils.getRootTagAttributeSafely(xmlFile, ATTR_CONTEXT, TOOLS_URI);
    if (context != null && !context.isEmpty()) {
      boolean startsWithDot = context.charAt(0) == '.';
      if (startsWithDot || context.indexOf('.') == -1) {
        // Prepend application package
        String pkg = ProjectSystemUtil.getModuleSystem(module).getPackageName();
        return startsWithDot ? pkg + context : pkg + '.' + context;
      }
      return context;
    }
    return null;
  }

  /**
   * Looks up the declared associated context/activity for the given XML file and
   * returns the associated class, if found
   *
   * @param module module containing the XML file
   * @param xmlFile the XML file
   * @return the associated class, or null
   */
  @Nullable
  public static PsiClass getContextClass(@NotNull Module module, @NotNull XmlFile xmlFile) {
    String fqn = getDeclaredContextFqcn(module, xmlFile);
    if (fqn != null) {
      Project project = module.getProject();
      return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    }
    return null;
  }

  /**
   * Returns the root tag for the given {@link PsiFile}, if any, acquiring the read
   * lock to do so if necessary
   *
   * @param file the file to look up the root tag for
   * @return the corresponding root tag, if any
   */
  @Nullable
  public static String getRootTagName(@NotNull PsiFile file) {
    ResourceFolderType folderType = IdeResourcesUtil.getFolderType(file);
    if (folderType == ResourceFolderType.XML || folderType == ResourceFolderType.MENU || folderType == ResourceFolderType.DRAWABLE) {
      if (file instanceof XmlFile) {
        XmlTag rootTag = AndroidPsiUtils.getRootTagSafely(((XmlFile)file));
        return rootTag == null ? null : rootTag.getName();
      }
    }
    return null;
  }
}
