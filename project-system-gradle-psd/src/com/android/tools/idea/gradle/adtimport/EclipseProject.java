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

package com.android.tools.idea.gradle.adtimport;

import static com.android.SdkConstants.ANDROID_LIBRARY;
import static com.android.SdkConstants.ANDROID_LIBRARY_REFERENCE_FORMAT;
import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.CURRENT_PLATFORM;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FD_SOURCES;
import static com.android.SdkConstants.FN_PROJECT_PROPERTIES;
import static com.android.SdkConstants.GEN_FOLDER;
import static com.android.SdkConstants.LIBS_FOLDER;
import static com.android.SdkConstants.PLATFORM_WINDOWS;
import static com.android.SdkConstants.PROGUARD_CONFIG;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.sdklib.internal.project.ProjectProperties.PROPERTY_SDK;
import static com.android.tools.idea.gradle.util.ImportUtil.APPCOMPAT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.GRIDLAYOUT_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.MEDIA_ROUTER_ARTIFACT;
import static com.android.tools.idea.gradle.util.ImportUtil.SUPPORT_ARTIFACT;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.android.xml.AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION;
import static com.android.xml.AndroidManifest.ATTRIBUTE_TARGET_PACKAGE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_TARGET_SDK_VERSION;
import static com.android.xml.AndroidManifest.NODE_INSTRUMENTATION;
import static com.android.xml.AndroidManifest.NODE_USES_SDK;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.util.PropertiesFiles;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Provides information about an Eclipse project */
class EclipseProject implements Comparable<EclipseProject> {
  static final String DEFAULT_LANGUAGE_LEVEL = "1.6";
  private static final String HOME_PROPERTY = "user.home";                    //$NON-NLS-1$
  private static final String HOME_PROPERTY_REF = "${" + HOME_PROPERTY + '}'; //$NON-NLS-1$
  private static final String SDK_PROPERTY_REF = "${" + PROPERTY_SDK + '}';   //$NON-NLS-1$
  private final GradleImport myImporter;
  private final File myDir;
  private final File myCanonicalDir;
  private boolean myLibrary;
  private boolean myAndroidProject;
  private boolean myNdkProject;
  private AndroidVersion myMinSdkVersion;
  private AndroidVersion myTargetSdkVersion;
  private Document myProjectDoc;
  private Document myManifestDoc;
  private Properties myProjectProperties;
  private AndroidVersion myVersion;
  private String myAddOn;
  private String myName;
  private String myLanguageLevel;
  private List<EclipseProject> myDirectLibraries;
  private List<File> mySourcePaths;
  private List<File> myJarPaths;
  private List<File> myInstrumentationJarPaths;
  private List<File> myNativeLibs;
  private List<String> myInferredLibraries;
  private File myNativeSources;
  private String myNativeModuleName;
  private File myOutputDir;
  private String myPackage;
  private List<File> myLocalProguardFiles;
  private List<File> mySdkProguardFiles;
  private List<EclipseProject> myAllLibraries;
  private EclipseImportModule myModule;
  private Map<String, String> myProjectVariableMap;
  private Map<String, String> myLinkedResourceMap;
  private File myInstrumentationDir;
  private Map<File, Charset> myFileCharsets;
  private Charset myDefaultCharset;

  private EclipseProject(@NonNull GradleImport importer, @NonNull File dir) throws IOException {
    myImporter = importer;
    myDir = dir;
    myCanonicalDir = dir.getCanonicalFile();

    // Ensure that  the library references (which are canonicalized) find this project
    // if included from multiple locations
    myImporter.registerProject(this);

    initProjectName();
    initAndroidProject();
    initLanguageLevel();

    if (isAndroidProject()) {
      Properties properties = getProjectProperties();
      if (properties != null) {
        initProguard(properties);
        initVersion(properties);
        initLibraries(properties);
        initLibrary(properties);
      }
      initPackage();
      initMinSdkVersion();
      initInstrumentation();
    }
    else {
      myDirectLibraries = new ArrayList<EclipseProject>(4);
    }

    initClassPathEntries();
    initJni();
    initEncoding();
  }

  @NonNull
  public static EclipseProject getProject(@NonNull GradleImport importer, @NonNull File dir) throws IOException {
    Map<File, EclipseProject> mProjectMap = importer.getProjectMap();
    EclipseProject project = mProjectMap.get(dir);

    if (project == null) {
      project = createProject(importer, dir);
      // The project should register itself in the map; we don't have to do that here.
      // (The code used to do that here, but it turns out project creation can recursively
      // visit library references as part of initialization, so have the projects register
      // themselves prior to initialization instead)
      assert mProjectMap.get(dir) != null;
    }

    return project;
  }

  @NonNull
  private static EclipseProject createProject(@NonNull GradleImport importer, @NonNull File dir) throws IOException {
    // Read the .classpath, .project, project.properties and local.properties files (if there)
    return new EclipseProject(importer, dir);
  }

