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

import static com.intellij.openapi.application.ApplicationManager.getApplication;

import com.android.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.parsers.PsiXmlFile;
import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.android.tools.rendering.AndroidXmlFiles;
import com.android.utils.TraceUtils;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
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
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  @NonNls public static final String LAUNCH_CATEGORY_NAME = "android.intent.category.LAUNCHER";
  @NonNls public static final String LEANBACK_LAUNCH_CATEGORY_NAME = "android.intent.category.LEANBACK_LAUNCHER";
  @NonNls public static final String DEFAULT_CATEGORY_NAME = "android.intent.category.DEFAULT";

  @NonNls public static final String INSTRUMENTATION_RUNNER_BASE_CLASS = SdkConstants.CLASS_INSTRUMENTATION;
  @NonNls public static final String SERVICE_CLASS_NAME = SdkConstants.CLASS_SERVICE;
  @NonNls public static final String RECEIVER_CLASS_NAME = SdkConstants.CLASS_BROADCASTRECEIVER;
  @NonNls public static final String PROVIDER_CLASS_NAME = SdkConstants.CLASS_CONTENTPROVIDER;

  @NonNls public static final String TAG_LINEAR_LAYOUT = SdkConstants.LINEAR_LAYOUT;

  private static class LazyHolder {
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
    return ProjectSystemUtil.getProjectSystem(project).isAndroidProject();
  }

  // TODO(b/291955340): Should have @RequiresBackgroundThread
  @Nullable
  public static <T extends DomElement> T loadDomElement(@NotNull Module module,
                                                        @NotNull VirtualFile file,
                                                        @NotNull Class<T> aClass) {
    return loadDomElement(module.getProject(), file, aClass);
  }

  // TODO(b/291955340): Should have @RequiresBackgroundThread
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

  // TODO(b/291955340): Should have @RequiresBackgroundThread
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

  // Note to the reader: originally, this loop attempted to detect an existing run configuration of the same type
  // and the same module, and would short-circuit with the target selection mode of that configuration if present.
  // However, the detection was buggy, because it was an identity comparison between a Module and a
  // RunConfigurationModule, so the short-circuit was never taken in 13 years of the code existing.  It's not clear
  // that this routine has any value at all (it will tend to choose the target selection mode from the last
  // current run configuration in some arbitrary order) and in any case I would expect that the vast majority of
  // projects have exactly one TargetSelectionMode available.
  @Nullable
  public static TargetSelectionMode getDefaultTargetSelectionMode(@NotNull Project project,
                                                                  @NotNull ConfigurationType type,
                                                                  @NonNls ConfigurationType alternativeType) {
    RunManager runManager = RunManager.getInstance(project);
    List<RunConfiguration> configurations = runManager.getConfigurationsList(type);

    TargetSelectionMode alternative = null;

    if (!configurations.isEmpty()) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase runConfig) {
          alternative = runConfig.getDeployTargetContext().getTargetSelectionMode();
        }
      }
    }
    if (alternative != null) {
      return alternative;
    }
    configurations = runManager.getConfigurationsList(alternativeType);
    if (!configurations.isEmpty()) {
      for (RunConfiguration configuration : configurations) {
        if (configuration instanceof AndroidRunConfigurationBase runConfig) {
          return runConfig.getDeployTargetContext().getTargetSelectionMode();
        }
      }
    }
    return null;
  }

  public static boolean equalIgnoringDelimiters(@NotNull String s1, @NotNull String s2) {
    if (s1.length() != s2.length()) return false;
    for (int i = 0, n = s1.length(); i < n; i++) {
      char c1 = s1.charAt(i);
      char c2 = s2.charAt(i);
      if (Character.isLetterOrDigit(c1) || Character.isLetterOrDigit(c2)) {
        if (c1 != c2) return false;
      }
    }
    return true;
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
    return StringUtil.isJavaIdentifier(candidate) && !PsiUtil.isKeyword(candidate, LanguageLevel.JDK_1_5);
  }

  public static boolean isPackagePrefix(@NotNull String prefix, @NotNull String name) {
    return name.equals(prefix) || name.startsWith(prefix + ".");
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
      result.add(FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(url)));
    }
    return result;
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
    return AndroidXmlFiles.getDeclaredContextFqcn(ProjectSystemUtil.getModuleSystem(module).getPackageName(), new PsiXmlFile(xmlFile));
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
}
