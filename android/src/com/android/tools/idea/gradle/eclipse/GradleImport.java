/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.gradle.eclipse;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.util.PropertiesUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.PositionXmlParser;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.*;
import com.google.common.io.Files;
import com.google.common.primitives.Bytes;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_NDK;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_SDK;
import static com.android.xml.AndroidManifest.NODE_INSTRUMENTATION;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

/**
 * Importer which can generate Android Gradle projects.
 * <p/>
 * It currently only supports importing ADT projects, so it will require
 * some tweaks to handle importing from other types of projects.
 * <p/>
 * The importer primarily imports complete ADT projects into complete (new) Gradle
 * projects, but it can also be used to import one or more ADT projects into an
 * existing Gradle project as new modules. See {@link #exportIntoProject} for
 * details on how to do this.
 * <p/>
 * TODO:
 * <ul>
 * <li>Migrate SDK folder from local.properties. If should make doubly sure that
 * the repository you point to contains the app support library and other
 * libraries that may be needed.</li>
 * <li>Consider whether I can make this import mechanism work for Maven and plain
 * sources as well?</li>
 * <li>Make it optional whether we replace the directory structure with the Gradle one?</li>
 * <li>Allow migrating a project in-place?</li>
 * <li>If I have a workspace, check to see if there are problem markers and if
 * so warn that the project may not be buildable</li>
 * <li>Optional:  at the end of the import, migrate Eclipse settings too --
 * such as code styles, compiler flags (especially those for the
 * project), ask about enabling eclipse key bindings, etc?</li>
 * <li>If replaceJars=false, insert *comments* in the source code for potential
 * replacements such that users don't forget and consider switching in the future</li>
 * <li>Figure out if we can reuse fragments from the default freemarker templates for
 * the code generation part.</li>
 * <li>Allow option to preserve module nesting hierarchy. It currently flattens.</li>
 * <li>Make it possible to use this wizard to migrate an already exported Eclipse project?</li>
 * <li>Consider making the export create an HTML file and open in browser?</li>
 * </ul>
 */
public class GradleImport {
  public static final String NL = SdkUtils.getLineSeparator();
  public static final int CURRENT_COMPILE_VERSION = 23;
  public static final String CURRENT_BUILD_TOOLS_VERSION = MIN_BUILD_TOOLS_VERSION;
  public static final String ANDROID_GRADLE_PLUGIN = GRADLE_PLUGIN_NAME + GRADLE_PLUGIN_RECOMMENDED_VERSION;
  public static final String MAVEN_URL_PROPERTY = "android.mavenRepoUrl";
  public static final String ECLIPSE_DOT_CLASSPATH = ".classpath";
  public static final String ECLIPSE_DOT_PROJECT = ".project";
  public static final String IMPORT_SUMMARY_TXT = "import-summary.txt";
  public static final String MAVEN_REPOSITORY;

  static {
    String repository = System.getProperty(MAVEN_URL_PROPERTY);
    if (repository == null) {
      repository = System.getenv("MAVEN_URL"); // as used by the CI server, and also all other test projects
    }

    if (repository == null) {
      repository = "jcenter()";
    }
    else {
      repository = "jcenter();" + NL + "        " +
                   "maven { url '" + repository + "' }";
    }
    MAVEN_REPOSITORY = repository;
  }

  /**
   * Whether we should place the repository definitions in the global build.gradle rather
   * than in each module
   */
  static final boolean DECLARE_GLOBAL_REPOSITORIES = true;
  private static final String WORKSPACE_PROPERTY = "android.eclipseWorkspace";
  private final List<String> myWarnings = Lists.newArrayList();
  private final List<String> myErrors = Lists.newArrayList();
  private List<? extends ImportModule> myRootModules;
  private Set<ImportModule> myModules;
  private ImportSummary mySummary;
  private File myWorkspaceLocation;
  private File myGradleWrapperLocation;
  private File mySdkLocation;
  private File myNdkLocation;
  private Set<String> myHandledJars = Sets.newHashSet();
  private Map<String, File> myWorkspaceProjects;
  /**
   * Whether we should convert project names to lowercase module names
   */
  private boolean myGradleNameStyle = true;
  /**
   * Whether we should try to replace jars with dependencies
   */
  private boolean myReplaceJars = true;
  /**
   * Whether we should try to replace libs with dependencies
   */
  private boolean myReplaceLibs = true;
  /**
   * Whether the importer is in "import into existing project" mode. In that case,
   * some different choices are made; for example, we don't rewrite the module name
   * to "app" when importing a single project; we preserve the project name.
   */
  private boolean myImportIntoExisting;
  /**
   * Whether we should emit per-module repository definitions
   */
  @SuppressWarnings("PointlessBooleanExpression")
  private boolean myPerModuleRepositories = !DECLARE_GLOBAL_REPOSITORIES;
  private Map<String, File> myPathMap = Maps.newTreeMap();
  /**
   * Map of modules user chose to import with their new names. Can be
   * <code>null</code> when all modules will be imported
   */
  private Map<File, String> mySelectedModules;
  private boolean myDefaultEncodingInitialized;
  private Charset myDefaultEncoding;
  private Map<File, EclipseProject> myProjectMap = Maps.newHashMap();

  public GradleImport() {
    String workspace = System.getProperty(WORKSPACE_PROPERTY);
    if (workspace != null) {
      myWorkspaceLocation = new File(workspace);
    }
  }

  public static boolean isEclipseProjectDir(@Nullable File file) {
    return file != null &&
           file.isDirectory() &&
           new File(file, ECLIPSE_DOT_CLASSPATH).exists() &&
           new File(file, ECLIPSE_DOT_PROJECT).exists();
  }

  public static boolean isAdtProjectDir(@Nullable File file) {
    return new File(file, ANDROID_MANIFEST_XML).exists() &&
           (isEclipseProjectDir(file) ||
            (new File(file, FD_RES).exists() && ((new File(file, FD_SOURCES).exists() || new File(file, "jni").exists()))));
  }

  @Nullable
  private static File getDirFromLocalProperties(@NonNull File projectDir, @NonNull String property) {
    File localProperties = new File(projectDir, FN_LOCAL_PROPERTIES);
    if (localProperties.exists()) {
      try {
        Properties properties = PropertiesUtil.getProperties(localProperties);
        if (properties != null) {
          String sdk = properties.getProperty(property);
          if (sdk != null) {
            File dir = new File(sdk);
            if (dir.exists()) {
              return dir;
            }
            else {
              dir = new File(sdk.replace('/', separatorChar));
              if (dir.exists()) {
                return dir;
              }
            }
          }
        }
      }
      catch (IOException e) {
        // ignore properties
      }
    }

    return null;
  }