  @Nullable
  private static AndroidVersion getApiVersion(Element usesSdk, String attribute, @Nullable AndroidVersion defaultApiLevel) {
    String valueString = null;
    if (usesSdk.hasAttributeNS(ANDROID_URI, attribute)) {
      valueString = usesSdk.getAttributeNS(ANDROID_URI, attribute);
    }

    if (valueString != null) {
      AndroidVersion version = SdkVersionInfo.getVersion(valueString, null);
      if (version != null) {
        return version;
      }
    }

    return defaultApiLevel;
  }

  @Nullable
  private static String getInstrumentationTarget(@NonNull GradleImport importer, @NonNull File manifest) throws IOException {
    Document doc = importer.getXmlDocument(manifest, true);
    if (doc != null) {
      NodeList list = doc.getElementsByTagName(NODE_INSTRUMENTATION);
      for (int i = 0; i < list.getLength(); i++) {
        Element tag = (Element)list.item(i);
        String target = tag.getAttributeNS(ANDROID_URI, ATTRIBUTE_TARGET_PACKAGE);
        if (target != null && !target.isEmpty()) {
          return target;
        }
      }
    }

    return null;
  }

  @Nullable
  private static String getStringValue(@NonNull Element element) {
    NodeList children = element.getChildNodes();
    for (int j = 0; j < children.getLength(); j++) {
      Node child = children.item(j);
      if (child.getNodeType() == Node.TEXT_NODE) {
        return child.getNodeValue().trim();
      }

    }

    return null;
  }

  /**
   * Creates a list of modules from the given set of projects. The returned list
   * is in dependency order.
   */
  public static List<? extends ImportModule> performImport(@NonNull GradleImport importer, @NonNull Collection<EclipseProject> projects) {
    List<EclipseImportModule> modules = new ArrayList<>();
    List<EclipseImportModule> replacedByDependencies = new ArrayList<>();

    for (EclipseProject project : projects) {
      EclipseImportModule module = new EclipseImportModule(importer, project);
      module.initialize();
      if (module.isReplacedWithDependency()) {
        replacedByDependencies.add(module);
      }
      else {
        modules.add(module);
      }
    }

    // Some libraries may be replaced by just a dependency (for example,
    // instead of copying in a whole copy of ActionBarSherlock, just
    // replace by the corresponding dependency.
    for (EclipseImportModule replaced : replacedByDependencies) {
      assert replaced.getReplaceWithDependencies() != null;
      EclipseProject project = replaced.getProject();
      for (EclipseImportModule module : modules) {
        if (module.getProject().getAllLibraries().contains(project)) {
          module.addDependencies(replaced.getReplaceWithDependencies());
        }
      }
    }

    // Strip out .jar files from the libs/ folder if already implied by
    // library dependencies
    for (EclipseImportModule module : modules) {
      module.removeJarDependencies();
    }

    // Sort by dependency order
    Collections.sort(modules);

    return modules;
  }

  private void initVersion(@NonNull Properties properties) {
    String target = properties.getProperty("target"); //$NON-NLS-1$
    if (target != null) {
      myVersion = AndroidTargetHash.getPlatformVersion(target);

      // getPlatformVersion does not handle API numbers correctly
      if (myVersion != null && myVersion.isPreview()) {
        // Update codename
        AndroidVersion version = SdkVersionInfo.getVersion(myVersion.getCodename(), null);
        if (version != null) {
          myVersion = version;
        }
      } else {
        int index = target.lastIndexOf(':');
        if (index != -1) {
          myVersion = SdkVersionInfo.getVersion(target.substring(index + 1), null);
          myAddOn = target;
        }
      }
    }
  }