  public static boolean isEclipseWorkspaceDir(@NonNull File file) {
    return file.isDirectory() && new File(file, ".metadata" + separator + "version.ini").exists();
  }

  private static String generateProguardFileList(List<File> localRules, List<File> sdkRules) {
    assert !localRules.isEmpty() || !sdkRules.isEmpty();
    StringBuilder sb = new StringBuilder();
    for (File rule : sdkRules) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append("getDefaultProguardFile('");
      sb.append(escapeGroovyStringLiteral(rule.getName()));
      sb.append("')");
    }

    for (File rule : localRules) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append("'");
      // Note: project config files are flattened into the module structure (see
      // ImportModule#copyInto handler)
      sb.append(escapeGroovyStringLiteral(rule.getName()));
      sb.append("'");
    }

    return sb.toString();
  }

  private static void appendDependencies(@NonNull StringBuilder sb, @NonNull ImportModule module) throws IOException {
    if (!module.getDirectDependencies().isEmpty() ||
        !module.getDependencies().isEmpty() ||
        !module.getJarDependencies().isEmpty() ||
        !module.getTestDependencies().isEmpty() ||
        !module.getTestJarDependencies().isEmpty()) {
      sb.append(NL);
      sb.append("dependencies {").append(NL);
      for (ImportModule lib : module.getDirectDependencies()) {
        if (lib.isReplacedWithDependency()) {
          continue;
        }
        sb.append("    compile project('").append(lib.getModuleReference()).append("')").append(NL);
      }
      for (GradleCoordinate dependency : module.getDependencies()) {
        sb.append("    compile '").append(dependency.toString()).append("'").append(NL);
      }
      for (File jar : module.getJarDependencies()) {
        String path = jar.getPath().replace(separatorChar, '/'); // Always / in gradle
        sb.append("    compile files('").append(escapeGroovyStringLiteral(path)).append("')").append(NL);
      }
      for (GradleCoordinate dependency : module.getTestDependencies()) {
        sb.append("    androidTestCompile '").append(dependency.toString()).append("'").append(NL);
      }
      for (File jar : module.getTestJarDependencies()) {
        String path = jar.getPath().replace(separatorChar, '/');
        sb.append("    androidTestCompile files('").append(escapeGroovyStringLiteral(path)).append("')").append(NL);
      }
      sb.append("}").append(NL);
    }
  }

  @NotNull
  public static String escapeGroovyStringLiteral(@NotNull String s) {
    StringBuilder sb = new StringBuilder(s.length() + 5);
    for (int i = 0, n = s.length(); i < n; i++) {
      char c = s.charAt(i);
      if (c == '\\' || c == '\'') {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }

  private static String formatMessage(@Nullable String project, @Nullable File file, @NonNull String message) {
    StringBuilder sb = new StringBuilder();
    if (project != null) {
      sb.append("Project ").append(project).append(":");
    }
    if (file != null) {
      sb.append(file.getPath());
      sb.append(":\n");
    }

    sb.append(message);

    return sb.toString();
  }

  /**
   * Returns true if the given file should be ignored (note: this may not return
   * true for files inside ignored folders, so to determine if a given file should
   * really be ignored you should check all ancestors as well, or only call this as
   * part of a recursive directory traversal)
   */
  static boolean isIgnoredFile(File file) {
    String name = file.getName();
    return name.equals(".svn") ||
           name.equals(".git") ||
           name.equals(".hg") ||
           name.equals(".DS_Store") ||
           name.endsWith("~") && name.length() > 1;
  }

  /**
   * Copies the file with the given source encoding to a UTF-8 text file
   */
  private static void copyTextFileWithEncoding(@NonNull File source, @NonNull File dest, @NonNull Charset sourceEncoding)
    throws IOException {
    if (!Charsets.UTF_8.equals(sourceEncoding)) {
      String text = Files.toString(source, sourceEncoding);
      Files.write(text, dest, Charsets.UTF_8);
    }
    else {
      // Already using the right encoding
      Files.copy(source, dest);
    }
  }

  /**
   * Returns true if this file is believed to be a source file (so it should be encoded
   * as a UTF-8 file in the imported project
   *
   * @param file the file to check
   * @return true if it is known to be a source file
   */
  @VisibleForTesting
  public static boolean isTextFile(@NonNull File file) {
    String name = file.getName();
    return name.endsWith(DOT_JAVA) ||
           name.endsWith(DOT_XML) ||
           name.endsWith(DOT_AIDL) ||
           name.endsWith(DOT_FS) ||
           name.endsWith(DOT_RS) ||
           name.endsWith(DOT_RSH) ||
           name.endsWith(DOT_RSH) ||
           name.endsWith(DOT_TXT) ||
           name.endsWith(DOT_GRADLE) ||
           name.endsWith(DOT_PROPERTIES) ||
           name.endsWith(".cfg") ||
           name.endsWith(".pro") ||
           name.endsWith(".h") ||
           name.endsWith(".c") ||
           name.endsWith(".cpp");
  }

  /**
   * Computes the relative path for the given file inside another directory
   */
  @Nullable
  public static File computeRelativePath(@NonNull File canonicalBase, @NonNull File file) throws IOException {
    File canonical = file.getCanonicalFile();
    String canonicalPath = canonical.getPath();
    if (canonicalPath.startsWith(canonicalBase.getPath())) {
      int length = canonicalBase.getPath().length();
      if (canonicalPath.length() == length) {
        return new File(".");
      }
      else if (canonicalPath.charAt(length) == separatorChar) {
        return new File(canonicalPath.substring(length + 1));
      }
      else {
        return new File(canonicalPath.substring(length));
      }
    }

    return null;
  }

  /**
   * Imports the given projects. Note that this just reads in the project state;
   * it does not actually write out a Gradle project. For that, you should call
   * {@link #exportProject(java.io.File, boolean)}.
   *
   * @param projectDirs the project directories to import
   * @throws IOException if something is wrong
   */
  public void importProjects(@NonNull List<File> projectDirs) throws IOException {
    mySummary = new ImportSummary(this);
    myProjectMap.clear();
    myHandledJars.clear();
    myWarnings.clear();
    myErrors.clear();
    myWorkspaceProjects = null;
    myRootModules = Collections.emptyList();
    myModules = Sets.newHashSet();

    for (File file : projectDirs) {
      if (file.isFile()) {
        assert !file.isDirectory();
        file = file.getParentFile();
      }

      guessWorkspace(file);

      if (isAdtProjectDir(file)) {
        guessSdk(file);
        guessNdk(file);

        try {
          EclipseProject.getProject(this, file);
        }
        catch (ImportException e) {
          // Already recorded
          return;
        }
        catch (Exception e) {
          reportError(null, file, e.toString(), false);
          return;
        }
      }
      else {
        reportError(null, file, "Not a recognized project: " + file, false);
        return;
      }
    }

    // Find unique projects. (We can register projects under multiple paths
    // if the dir and the canonical dir differ, so pick unique values here)
    Set<EclipseProject> projects = Sets.newHashSet(myProjectMap.values());
    myRootModules = EclipseProject.performImport(this, projects);
    for (ImportModule module : myRootModules) {
      myModules.add(module);
      myModules.addAll(module.getAllDependencies());
    }
  }

  /**
   * Sets location of gradle wrapper to copy into exported project, if known
   */
  @NonNull
  public GradleImport setGradleWrapperLocation(@NonNull File gradleWrapper) {
    myGradleWrapperLocation = gradleWrapper;
    return this;
  }

  /**
   * Returns the location of the SDK to use with the import, if known
   */
  @Nullable
  public File getSdkLocation() {
    return mySdkLocation;
  }

  /**
   * Sets location of the SDK to use with the import, if known
   */
  @NonNull
  public GradleImport setSdkLocation(@Nullable File sdkLocation) {
    mySdkLocation = sdkLocation;
    return this;
  }

  /**
   * Gets location of the SDK to use with the import, if known
   */
  @Nullable
  public File getNdkLocation() {
    return myNdkLocation;
  }

  /**
   * Sets location of the SDK to use with the import, if known
   */
  @NonNull
  public GradleImport setNdkLocation(@Nullable File ndkLocation) {
    myNdkLocation = ndkLocation;
    return this;
  }

  /**
   * Gets location of Eclipse workspace, if known
   */
  @Nullable
  public File getEclipseWorkspace() {
    return myWorkspaceLocation;
  }

  /**
   * Sets location of Eclipse workspace, if known
   */
  public GradleImport setEclipseWorkspace(@NonNull File workspace) {
    myWorkspaceLocation = workspace;
    assert myWorkspaceLocation.exists() : workspace.getPath();
    myWorkspaceProjects = null;
    return this;
  }

  /**
   * Whether import should attempt to replace jars with dependencies
   */
  public boolean isReplaceJars() {
    return myReplaceJars;
  }

  /**
   * Whether import should attempt to replace jars with dependencies
   */
  @NonNull
  public GradleImport setReplaceJars(boolean replaceJars) {
    myReplaceJars = replaceJars;
    return this;
  }

  /**
   * Whether import should attempt to replace inlined library projects with dependencies
   */
  public boolean isReplaceLibs() {
    return myReplaceLibs;
  }

  /**
   * Whether import should attempt to replace inlined library projects with dependencies
   */
  public GradleImport setReplaceLibs(boolean replaceLibs) {
    myReplaceLibs = replaceLibs;
    return this;
  }

  /**
   * Whether we're in "import into existing project" mode, or import complete new project
   * mode (the default)
   */
  public boolean isImportIntoExisting() {
    return myImportIntoExisting;
  }

  /**
   * Sets whether we're in "import into existing project" mode, or import complete new project
   * mode (the default). When importing into existing projects some different behaviors are
   * applied; for example, we don't change the module name to "app" when there is just one
   * project being imported.
   */
  public void setImportIntoExisting(boolean importIntoExisting) {
    myImportIntoExisting = importIntoExisting;
  }

  /**
   * Returns whether the importer emits the repository definitions in each module's build.gradle
   * rather than at the top level in the shared build.gradle
   */
  public boolean isPerModuleRepositories() {
    return myPerModuleRepositories;
  }

  /**
   * Sets whether the importer emits the repository definitions in each module's build.gradle
   * rather than at the top level in the shared build.gradle
   */
  public void setPerModuleRepositories(boolean perModuleRepositories) {
    myPerModuleRepositories = perModuleRepositories;
  }

  /**
   * Whether import should lower-case module names from ADT project names
   */
  public boolean isGradleNameStyle() {
    return myGradleNameStyle;
  }

  /**
   * Whether import should lower-case module names from ADT project names
   */
  @NonNull
  public GradleImport setGradleNameStyle(boolean lowerCase) {
    myGradleNameStyle = lowerCase;
    return this;
  }

  private void guessWorkspace(@NonNull File projectDir) {
    if (myWorkspaceLocation == null) {
      File dir = projectDir.getParentFile();
      while (dir != null) {
        if (isEclipseWorkspaceDir(dir)) {
          setEclipseWorkspace(dir);
          break;
        }
        dir = dir.getParentFile();
      }
    }
  }

  private void guessSdk(@NonNull File projectDir) {
    if (mySdkLocation == null) {
      mySdkLocation = getDirFromLocalProperties(projectDir, PROPERTY_SDK);

      if (mySdkLocation == null && myWorkspaceLocation != null) {
        mySdkLocation = getDirFromWorkspaceSetting(getAdtSettingsFile(), "com.android.ide.eclipse.adt.sdk");
      }
    }
  }

  private void guessNdk(@NonNull File projectDir) {
    if (myNdkLocation == null) {
      myNdkLocation = getDirFromLocalProperties(projectDir, PROPERTY_NDK);

      if (myNdkLocation == null && myWorkspaceLocation != null) {
        myNdkLocation = getDirFromWorkspaceSetting(getNdkSettingsFile(), "ndkLocation");
      }
    }
  }

  @Nullable
  private Charset getEncodingFromWorkspaceSetting() {
    if (myWorkspaceLocation != null && !myDefaultEncodingInitialized) {
      myDefaultEncodingInitialized = true;
      File settings = getEncodingSettingsFile();
      if (settings.exists()) {
        try {
          Properties properties = PropertiesUtil.getProperties(settings);
          if (properties != null) {
            String encodingName = properties.getProperty("encoding");
            if (encodingName != null) {
              try {
                myDefaultEncoding = Charset.forName(encodingName);
              }
              catch (UnsupportedCharsetException uce) {
                reportWarning((ImportModule)null, settings, "Unknown charset " + encodingName);
              }

            }
          }
        }
        catch (IOException e) {
          // ignore properties
        }
      }
    }

    return myDefaultEncoding;
  }

  @Nullable
  private File getDirFromWorkspaceSetting(@NonNull File settings, @NonNull String property) {
    //noinspection VariableNotUsedInsideIf
    if (myWorkspaceLocation != null) {
      if (settings.exists()) {
        try {
          Properties properties = PropertiesUtil.getProperties(settings);
          if (properties != null) {
            String path = properties.getProperty(property);
            if (path == null) {
              return null;
            }
            File dir = new File(path);
            if (dir.exists()) {
              return dir;
            }
            else {
              dir = new File(path.replace('/', separatorChar));
              if (dir.exists()) {
                return dir;
              }
            }
          }
        }
        catch (IOException e) {
          // Ignore workspace data
        }
      }
    }

    return null;
  }

  @Nullable
  public File resolveWorkspacePath(@Nullable EclipseProject fromProject, @NonNull String path, boolean record) {
    if (path.isEmpty()) {
      return null;
    }

    // If file within project, must match on all prefixes
    for (Map.Entry<String, File> entry : myPathMap.entrySet()) {
      String workspacePath = entry.getKey();
      File file = entry.getValue();
      if (file != null && path.startsWith(workspacePath)) {
        if (path.equals(workspacePath)) {
          return file;
        }
        else {
          path = path.substring(workspacePath.length());
          if (path.charAt(0) == '/' || path.charAt(0) == separatorChar) {
            path = path.substring(1);
          }
          File resolved = new File(file, path.replace('/', separatorChar));
          if (resolved.exists()) {
            return resolved;
          }
        }
      }
    }

    if (fromProject != null && myWorkspaceLocation == null) {
      guessWorkspace(fromProject.getDir());
    }

    if (myWorkspaceLocation != null) {
      // Is the file present directly in the workspace?
      char first = path.charAt(0);
      if (first != '/') {
        return null;
      }
      File f = new File(myWorkspaceLocation, path.substring(1).replace('/', separatorChar));
      if (f.exists()) {
        myPathMap.put(path, f);
        return f;
      }

      // Other files may be in other file systems, mapped by a .location link in the
      // workspace metadata
      if (myWorkspaceProjects == null) {
        myWorkspaceProjects = Maps.newHashMap();
        File projectDir = new File(myWorkspaceLocation, ".metadata" +
                                                       separator +
                                                       ".plugins" +
                                                       separator +
                                                       "org.eclipse.core.resources" +
                                                       separator +
                                                       ".projects");
        File[] projects = projectDir.exists() ? projectDir.listFiles() : null;
        byte[] target = "URI//file:".getBytes(Charsets.US_ASCII);
        if (projects != null) {
          for (File project : projects) {
            File location = new File(project, ".location");
            if (location.exists()) {
              try {
                byte[] bytes = Files.toByteArray(location);
                int start = Bytes.indexOf(bytes, target);
                if (start != -1) {
                  int end = start + target.length;
                  for (; end < bytes.length; end++) {
                    if (bytes[end] == (byte)0) {
                      break;
                    }
                  }
                  try {
                    int length = end - start;
                    String s = new String(bytes, start, length, UTF_8);
                    s = s.substring(5); // skip URI//
                    File file = SdkUtils.urlToFile(s);
                    if (file.exists()) {
                      String name = project.getName();
                      myWorkspaceProjects.put('/' + name, file);
                      //noinspection ConstantConditions
                    }
                  }
                  catch (Throwable t) {
                    // Ignore binary data we can't read
                  }
                }
              }
              catch (IOException e) {
                reportWarning((ImportModule)null, location, "Can't read .location file");
              }
            }
          }
        }
      }

      // Is it just a project root?
      File project = myWorkspaceProjects.get(path);
      if (project != null) {
        myPathMap.put(path, project);
        return project;
      }

      // If file within project, must match on all prefixes
      for (Map.Entry<String, File> entry : myWorkspaceProjects.entrySet()) {
        String workspacePath = entry.getKey();
        File file = entry.getValue();
        if (file != null && path.startsWith(workspacePath)) {
          if (path.equals(workspacePath)) {
            return file;
          }
          else {
            path = path.substring(workspacePath.length());
            if (path.charAt(0) == '/' || path.charAt(0) == separatorChar) {
              path = path.substring(1);
            }
            File resolved = new File(file, path.replace('/', separatorChar));
            if (resolved.exists()) {
              return resolved;
            }
          }
        }
      }

      // Record path as one we need to resolve
      if (record) {
        myPathMap.put(path, null);
      }
    }
    else if (record) {
      // Record path as one we need to resolve
      myPathMap.put(path, null);
    }

    return null;
  }

  public void exportProject(@NonNull File destDir, boolean allowNonEmpty) throws IOException {
    mySummary.setDestDir(destDir);
    if (!isImportIntoExisting()) {
      createDestDir(destDir, allowNonEmpty);
      createProjectBuildGradle(new File(destDir, FN_BUILD_GRADLE));

      exportGradleWrapper(destDir);
      exportLocalProperties(destDir);
    }
    exportSettingsGradle(new File(destDir, FN_SETTINGS_GRADLE), isImportIntoExisting());
    for (ImportModule module : getModulesToImport()) {
      exportModule(new File(destDir, module.getModuleName()), module);
    }

    mySummary.write(new File(destDir, IMPORT_SUMMARY_TXT));
  }

  private Iterable<? extends ImportModule> getModulesToImport() {
    if (mySelectedModules == null) {
      return myRootModules;
    }
    else {
      ImmutableSet.Builder<ImportModule> builder = ImmutableSet.builder();
      for (ImportModule module : myRootModules) {
        File dir = module.getDir();
        if (mySelectedModules.containsKey(dir)) {
          String name = mySelectedModules.get(dir);
          if (name != null) {
            module.setModuleName(name);
          }
          builder.add(module);
        }
      }
      return builder.build();
    }
  }

  @Deprecated
  public void setModulesToImport(Map<String, File> modules) {
    mySelectedModules = Maps.newHashMap();
    for (File module : modules.values()) {
      mySelectedModules.put(module, null);
    }
  }

  /**
   * Like {@link #exportProject(java.io.File, boolean)}, but writes into an existing
   * project instead of creating a new one.
   * <p>
   * <b>NOTE</b>: When performing an import into an existing project, note that
   * you should call {@link #setImportIntoExisting(boolean)} before the call to
   * read in projects ({@link #importProjects(java.util.List)}. Note also that
   * you should call {@link #setPerModuleRepositories(boolean)} with a suitable
   * value based on whether the existing project defines shared repositories.
   * This is similar to how we pass the "perModuleRepositories" variable to
   * our Freemarker templates (such as
   * templates/gradle-projects/NewAndroidModule/root/build.gradle.ftl ) so it
   * can decide whether to include this info in the new module. In Studio we
   * set it based on whether $PROJECT/build.gradle contains "repositories" (this
   * is done in NewModuleWizard).
   * </p>
   *
   * @param projectDir     the root directory containing the project to write into
   * @param updateSettings whether the importer should attempt to update the settings.gradle
   *                       file in the project or not. Clients such as Android Studio may
   *                       wish to pass false here in order to handle this part
   * @param writeSummary   whether we should generate an import summary
   * @param destDirMap     optional map from ADT project dir to destination directory to
   *                       write each module as.
   * @return the list of imported module directories
   */
  @NonNull
  public List<File> exportIntoProject(@NonNull File projectDir,
                                      boolean updateSettings,
                                      boolean writeSummary,
                                      @Nullable Map<File, File> destDirMap) throws IOException {
    mySummary.setDestDir(projectDir);

    List<File> imported = Lists.newArrayListWithExpectedSize(myRootModules.size());
    for (ImportModule module : getModulesToImport()) {
      File moduleDir = null;
      if (destDirMap != null) {
        moduleDir = destDirMap.get(module.getDir());
      }
      if (moduleDir == null) {
        moduleDir = new File(projectDir, module.getModuleName());
        if (moduleDir.exists()) {
          module.pickUniqueName(projectDir);
          moduleDir = new File(projectDir, module.getModuleName());
          assert !moduleDir.exists();
        }
      }
      exportModule(moduleDir, module);
      imported.add(moduleDir);
    }

    if (updateSettings) {
      exportSettingsGradle(new File(projectDir, FN_SETTINGS_GRADLE), true);
    }

    if (writeSummary) {
      mySummary.write(new File(projectDir, IMPORT_SUMMARY_TXT));
    }

    return imported;
  }

  private void exportGradleWrapper(@NonNull File destDir) throws IOException {
    if (myGradleWrapperLocation != null && myGradleWrapperLocation.exists()) {
      File gradlewDest = new File(destDir, FN_GRADLE_WRAPPER_UNIX);
      copyDir(new File(myGradleWrapperLocation, FN_GRADLE_WRAPPER_UNIX), gradlewDest, null, false, null);
      boolean madeExecutable = gradlewDest.setExecutable(true);
      if (!madeExecutable) {
        reportWarning((ImportModule)null, gradlewDest, "Could not make gradle wrapper script executable");
      }
      copyDir(new File(myGradleWrapperLocation, FN_GRADLE_WRAPPER_WIN), new File(destDir, FN_GRADLE_WRAPPER_WIN), null, false, null);
      copyDir(new File(myGradleWrapperLocation, FD_GRADLE), new File(destDir, FD_GRADLE), null, false, null);
    }
  }

  // Write local.properties file
  private void exportLocalProperties(@NonNull File destDir) throws IOException {
    boolean needsNdk = needsNdk();
    if (myNdkLocation != null && needsNdk || mySdkLocation != null) {
      Properties properties = new Properties();
      if (mySdkLocation != null) {
        properties.setProperty(PROPERTY_SDK, mySdkLocation.getPath());
      }
      if (myNdkLocation != null && needsNdk) {
        properties.setProperty(PROPERTY_NDK, myNdkLocation.getPath());
      }

      File path = new File(destDir, FN_LOCAL_PROPERTIES);
      String comments = "# This file must *NOT* be checked into Version Control Systems,\n" +
                        "# as it contains information specific to your local configuration.\n" +
                        "\n" +
                        "# Location of the SDK. This is only used by Gradle.\n";
      PropertiesUtil.savePropertiesToFile(properties, path, comments);
    }
  }

  /**
   * Returns true if this project appears to need the NDK
   */
  public boolean needsNdk() {
    for (ImportModule module : myModules) {
      if (module.isNdkProject()) {
        return true;
      }
    }

    return false;
  }

  private void exportModule(File destDir, ImportModule module) throws IOException {
    mkdirs(destDir);
    createModuleBuildGradle(new File(destDir, FN_BUILD_GRADLE), module);
    module.copyInto(destDir);
  }

  @SuppressWarnings("MethodMayBeStatic")
  /** Ensure that the given directory exists, and if it can't be created, report an I/O error */
  public void mkdirs(@NonNull File destDir) throws IOException {
    if (!destDir.exists()) {
      boolean ok = destDir.mkdirs();
      if (!ok) {
        reportError(null, destDir, "Could not make directory " + destDir);
      }
    }
  }

  private void createModuleBuildGradle(@NonNull File file, ImportModule module) throws IOException {
    StringBuilder sb = new StringBuilder(500);

    if (module.isApp() || module.isAndroidLibrary()) {
      //noinspection PointlessBooleanExpression,ConstantConditions
      if (myPerModuleRepositories) {
        appendRepositories(sb, true);
      }

      if (module.isApp()) {
        sb.append("apply plugin: 'com.android.application'").append(NL);
      }
      else {
        assert module.isAndroidLibrary();
        sb.append("apply plugin: 'com.android.library'").append(NL);
      }
      sb.append(NL);
      //noinspection PointlessBooleanExpression,ConstantConditions
      if (myPerModuleRepositories) {
        sb.append("repositories {").append(NL);
        sb.append("    ").append(MAVEN_REPOSITORY).append(NL);
        sb.append("}").append(NL);
        sb.append(NL);
      }
      sb.append("android {").append(NL);
      AndroidVersion compileSdkVersion = module.getCompileSdkVersion();
      AndroidVersion minSdkVersion = module.getMinSdkVersion();
      AndroidVersion targetSdkVersion = module.getTargetSdkVersion();
      String compileSdkVersionString = compileSdkVersion.isPreview()
                                       ? '\'' + AndroidTargetHash.getPlatformHashString(compileSdkVersion) + '\''
                                       : Integer.toString(compileSdkVersion.getApiLevel());
      String addOn = module.getAddOn();
      if (addOn != null) {
        compileSdkVersionString =  '\'' + addOn + '\'';
      }
      String minSdkVersionString = minSdkVersion.isPreview()
                                   ? '\'' + module.getMinSdkVersion().getCodename() + '\''
                                   : Integer.toString(module.getMinSdkVersion().getApiLevel());
      String targetSdkVersionString = targetSdkVersion.isPreview()
                                      ? '\'' + module.getTargetSdkVersion().getCodename() + '\''
                                      : Integer.toString(module.getTargetSdkVersion().getApiLevel());
      sb.append("    compileSdkVersion ").append(compileSdkVersionString).append(NL);
      sb.append("    buildToolsVersion \"").append(getBuildToolsVersion()).append("\"").append(NL);
      sb.append(NL);
      sb.append("    defaultConfig {").append(NL);
      if (module.getPackage() != null && module.isApp()) {
        sb.append("        applicationId \"").append(module.getPackage()).append('"').append(NL);
      }
      if (minSdkVersion.getApiLevel() > 1) {
        sb.append("        minSdkVersion ").append(minSdkVersionString).append(NL);
      }
      if (targetSdkVersion.getApiLevel() > 1 && compileSdkVersion.getApiLevel() > 3) {
        sb.append("        targetSdkVersion ").append(targetSdkVersionString).append(NL);
      }

      String languageLevel = module.getLanguageLevel();
      if (!languageLevel.equals(EclipseProject.DEFAULT_LANGUAGE_LEVEL)) {
        sb.append("        compileOptions {").append(NL);
        String level = languageLevel.replace('.', '_'); // 1.6 => 1_6
        sb.append("            sourceCompatibility JavaVersion.VERSION_").append(level).append(NL);
        sb.append("            targetCompatibility JavaVersion.VERSION_").append(level).append(NL);
        sb.append("        }").append(NL);
      }

      if (module.isNdkProject() && module.getNativeModuleName() != null) {
        sb.append(NL);
        sb.append("        ndk {").append(NL);
        sb.append("            moduleName \"").append(module.getNativeModuleName()).append("\"").append(NL);
        sb.append("        }").append(NL);
      }

      if (module.getInstrumentationDir() != null) {
        sb.append(NL);
        File manifestFile = new File(module.getInstrumentationDir(), ANDROID_MANIFEST_XML);
        assert manifestFile.exists() : manifestFile;
        Document manifest = getXmlDocument(manifestFile, true);
        if (manifest != null && manifest.getDocumentElement() != null) {
          String pkg = manifest.getDocumentElement().getAttribute(ATTR_PACKAGE);
          if (pkg != null && !pkg.isEmpty()) {
            sb.append("        testApplicationId \"").append(pkg).append("\"").append(NL);
          }
          NodeList list = manifest.getElementsByTagName(NODE_INSTRUMENTATION);
          if (list.getLength() > 0) {
            Element tag = (Element)list.item(0);
            String runner = tag.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (runner != null && !runner.isEmpty()) {
              sb.append("        testInstrumentationRunner \"").append(runner).append("\"").append(NL);
            }
            Attr attr = tag.getAttributeNodeNS(ANDROID_URI, "functionalTest");
            if (attr != null) {
              sb.append("        testFunctionalTest ").append(attr.getValue()).append(NL);
            }
            attr = tag.getAttributeNodeNS(ANDROID_URI, "handleProfiling");
            if (attr != null) {
              sb.append("        testHandlingProfiling ").append(attr.getValue()).append(NL);
            }
          }
        }
      }

      sb.append("    }").append(NL);
      sb.append(NL);

      List<File> localRules = module.getLocalProguardFiles();
      List<File> sdkRules = module.getSdkProguardFiles();
      if (!localRules.isEmpty() || !sdkRules.isEmpty()) {
        // User specified ProGuard rules; replicate exactly
        sb.append("    buildTypes {").append(NL);
        sb.append("        release {").append(NL);
        sb.append("            minifyEnabled true").append(NL);
        sb.append("            proguardFiles ");
        sb.append(generateProguardFileList(localRules, sdkRules)).append(NL);
        sb.append("        }").append(NL);
        sb.append("    }").append(NL);
      }
      else {
        // User didn't specify ProGuard rules; put in defaults (but off)
        sb.append("    buildTypes {").append(NL);
        sb.append("        release {").append(NL);
        sb.append("            minifyEnabled false").append(NL);
        sb.append("            proguardFiles getDefaultProguardFile('proguard-" + "android.txt'), 'proguard-rules.txt'").append(NL);
        sb.append("        }").append(NL);
        sb.append("    }").append(NL);
      }
      sb.append("}").append(NL);
      appendDependencies(sb, module);

    }
    else if (module.isJavaLibrary()) {
      //noinspection PointlessBooleanExpression,ConstantConditions
      if (myPerModuleRepositories) {
        appendRepositories(sb, false);
      }

      sb.append("apply plugin: 'java'").append(NL);

      String languageLevel = module.getLanguageLevel();
      if (!languageLevel.equals(EclipseProject.DEFAULT_LANGUAGE_LEVEL)) {
        sb.append(NL);
        sb.append("sourceCompatibility = \"");
        sb.append(languageLevel);
        sb.append("\"").append(NL);
        sb.append("targetCompatibility = \"");
        sb.append(languageLevel);
        sb.append("\"").append(NL);
      }

      appendDependencies(sb, module);
    }
    else {
      assert false : module;
    }

    Files.write(sb.toString(), file, UTF_8);
  }

  String getBuildToolsVersion() {
    AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(mySdkLocation);
    StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(progress, false);
    if (buildTool == null) {
      buildTool = sdkHandler.getLatestBuildTool(progress, true);
    }
    if (buildTool != null) {
      return buildTool.getRevision().toString();
    }

    return CURRENT_BUILD_TOOLS_VERSION;
  }

  private void appendRepositories(@NonNull StringBuilder sb, boolean needAndroidPlugin) {
    //noinspection PointlessBooleanExpression,ConstantConditions
    if (myPerModuleRepositories) {
      //noinspection SpellCheckingInspection
      sb.append("buildscript {").append(NL);
      sb.append("    repositories {").append(NL);
      sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
      sb.append("    }").append(NL);
      if (needAndroidPlugin) {
        sb.append("    dependencies {").append(NL);
        sb.append("        classpath '" + ANDROID_GRADLE_PLUGIN + "'").append(NL);
        sb.append("    }").append(NL);
      }
      sb.append("}").append(NL);
    }
  }

  private void createProjectBuildGradle(@NonNull File file) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("// Top-level build file where you can add configuration options common to all sub-projects/modules.");

    //noinspection PointlessBooleanExpression,ConstantConditions
    if (!myPerModuleRepositories) {
      sb.append(NL);
      //noinspection SpellCheckingInspection
      sb.append("buildscript {").append(NL);
      sb.append("    repositories {").append(NL);
      sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
      sb.append("    }").append(NL);
      sb.append("    dependencies {").append(NL);
      sb.append("        classpath '" + ANDROID_GRADLE_PLUGIN + "'").append(NL);
      sb.append("    }").append(NL);
      sb.append("}").append(NL);
      sb.append(NL);
      //noinspection SpellCheckingInspection
      sb.append("allprojects {").append(NL);
      sb.append("    repositories {").append(NL);
      sb.append("        ").append(MAVEN_REPOSITORY).append(NL);
      sb.append("    }").append(NL);
      sb.append("}");
    }
    sb.append(NL);
    Files.write(sb.toString(), file, UTF_8);
  }

  private void exportSettingsGradle(@NonNull File file, boolean append) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (append) {
      if (!file.exists()) {
        append = false;
      }
      else {
        // Ensure that the new include statements are separate code statements, not
        // for example inserted at the end of a // line comment
        String existing = Files.toString(file, UTF_8);
        if (!existing.endsWith(NL)) {
          sb.append(NL);
        }
      }
    }

    for (ImportModule module : getModulesToImport()) {
      sb.append("include '");
      sb.append(module.getModuleReference());
      sb.append("'");
      sb.append(NL);
    }

    String code = sb.toString();
    if (append) {
      Files.append(code, file, UTF_8);
    }
    else {
      Files.write(code, file, UTF_8);
    }
  }

  private void createDestDir(@NonNull File destDir, boolean allowNonEmpty) throws IOException {
    if (destDir.exists()) {
      if (!allowNonEmpty) {
        File[] files = destDir.listFiles();
        if (files != null && files.length > 0) {
          throw new IOException("Destination directory " + destDir + " should be empty");
        }
      }
    }
    else {
      mkdirs(destDir);
    }
  }

  @NonNull
  public List<String> getWarnings() {
    return myWarnings;
  }

  @NonNull
  public List<String> getErrors() {
    return myErrors;
  }

  /**
   * Returns module names to module source locations mappings.
   */
  @NonNull
  public Map<String, File> getDetectedModuleLocations() {
    TreeMap<String, File> modules = new TreeMap<String, File>();
    for (ImportModule module : myModules) {
      modules.put(module.getModuleName(), module.getCanonicalModuleDir());
    }
    return modules;
  }

  public void setImportModuleNames(Map<File, String> moduleLocationToName) {
    mySelectedModules = ImmutableMap.copyOf(moduleLocationToName);
  }

  @NotNull
  public Set<String> getProjectDependencies(String projectName) {
    ImportModule module = null;
    for (ImportModule m : myModules) {
      if (Objects.equal(m.getModuleName(), projectName)) {
        module = m;
        break;
      }
    }
    if (module == null) {
      return ImmutableSet.of();
    }
    else {
      ImmutableSet.Builder<String> deps = ImmutableSet.builder();
      for (ImportModule importModule : module.getAllDependencies()) {
        deps.add(importModule.getModuleName());
      }
      return deps.build();
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void reportError(@Nullable EclipseProject project, @Nullable File file, @NonNull String message) {
    reportError(project, file, message, true);
  }

  public void reportError(@Nullable EclipseProject project, @Nullable File file, @NonNull String message, boolean abort) {
    String text = formatMessage(project != null ? project.getName() : null, file, message);
    myErrors.add(text);
    if (abort) {
      throw new ImportException(text);
    }
  }

  public void reportWarning(@Nullable ImportModule module, @Nullable File file, @NonNull String message) {
    String moduleName = module != null ? module.getOriginalName() : null;
    myWarnings.add(formatMessage(moduleName, file, message));
  }

  public void reportWarning(@Nullable EclipseProject project, @Nullable File file, @NonNull String message) {
    String moduleName = project != null ? project.getName() : null;
    myWarnings.add(formatMessage(moduleName, file, message));
  }

  @Nullable
  File resolvePathVariable(@Nullable EclipseProject fromProject, @NonNull String name, boolean record) throws IOException {
    File file = myPathMap.get(name);
    if (file != null) {
      return file;
    }

    if (fromProject != null && myWorkspaceLocation == null) {
      guessWorkspace(fromProject.getDir());
    }

    String value = null;
    Properties properties = getJdtSettingsProperties(false);
    if (properties != null) {
      value = properties.getProperty("org.eclipse.jdt.core.classpathVariable." + name);
    }
    if (value == null) {
      properties = getPathSettingsProperties(false);
      if (properties != null) {
        value = properties.getProperty("pathvariable." + name);
      }
    }

    if (value == null) {
      if (record) {
        myPathMap.put(name, null);
      }
      return null;
    }

    file = new File(value.replace('/', separatorChar));

    return file;
  }

  @Nullable
  private Properties getJdtSettingsProperties(boolean mustExist) throws IOException {
    File settings = getJdtSettingsFile();
    if (!settings.exists()) {
      if (mustExist) {
        reportError(null, settings, "Settings file does not exist");
      }
      return null;
    }

    return PropertiesUtil.getProperties(settings);
  }

  private File getRuntimeSettingsDir() {
    return new File(getWorkspaceLocation(), ".metadata" + separator +
                                            ".plugins" + separator +
                                            "org.eclipse.core.runtime" + separator +
                                            ".settings");
  }

  private File getJdtSettingsFile() {
    return new File(getRuntimeSettingsDir(), "org.eclipse.jdt.core.prefs");
  }

  private File getPathSettingsFile() {
    return new File(getRuntimeSettingsDir(), "org.eclipse.core.resources.prefs");
  }

  private File getEncodingSettingsFile() {
    return getPathSettingsFile();
  }

  private File getNdkSettingsFile() {
    return new File(getRuntimeSettingsDir(), "com.android.ide.eclipse.ndk.prefs");
  }

  private File getAdtSettingsFile() {
    return new File(getRuntimeSettingsDir(), "com.android.ide.eclipse.adt.prefs");
  }

  @Nullable
  private Properties getPathSettingsProperties(boolean mustExist) throws IOException {
    File settings = getPathSettingsFile();
    if (!settings.exists()) {
      if (mustExist) {
        reportError(null, settings, "Settings file does not exist");
      }
      return null;
    }

    return PropertiesUtil.getProperties(settings);
  }

  private File getWorkspaceLocation() {
    return myWorkspaceLocation;
  }

  @Nullable
  Document getXmlDocument(File file, boolean namespaceAware) throws IOException {
    String xml = Files.toString(file, UTF_8);
    try {
      return XmlUtils.parseDocument(xml, namespaceAware);
    }
    catch (Exception e) {
      reportError(null, file, "Invalid XML file: " + file.getPath() + ":\n" + e.getMessage());
      return null;
    }
  }

  Map<File, EclipseProject> getProjectMap() {
    return myProjectMap;
  }

  public ImportSummary getSummary() {
    return mySummary;
  }

  void registerProject(@NonNull EclipseProject project) {
    // Register not just this directory but the canonical versions too, since library
    // references in project.properties can be relative and can be made canonical;
    // we want to make sure that a project known by any of these versions of the paths
    // are treated as the same
    myProjectMap.put(project.getDir(), project);
    myProjectMap.put(project.getDir().getAbsoluteFile(), project);
    myProjectMap.put(project.getCanonicalDir(), project);
  }

  int getModuleCount() {
    int moduleCount = 0;
    for (ImportModule module : myModules) {
      if (!module.isReplacedWithDependency()) {
        moduleCount++;
      }
    }
    return moduleCount;
  }

  /**
   * Returns a path map for workspace paths
   */
  public Map<String, File> getPathMap() {
    return myPathMap;
  }

  /**
   * Handles copying the given source into the given destination, whether the source
   * is a file or directory. An optional handler can be used to perform special handling,
   * such as skipping files or changing the destination.
   *
   * @param source         the source file/directory to copy
   * @param dest           the destination for that file
   * @param handler        an optional copy handler
   * @param updateEncoding if false, do not try to rewrite encodings to UTF-8
   * @param sourceModule   if non null, a corresponding module this source file belongs to
   *                       (used to look up the default encoding if applicable)
   */
  public void copyDir(@NonNull File source,
                      @NonNull File dest,
                      @Nullable CopyHandler handler,
                      boolean updateEncoding,
                      @Nullable ImportModule sourceModule) throws IOException {
    if (handler != null && handler.handle(source, dest, updateEncoding, sourceModule)) {
      return;
    }
    if (source.isDirectory()) {
      if (isIgnoredFile(source)) {
        // Skip version control files when generating the migrated project;
        // it will only have fragments of the project, and in some cases moved
        // around, so don't pick up partial VCS state
        return;
      }

      mkdirs(dest);
      File[] files = source.listFiles();
      if (files != null) {
        for (File child : files) {
          copyDir(child, new File(dest, child.getName()), handler, updateEncoding, sourceModule);
        }
      }

      // Delete empty directories. This happens for example when a whole source subdirectory
      // turns out to only contain special files that are moved elsewhere (such as .aidl or resource files)
      File[] copied = dest.listFiles();
      if (copied != null && copied.length == 0) {
        //noinspection ResultOfMethodCallIgnored
        dest.delete();
      }
    }
    else if (updateEncoding && isTextFile(source)
             // Property files have their own special encoding; don't touch these
             && !source.getPath().endsWith(DOT_PROPERTIES)) {
      copyTextFile(sourceModule, source, dest);
    }
    else {
      Files.copy(source, dest);
    }
  }

  public void copyTextFile(@Nullable ImportModule module, @NonNull File source, @NonNull File dest) throws IOException {
    assert isTextFile(source) : source;

    Charset encoding = null;

    // A specific encoding configured for a given file always wins:
    if (module != null) {
      encoding = module.getFileEncoding(source);
      if (encoding != null) {
        copyTextFileWithEncoding(source, dest, encoding);
        return;
      }

      encoding = module.getProjectEncoding(source);
    }

    if (encoding == null) {
      encoding = getEncodingFromWorkspaceSetting();
    }

    // For XML files we can sometimes read the encoding right out of the XML prologue
    if (SdkUtils.endsWithIgnoreCase(source.getPath(), DOT_XML)) {
      String defaultCharset = encoding != null ? encoding.name() : SdkConstants.UTF_8;
      String xml = PositionXmlParser.getXmlString(Files.toByteArray(source), defaultCharset);
      // Replace prologue if it specifies the encoding
      if (xml.startsWith("<?xml")) {
        int prologueEnd = xml.indexOf("?>");
        if (prologueEnd != -1) {
          xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + xml.substring(prologueEnd + 2);
        }
      }

      Files.write(xml, dest, Charsets.UTF_8);
    }
    else if (encoding != null) {
      copyTextFileWithEncoding(source, dest, encoding);
    }
    else {
      Files.copy(source, dest);
    }
  }

  void markJarHandled(@NonNull File file) {
    myHandledJars.add(file.getName());
  }

  boolean isJarHandled(@NonNull File file) {
    return myHandledJars.contains(file.getName());
  }

  private boolean haveLocalRepository(@NonNull SdkMavenRepository repository) {
    if (repository.isInstalled(AndroidSdkHandler.getInstance(mySdkLocation))) {
      return true;
    }

    return repository.isInstalled(mySdkLocation, FileOpUtils.create());
  }

  public boolean needSupportRepository() {
    return haveArtifact("com.android.support");
  }

  public boolean needGoogleRepository() {
    return haveArtifact("com.google.android.gms");
  }

  private boolean haveArtifact(String groupId) {
    for (ImportModule module : getModulesToImport()) {
      for (GradleCoordinate dependency : module.getDependencies()) {
        if (groupId.equals(dependency.getGroupId())) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isMissingSupportRepository() {
    return !haveLocalRepository(SdkMavenRepository.ANDROID);
  }

  public boolean isMissingGoogleRepository() {
    return !haveLocalRepository(SdkMavenRepository.GOOGLE);
  }

  /**
   * Interface used by the {@link #copyDir} handler
   */
  public interface CopyHandler {
    /**
     * Optionally handle the given file; returns true if the file has been
     * handled
     *
     * @param source         the source file/directory to copy
     * @param dest           the destination for that file
     * @param updateEncoding if false, do not try to rewrite encodings to UTF-8
     * @param sourceModule   if non null, a corresponding module this source file belongs to
     */
    boolean handle(@NonNull File source, @NonNull File dest, boolean updateEncoding, @Nullable ImportModule sourceModule)
      throws IOException;
  }

  private static class ImportException extends RuntimeException {
    private String mMessage;

    private ImportException(@NonNull String message) {
      mMessage = message;
    }

    @Override
    public String getMessage() {
      return mMessage;
    }

    @Override
    public String toString() {
      return getMessage();
    }
  }
}