  private void initLibraries(@NonNull Properties properties) throws IOException {
    myDirectLibraries = new ArrayList<EclipseProject>(4);

    for (int i = 0; i < 1000; i++) {
      String key = String.format(Locale.US, ANDROID_LIBRARY_REFERENCE_FORMAT, i);
      String library = properties.getProperty(key);
      if (library == null || library.isEmpty()) {
        // No holes in the numbering sequence is allowed
        if (i == 0) {
          // Except for i=0; library projects are supposed to start with 1, and
          // all the ADT, sdklib and ant code which reads and writes these start with
          // 1, but I've encountered several projects in the wild that start with 0;
          // presumably from manual edits or because some older version of the tools
          // did this.
          // Instead of bailing here, try 1 too.
          continue;
        }
        else {
          // After 1, we don't allow any gaps in the sequence
          break;
        }
      }

      // Handle importing Windows-relative paths in project.properties on non-Windows,
      // and vice versa
      if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
        library = library.replace('/', '\\');
      }
      else {
        library = library.replace('\\', '/');
      }

      File path = new File(library);
      File joined = path.isAbsolute() ? path : new File(myDir, library);
      File libraryDir = joined.getCanonicalFile();
      if (!libraryDir.exists()) {
        if (myImporter.isReplaceLibs()) {
          // Look for some common libraries that we can probably just guess as a dependency replacement
          String libraryDirName = libraryDir.getName().replace('_', '-');
          if (libraryDirName.indexOf('-') == -1) {
            File parent = libraryDir.getParentFile();
            if (parent != null) {
              String parentName = parent.getName();
              if (parentName.equals("v7")) {
                // Recognize paths pointing into an SDK extras folder such as
                // ../../../android-sdk-mac_86/extras/android/compatibility/v7/appcompat
                // and turn them into appcompat-v7 such that they match the artifact check below
                libraryDirName = libraryDirName + '-' + parentName;
              }
            }
          }
          if (APPCOMPAT_ARTIFACT.equals(libraryDirName)
               || SUPPORT_ARTIFACT.equals(libraryDirName)
               || GRIDLAYOUT_ARTIFACT.equals(libraryDirName)
               || MEDIA_ROUTER_ARTIFACT.equals(libraryDirName)) {
            if (myInferredLibraries == null) {
              myInferredLibraries = new ArrayList<>();
            }
            myInferredLibraries.add(libraryDirName);
            continue;
          }
        }

        String message = "Library reference " + library + " could not be found";
        if (!path.isAbsolute()) {
          message += "\nPath is " + joined + " which resolves to " + libraryDir.getPath();
        }
        myImporter.reportError(this, getProjectPropertiesFile(), message);
      }

      EclipseProject libraryPrj = getProject(myImporter, libraryDir);
      myDirectLibraries.add(libraryPrj);
    }
  }

  private void initLibrary(@NonNull Properties properties) throws IOException {
    // This initialization must run after we've initialized the set of library
    // projects so we know whether or not we're including/merging manifests
    assert myDirectLibraries != null;
    String value = properties.getProperty(ANDROID_LIBRARY);
    myLibrary = VALUE_TRUE.equals(value);

    if (!myLibrary) {
      boolean mergeManifests = VALUE_TRUE.equals(properties.getProperty("manifestmerger.enabled")); //$NON-NLS-1$
      if (!mergeManifests) {
        // See if we (transitively) depend on libraries, and if any of them are
        // android library projects with non-empty manifests
        for (EclipseProject library : getAllLibraries()) {
          if (library.isAndroidProject() && library.isLibrary() &&
              library.getManifestFile().exists() &&
              library.getManifestDoc().getDocumentElement() != null &&
              XmlUtils.hasElementChildren(library.getManifestDoc().
                getDocumentElement())) {
            myImporter.getSummary().reportManifestsMayDiffer();
            break;
          }
        }
      }
    }
  }

  private void initPackage() throws IOException {
    myPackage = getManifestDoc().getDocumentElement().getAttribute(ATTR_PACKAGE);
  }

  private void initMinSdkVersion() throws IOException {
    NodeList usesSdks = getManifestDoc().getDocumentElement().getElementsByTagName(NODE_USES_SDK);
    if (usesSdks.getLength() > 0) {
      Element usesSdk = (Element)usesSdks.item(0);
      myMinSdkVersion = getApiVersion(usesSdk, ATTRIBUTE_MIN_SDK_VERSION, AndroidVersion.DEFAULT);
      myTargetSdkVersion = getApiVersion(usesSdk, ATTRIBUTE_TARGET_SDK_VERSION, myMinSdkVersion);
    }
    else {
      myMinSdkVersion = null;
      myTargetSdkVersion = null;
    }
  }

  private void initProjectName() throws IOException {
    Document document = getProjectDocument();
    if (document == null) {
      return;
    }
    NodeList names = document.getElementsByTagName("name");

    for (int i = 0; i < names.getLength(); i++) {
      Node element = names.item(i);
      myName = getStringValue((Element)element);
      //noinspection VariableNotUsedInsideIf
      if (myName != null) {
        break;
      }
    }

    if (myName == null) {
      myName = myDir.getName();
    }
  }

  private void initJni() throws IOException {
    File jniDir = new File(myDir, "jni");
    if (!jniDir.exists()) {
      return;
    }

    //noinspection SpellCheckingInspection
    if (myNdkProject) {
      myNativeSources = jniDir;

      File makefile = new File(jniDir, "Android.mk");
      if (makefile.exists()) {
        Pattern pattern = Pattern.compile("\\s*LOCAL_MODULE\\s*:=\\s*(\\S+)\\s*");
        for (String line : Files.readLines(makefile, Charsets.UTF_8)) {
          Matcher matcher = pattern.matcher(line);
          if (matcher.matches()) {
            myNativeModuleName = matcher.group(1);

            if (myNativeLibs != null) {
              // Remove libs from the libs/<abi> folder if they are just
              // outputs from these sources
              String libName = "lib" + myNativeModuleName + ".so";
              ListIterator<File> iterator = myNativeLibs.listIterator();
              while (iterator.hasNext()) {
                File lib = iterator.next();
                if (libName.equals(lib.getName())) {
                  iterator.remove();
                }
              }
              if (myNativeLibs.isEmpty()) {
                myNativeLibs = null;
              }
            }
            break;
          }
        }
      }
    }
  }

  private void initEncoding() throws IOException {
    File propertyFile = new File(myDir, ".settings" + separator +
                                       "org.eclipse.core.resources.prefs");
    if (propertyFile.exists()) {
      Properties properties = PropertiesFiles.getProperties(propertyFile);
      if (properties != null) {
        Enumeration<?> enumeration = properties.propertyNames();
        String prefix = "encoding/";
        while (enumeration.hasMoreElements()) {
          Object next = enumeration.nextElement();
          if (next instanceof String) {
            String key = (String)next;
            if (key.startsWith(prefix)) {
              String path = key.substring(prefix.length());
              String encoding = properties.getProperty(key);
              if (encoding != null && !encoding.isEmpty()) {
                try {
                  Charset charset = Charset.forName(encoding);
                  if ("<project>".equals(path)) {
                    myDefaultCharset = charset;
                  }
                  else {
                    if (myFileCharsets == null) {
                      myFileCharsets = Maps.newHashMap();
                    }
                    File file = resolveVariableExpression(path);
                    if (file != null) {
                      myFileCharsets.put(file, charset);
                    }
                    else {
                      myFileCharsets.put(new File(path), charset);
                    }
                  }
                }
                catch (UnsupportedCharsetException uce) {
                  myImporter.reportWarning(this, propertyFile, "Unknown charset " + encoding);
                }
              }
            }
          }
        }
      }
    }
  }

  @Nullable
  Charset getProjectEncoding() {
    return myDefaultCharset;
  }

  @Nullable
  Charset getFileEncoding(@NonNull File file) {
    return myFileCharsets != null ? myFileCharsets.get(file) : null;
  }

  private void initInstrumentation() throws IOException {
    // Find unit test projects pointing to this Gradle project. Where do we look?
    // For now, in direct sub directories of the project, as well as sibling directories

    File projectDir = findInstrumentationTests(myDir);
    if (projectDir == null && myDir.getParentFile() != null) {
      projectDir = findInstrumentationTests(myDir.getParentFile());
    }

    if (projectDir != null && !projectDir.equals(myDir)) {
      myInstrumentationDir = projectDir;

      File libs = new File(myInstrumentationDir, LIBS_FOLDER);
      if (libs.exists()) {
        File[] files = libs.listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.isFile() && endsWithIgnoreCase(file.getPath(), DOT_JAR)) {
              if (myInstrumentationJarPaths == null) {
                myInstrumentationJarPaths = new ArrayList<>();
              }
              myInstrumentationJarPaths.add(file);
            }
          }
        }
      }
    }
  }

  @Nullable
  private File findInstrumentationTests(File parent) {
    File[] files = parent.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          File manifest = new File(file, ANDROID_MANIFEST_XML);
          if (manifest.exists()) {
            try {
              String target = getInstrumentationTarget(myImporter, manifest);
              if (target != null && target.equals(myPackage)) {
                return file;
              }
            }
            catch (IOException e) {
              // Ignore this manifest
            }
          }
        }
      }
    }
    return null;
  }

  private void initClassPathEntries() throws IOException {
    assert mySourcePaths == null && myJarPaths == null;
    mySourcePaths = new ArrayList<>();
    myJarPaths = new ArrayList<>();

    Document document = null;
    File classPathFile = getClassPathFile();
    if (!classPathFile.exists()) {
      File src = new File(myDir, FD_SOURCES);
      if (src.exists()) {
        mySourcePaths.add(src);
      }
    }
    else {
      document = myImporter.getXmlDocument(classPathFile, false);
    }

    if (document != null) {
      NodeList entries = document.getElementsByTagName("classpathentry");
      for (int i = 0; i < entries.getLength(); i++) {
        Node entry = entries.item(i);
        assert entry.getNodeType() == Node.ELEMENT_NODE;
        Element element = (Element)entry;
        String kind = element.getAttribute("kind");
        String path = element.getAttribute("path");
        if (kind.equals("var")) {
          File resolved = resolveVariableExpression(path);
          if (resolved != null) {
            mySourcePaths.add(resolved);
          }
          else {
            myImporter.reportWarning(this, getClassPathFile(), "Could not resolve path variable " + path);
          }
        }
        else if (kind.equals("src") && !path.isEmpty()) {
          if (!path.equals(GEN_FOLDER)) { // ignore special generated source folder
            File resolved = resolveVariableExpression(path);
            if (resolved != null) {
              if (path.startsWith("/") && GradleImport.isEclipseProjectDir(resolved)) {
                // It's pointing to another project. Just add a dependency.
                EclipseProject lib = getProject(myImporter, resolved);
                if (!myDirectLibraries.contains(lib)) {
                  myDirectLibraries.add(lib);
                  myAllLibraries = null; // force refresh if already consulted
                }
              }
              else {
                // It's some other source directory: just include as a source path
                mySourcePaths.add(resolved);
              }
            }
            else {
              myImporter.reportWarning(this, getClassPathFile(), "Could not resolve source path " +
                                                                path +
                                                                " in project " +
                                                                getName() +
                                                                ": ignored. The project may not " +
                                                                "compile if the given source path provided " +
                                                                "source code.");
            }
          }
        }
        else if (kind.equals("lib") && !path.isEmpty()) {
          // Java library dependency. In Android projects we don't need these since
          // we pick up the information from the project.properties file for library
          // dependencies and the libs/ folder for jar files.
          if (!isAndroidProject()) {
            File resolved = resolveVariableExpression(path);
            if (resolved != null) {
              myJarPaths.add(resolved);
            }
            else {
              myImporter.reportWarning(this, getClassPathFile(),
                                      "Absolute path in the path entry: If outside project, may not " + "work correctly: " + path);
            }
          }
        }
        else if (kind.equals("output") && !path.isEmpty()) {
          String relative = path.replace('/', separatorChar);
          File file = new File(relative);
          if (!file.isAbsolute()) {
            myOutputDir = file;
          }
        }
        // else: ignore kind="con"
      }
    }

    // Automatically add in libraries in libs
    File[] libs = new File(myDir, LIBS_FOLDER).listFiles();
    if (libs != null) {
      for (File lib : libs) {
        if (!lib.isFile()) {
          // ABI folder?
          File[] libraries = lib.listFiles();
          if (libraries != null) {
            for (File library : libraries) {
              String name = library.getName();
              if (library.isFile() && name.startsWith("lib") && name.contains(".so")) { // or .endsWith? Allow libfoo.so.1 ?
                if (myNativeLibs == null) {
                  myNativeLibs = new ArrayList<>();
                }
                File relative = new File(LIBS_FOLDER, lib.getName() + separator + library.getName());
                myNativeLibs.add(relative);
              }
            }
          }
          continue;
        }
        //noinspection ConstantConditions
        assert lib.isFile();
        if (!endsWithIgnoreCase(lib.getPath(), DOT_JAR)) {
          continue;
        }
        File relative = new File(LIBS_FOLDER, lib.getName());
        if (!(myJarPaths.contains(relative) || myJarPaths.contains(lib))) {
          // Skip jars that are the result of a library project dependency
          boolean isLibraryJar = false;
          for (EclipseProject project : getAllLibraries()) {
            if (!project.isAndroidProject()) {
              continue;
            }
            String pkg = project.getPackage();
            if (pkg != null) {
              String jarName = pkg.replace('.', '-') + DOT_JAR;
              if (jarName.equals(lib.getName())) {
                isLibraryJar = true;
                break;
              }
            }
          }
          if (!isLibraryJar) {
            myJarPaths.add(relative);
          }
        }
      }
      Collections.sort(myJarPaths);
      if (myNativeLibs != null) {
        Collections.sort(myNativeLibs);
      }
    }
  }

  private Map<String, String> getProjectVariableMap() {
    if (myProjectVariableMap == null) {
      myProjectVariableMap = Maps.newHashMap();

      Document document;
      try {
        document = getProjectDocument();
        if (document == null) {
          return myProjectVariableMap;
        }
      }
      catch (IOException e) {
        return myProjectVariableMap;
      }
      NodeList variables = document.getElementsByTagName("variable");
      for (int i = 0, n = variables.getLength(); i < n; i++) {
        Element variable = (Element)variables.item(i);
        NodeList names = variable.getElementsByTagName("name");
        NodeList values = variable.getElementsByTagName("value");
        if (names.getLength() == 1 && values.getLength() == 1) {
          String value = getStringValue((Element)values.item(0));
          String key = getStringValue((Element)names.item(0));
          myProjectVariableMap.put(key, value);
        }
      }
    }

    return myProjectVariableMap;
  }

  private Map<String, String> getLinkedResourceMap() {
    if (myLinkedResourceMap == null) {
      myLinkedResourceMap = Maps.newHashMap();

      Document document;
      try {
        document = getProjectDocument();
        if (document == null) {
          return myProjectVariableMap;
        }
      }
      catch (IOException e) {
        return myLinkedResourceMap;
      }
      NodeList links = document.getElementsByTagName("link");
      for (int i = 0, n = links.getLength(); i < n; i++) {
        Element variable = (Element)links.item(i);
        NodeList names = variable.getElementsByTagName("name");
        NodeList values = variable.getElementsByTagName("locationURI");
        if (names.getLength() == 1 && values.getLength() == 1) {
          String value = getStringValue((Element)values.item(0));
          String key = getStringValue((Element)names.item(0));
          myLinkedResourceMap.put(key, value);
        }
      }

    }

    return myLinkedResourceMap;
  }

  @VisibleForTesting
  @Nullable
  File resolveVariableExpression(@NonNull String path) throws IOException {
    File file = resolveVariableExpression(path, true, 0);
    if (file != null && myImporter.getPathMap().containsKey(path)) {
      myImporter.getPathMap().put(path, file);
    }
    return file;
  }

  @Nullable
  private File resolveVariableExpression(@NonNull String path, boolean record, int depth) throws IOException {
    if (depth > 50) { // probably cyclical definition of variables
      return null;
    }
    if (path.equals("PROJECT_LOC")) {
      return myDir;
    }
    else if (path.equals("PARENT_LOC")) {
      return myDir.getParentFile();
    }
    else if (path.equals("WORKSPACE_LOC")) {
      return myImporter.getEclipseWorkspace();
    }
    else if (path.startsWith("PARENT-")) {
      Pattern pattern = Pattern.compile("PARENT-(\\d+)-(.+)");
      Matcher matcher = pattern.matcher(path);
      if (matcher.matches()) {
        // Replace suffix a given number of times
        int count = Integer.parseInt(matcher.group(1));
        String target = matcher.group(2);
        int index = target.indexOf('/');
        if (index == -1) {
          index = target.indexOf('\\');
        }
        String var = index == -1 ? target : target.substring(0, index);
        File file = resolveVariableExpression(var, false, depth + 1);
        if (file != null) {
          File original = file;
          for (int i = 0; i < count; i++) {
            if (file == null) {
              break;
            }
            file = file.getParentFile();
          }
          if (file == null) {
            // Try again but with canonical files
            file = original.getCanonicalFile();
            for (int i = 0; i < count; i++) {
              if (file == null) {
                break;
              }
              file = file.getParentFile();
            }

          }
        }

        if (file != null && index != -1) {
          file = new File(file, target.substring(index + 1));
        }
        return file;
      }
    }

    // See if it's an absolute path
    String filePath = path.replace('/', separatorChar);
    File resolved = new File(filePath);
    if (resolved.exists()) {
      return resolved;
    }

    // See if it's a relative path
    resolved = new File(myDir, filePath);
    if (resolved.exists()) {
      return resolved;
    }

    // Look up in shared path map (and record path for user editing in wizard
    // if not resolvable)
    // No -- this needs to be per project?? Only if it's used in multiple projects...
    resolved = myImporter.getPathMap().get(path);
    if (resolved != null) {
      return resolved;
    }

    if (record) {
      // Record the path expression such that the user can provide a resolution
      myImporter.getPathMap().put(path, null);
    }

    // Workspace path?
    if (path.startsWith("/")) { // It's / on Windows too
      // Workspace path
      resolved = myImporter.resolveWorkspacePath(this, path, record);
      if (resolved != null) {
        return resolved;
      }

      if (path.indexOf('/', 1) == -1 && path.indexOf('\\', 1) == -1) {
        String name = path.substring(1);
        // If we can't resolve workspace paths, try looking relative
        // to the current project; dependent projects are often there
        File parent = myDir.getParentFile();
        if (parent != null) {
          File sibling = new File(parent, name);
          if (sibling.exists()) {
            return sibling;
          }
        }

        // Libraries are also often children
        File child = new File(myDir, name);
        if (child.exists()) {
          return child;
        }
      }
    }
    else if (path.startsWith("$%7B")) {
      // E.g. "<value>$%7BPARENT-2-PARENT_LOC%7D/Users</value>"
      // This corresponds to {PARENT_LOC}/../../
      int start = 4;
      int end = path.indexOf("%7D", 4);
      if (end != -1) {
        String sub = path.substring(start, end);
        File expression = resolveVariableExpression(sub, false, depth + 1);
        if (expression != null) {
          String suffix = path.substring(end + 3);
          if (suffix.isEmpty()) {
            return expression;
          }
          else {
            resolved = new File(expression, suffix.replace('/', separatorChar));
            if (resolved.exists()) {
              return resolved;
            }
          }
        }
      }
    }
    else {
      // Path variable?
      int index = path.indexOf('/');
      if (index == -1) {
        index = path.indexOf('\\');
      }
      String var;
      if (index == -1) {
        var = path;
      }
      else {
        var = path.substring(0, index);
      }

      Map<String, String> map = getLinkedResourceMap();
      String expression = map.get(var);
      if (expression == null || expression.equals(var)) {
        map = getProjectVariableMap();
        expression = map.get(var);
      }
      File file;
      if (expression != null) {
        if (expression.startsWith("file:")) {
          file = SdkUtils.urlToFile(expression);
        }
        else {
          file = resolveVariableExpression(expression, false, depth + 1);
        }
      }
      else {
        file = myImporter.resolvePathVariable(this, var, false);
      }
      if (file != null) {
        if (index == -1) {
          return file;
        }
        else {
          resolved = new File(file, path.substring(index + 1));
          if (resolved.exists()) {
            return resolved;
          }
        }
      }
    }

    return null;
  }

  private void initAndroidProject() throws IOException {
    myAndroidProject = hasNature("com.android.ide.eclipse.adt.AndroidNature");
    if (!myAndroidProject && getProjectDocument() == null) {
      myAndroidProject = GradleImport.isAdtProjectDir(myDir);
    }
    myNdkProject = myAndroidProject && (hasNature("org.eclipse.cdt.core.cnature") ||
                                      hasNature("org.eclipse.cdt.core.ccnature") ||
                                      new File(myDir, "jni" + separator + "Android.mk").exists());
  }

  private boolean hasNature(String nature) throws IOException {
    Document document = getProjectDocument();
    if (document != null) {
      NodeList natures = document.getElementsByTagName("nature");
      for (int i = 0; i < natures.getLength(); i++) {
        Node element = natures.item(i);
        String value = getStringValue((Element)element);
        if (nature.equals(value)) {
          return true;
        }
      }
    }

    return false;
  }

  private void initLanguageLevel() throws IOException {
    if (myLanguageLevel == null) {
      myLanguageLevel = DEFAULT_LANGUAGE_LEVEL; // default
      File file = new File(myDir, ".settings" + separator + "org.eclipse.jdt.core.prefs");
      if (file.exists()) {
        Properties properties = PropertiesFiles.getProperties(file);
        if (properties != null) {
          String source = properties.getProperty("org.eclipse.jdt.core.compiler.source");
          if (source != null) {
            myLanguageLevel = source;
          }
        }
      }
    }
  }

  private void initProguard(@NonNull Properties properties) {
    myLocalProguardFiles = new ArrayList<>();
    mySdkProguardFiles = new ArrayList<>();

    String proguardConfig = properties.getProperty(PROGUARD_CONFIG);
    if (proguardConfig != null && !proguardConfig.isEmpty()) {
      // Be tolerant with respect to file and path separators just like
      // Ant is. Allow "/" in the property file to mean whatever the file
      // separator character is:
      if (File.separatorChar != '/' && proguardConfig.indexOf('/') != -1) {
        proguardConfig = proguardConfig.replace('/', File.separatorChar);
      }

      Iterable<String> paths = Lint.splitPath(proguardConfig);
      for (String path : paths) {
        if (path.startsWith(SDK_PROPERTY_REF)) {
          mySdkProguardFiles.add(new File(path.substring(SDK_PROPERTY_REF.length()).replace('/', separatorChar)));
        }
        else if (path.startsWith(HOME_PROPERTY_REF)) {
          myImporter.getSummary().reportIgnoredUserHomeProGuardFile(path);
        }
        else {
          File proguardConfigFile = new File(path.replace('/', separatorChar));
          if (!proguardConfigFile.isAbsolute()) {
            proguardConfigFile = new File(myDir, proguardConfigFile.getPath());
          }
          if (proguardConfigFile.isFile()) {
            myLocalProguardFiles.add(proguardConfigFile);
          }
        }
      }
    }
  }

  @NonNull
  public File getDir() {
    return myDir;
  }

  @NonNull
  public File getCanonicalDir() {
    return myCanonicalDir;
  }

  public boolean isLibrary() {
    return myLibrary;
  }

  @NonNull
  public List<File> getLocalProguardFiles() {
    assert isAndroidProject();
    return myLocalProguardFiles;
  }

  @NonNull
  public List<File> getSdkProguardFiles() {
    assert isAndroidProject();
    return mySdkProguardFiles;
  }

  @NonNull
  public File getResourceDir() {
    assert isAndroidProject();
    return new File(myDir, FD_RES);
  }

  @NonNull
  public File getAssetsDir() {
    assert isAndroidProject();
    return new File(myDir, FD_ASSETS);
  }

  @NonNull
  private File getClassPathFile() {
    return new File(myDir, GradleImport.ECLIPSE_DOT_CLASSPATH);
  }

  @NonNull
  public Document getManifestDoc() throws IOException {
    assert isAndroidProject();
    if (myManifestDoc == null) {
      File file = getManifestFile();
      myManifestDoc = myImporter.getXmlDocument(file, true);
    }

    return myManifestDoc;
  }

  @NonNull
  File getManifestFile() {
    assert isAndroidProject();
    return new File(myDir, ANDROID_MANIFEST_XML);
  }

  @Nullable
  public Properties getProjectProperties() throws IOException {
    if (myProjectProperties == null) {
      assert isAndroidProject();
      File file = getProjectPropertiesFile();
      if (file.exists()) {
        myProjectProperties = PropertiesFiles.getProperties(file);
      }
      else {
        myProjectProperties = new Properties();
      }
    }

    return myProjectProperties;
  }

  private File getProjectPropertiesFile() {
    return new File(myDir, FN_PROJECT_PROPERTIES);
  }

  @Nullable
  private Document getProjectDocument() throws IOException {
    if (myProjectDoc == null) {
      File file = new File(myDir, GradleImport.ECLIPSE_DOT_PROJECT);
      if (file.exists()) {
        myProjectDoc = myImporter.getXmlDocument(file, false);
      }
    }

    return myProjectDoc;
  }

  public boolean isAndroidProject() {
    return myAndroidProject;
  }

  public boolean isNdkProject() {
    return myNdkProject;
  }

  @Nullable
  public File getInstrumentationDir() {
    return myInstrumentationDir;
  }

  @Nullable
  public String getPackage() {
    assert isAndroidProject();
    return myPackage;
  }

  @NonNull
  public List<File> getSourcePaths() {
    return mySourcePaths;
  }

  @NonNull
  public List<String> getInferredLibraries() {
    return myInferredLibraries == null ? Collections.<String>emptyList() : myInferredLibraries;
  }

  @NonNull
  public List<File> getJarPaths() {
    return myJarPaths;
  }

  @NonNull
  public List<File> getTestJarPaths() {
    return myInstrumentationJarPaths != null ? myInstrumentationJarPaths : Collections.<File>emptyList();
  }

  @NonNull
  public List<File> getNativeLibs() {
    return myNativeLibs != null ? myNativeLibs : Collections.<File>emptyList();
  }

  @Nullable
  public File getNativeSources() {
    return myNativeSources;
  }

  @Nullable
  public String getNativeModuleName() {
    return myNativeModuleName;
  }

  @Nullable
  public File getOutputDir() {
    return myOutputDir;
  }

  /**
   * Returns "1.6", "1.7", etc
   */
  @NonNull
  public String getLanguageLevel() {
    return myLanguageLevel;
  }

  @NonNull
  public String getName() {
    return myName != null ? myName : myDir.getName();
  }

  @NonNull
  public AndroidVersion getMinSdkVersion() {
    assert isAndroidProject();
    return myMinSdkVersion != null ? myMinSdkVersion : AndroidVersion.DEFAULT;
  }

  @NonNull
  public AndroidVersion getTargetSdkVersion() {
    assert isAndroidProject();
    return myTargetSdkVersion != null ? myTargetSdkVersion : getMinSdkVersion();
  }

  @NonNull
  public AndroidVersion getCompileSdkVersion() {
    assert isAndroidProject();
    return myVersion == null ? new AndroidVersion(GradleImport.CURRENT_COMPILE_VERSION, null) : myVersion;
  }

  @Nullable
  public String getAddOn() {
    assert isAndroidProject();
    return myAddOn;
  }

  @NonNull
  public List<EclipseProject> getDirectLibraries() {
    return myDirectLibraries;
  }

  @NonNull
  public List<EclipseProject> getAllLibraries() {
    if (myAllLibraries == null) {
      if (myDirectLibraries.isEmpty()) {
        return myDirectLibraries;
      }

      List<EclipseProject> all = new ArrayList<EclipseProject>();
      Set<EclipseProject> seen = Sets.newHashSet();
      Set<EclipseProject> path = Sets.newHashSet();
      seen.add(this);
      path.add(this);
      addLibraryProjects(all, seen, path);
      myAllLibraries = all;
    }

    return myAllLibraries;
  }

  private void addLibraryProjects(@NonNull Collection<EclipseProject> collection,
                                  @NonNull Set<EclipseProject> seen,
                                  @NonNull Set<EclipseProject> path) {
    for (EclipseProject library : myDirectLibraries) {
      if (seen.contains(library)) {
        if (path.contains(library)) {
          myImporter.reportWarning(library, library.getDir(), "Internal error: cyclic library dependency for " + library);
        }
        continue;
      }
      collection.add(library);
      seen.add(library);
      path.add(library);
      // Recurse
      library.addLibraryProjects(collection, seen, path);
      path.remove(library);
    }
  }

  @Override
  public int compareTo(@NonNull EclipseProject other) {
    return myDir.compareTo(other.myDir);
  }

  @Override
  public String toString() {
    return myDir.getPath();
  }

  @Nullable
  public EclipseImportModule getModule() {
    return myModule;
  }

  public void setModule(@Nullable EclipseImportModule module) {
    myModule = module;
  }
}
