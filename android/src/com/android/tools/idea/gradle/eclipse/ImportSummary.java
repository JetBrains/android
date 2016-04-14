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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.repository.Revision;
import com.android.utils.SdkUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Records information about the import to be presented to the user:
 * <ul>
 *    <li>List of files *not* migrated</li>
 *    <li>Explain that the files were moved into the canonical gradle directory
 *    structure and explain what it is</li>
 *    <li>A summary of the file changes (files moved from where to where)</li>
 *    <li>Tips for things to do next (e.g. create signing configs, flavors, etc</li>
 *    <li>Warning if manifest merger was not enabled before AND there are libraries
 *    without empty manifests</li>
 *    <li>Warning if I've replaced a .jar with a dependency of unknown version</li>
 *    <li>TODO: End with a section of migration tips for Eclipse users (e.g. to not look
 *    for the Problems view, how to use Eclipse key bindings, etc.</li>
 *     </ul>
 */
public class ImportSummary {
  static final String MSG_HEADER = ""
          + "ECLIPSE ANDROID PROJECT IMPORT SUMMARY\n"
          + "======================================\n";

  static final String MSG_MANIFEST = "\n"
          + "Manifest Merging:\n"
          + "-----------------\n"
          + "Your project uses libraries that provide manifests, and your Eclipse\n"
          + "project did not explicitly turn on manifest merging. In Android Gradle\n"
          + "projects, manifests are always merged (meaning that contents from your\n"
          + "libraries' manifests will be merged into the app manifest. If you had\n"
          + "manually copied contents from library manifests into your app manifest\n"
          + "you may need to remove these for the app to build correctly.\n";

  static final String MSG_UNHANDLED = "\n"
          + "Ignored Files:\n"
          + "--------------\n"
          + "The following files were *not* copied into the new Gradle project; you\n"
          + "should evaluate whether these are still needed in your project and if\n"
          + "so manually move them:\n\n";

  static final String MSG_REPLACED_JARS = "\n"
          + "Replaced Jars with Dependencies:\n"
          + "--------------------------------\n"
          + "The importer recognized the following .jar files as third party\n"
          + "libraries and replaced them with Gradle dependencies instead. This has\n"
          + "the advantage that more explicit version information is known, and the\n"
          + "libraries can be updated automatically. However, it is possible that\n"
          + "the .jar file in your project was of an older version than the\n"
          + "dependency we picked, which could render the project not compileable.\n"
          + "You can disable the jar replacement in the import wizard and try again:\n\n";

  static final String MSG_REPLACED_LIBS = "\n"
          + "Replaced Libraries with Dependencies:\n"
          + "-------------------------------------\n"
          + "The importer recognized the following library projects as third party\n"
          + "libraries and replaced them with Gradle dependencies instead. This has\n"
          + "the advantage that more explicit version information is known, and the\n"
          + "libraries can be updated automatically. However, it is possible that\n"
          + "the source files in your project were of an older version than the\n"
          + "dependency we picked, which could render the project not compileable.\n"
          + "You can disable the library replacement in the import wizard and try\n"
          + "again:\n\n";

  static final String MSG_FOOTER = "\n"
          + "Next Steps:\n"
          + "-----------\n"
          + "You can now build the project. The Gradle project needs network\n"
          + "connectivity to download dependencies.\n"
          + "\n"
          + "Bugs:\n"
          + "-----\n"
          + "If for some reason your project does not build, and you determine that\n"
          + "it is due to a bug or limitation of the Eclipse to Gradle importer,\n"
          + "please file a bug at http://b.android.com with category\n"
          + "Component-Tools.\n"
          + "\n"
          + "(This import summary is for your information only, and can be deleted\n"
          + "after import once you are satisfied with the results.)\n";

  static final String MSG_FOLDER_STRUCTURE = "\n"
          + "Moved Files:\n"
          + "------------\n"
          + "Android Gradle projects use a different directory structure than ADT\n"
          + "Eclipse projects. Here's how the projects were restructured:\n\n";

  static final String MSG_MISSING_REPO_1 = "\n"
          + "Missing Android Support Repository:\n"
          + "-----------------------------------\n"
          + "Some useful libraries, such as the Android Support Library, are\n"
          + "installed from a special Maven repository, which should be installed\n"
          + "via the SDK manager.\n"
          + "\n"
          + "It looks like this library is missing from your SDK installation at:\n";

  static final String MSG_MISSING_REPO_2 = "\n"
          + "To install it, open the SDK manager, and in the Extras category,\n"
          + "select \"Android Support Repository\". You may also want to install the\n"
          + "\"Google Repository\" if you want to use libraries like Google Play\n"
          + "Services.\n";

  static final String MSG_MISSING_GOOGLE_REPOSITORY_1 = "\n"
          + "Missing Google Repository:\n"
          + "--------------------------\n"
          + "The Google Play Services library is installed from a special Maven\n"
          + "Repository, which should be installed via the SDK manager.\n"
          + "\n"
          + "It looks like this library is missing from your SDK installation at:\n";

  static final String MSG_MISSING_GOOGLE_REPOSITORY_2 = "\n"
          + "To install it, open the SDK manager, and in the Extras category,\n"
          + "select \"Google Repository\".\n";

  static final String MSG_BUILD_TOOLS_VERSION = "\n"
          + "Old Build Tools:\n"
          + "----------------\n"
          + "The version of the build tools installed with your SDK is old. It\n"
          + "should be at least version 19.0.1 to work well with the Gradle build\n"
          + "system. To update it, open the Android SDK Manager, and install the\n"
          + "highest available version of Tools > Android SDK Build-tools.\n";

  static final String MSG_GUESSED_VERSIONS = "\n"
          + "Potentially Missing Dependency:\n"
          + "-------------------------------\n"
          + "When we replaced the following .jar files with a Gradle dependency, we\n"
          + "inferred the dependency version number from the filename. This\n"
          + "specific version may not actually be available from the repository.\n"
          + "If you get a build error stating that the dependency is missing, edit\n"
          + "the version number to for example \"+\" to pick up the latest version\n"
          + "instead. (This may require you to update your code if the library APIs\n"
          + "have changed.)\n\n";

  static final String MSG_USER_HOME_PROGUARD = "\n"
          + "Ignored Per-User ProGuard Configuration File:\n"
          + "---------------------------------------------\n"
          + "The ProGuard configuration in the imported project pointed to a\n"
          + "ProGuard rule file in the current user's home directory. This is not\n"
          + "supported from the Android Gradle build system (which emphasizes\n"
          + "repeatable builds). If you want to share ProGuard rules between\n"
          + "projects, use relative paths (from the project location) instead.\n";

  static final String MSG_RISKY_PROJECT_LOCATION = "\n"
          + "Risky Project Location:\n"
          + "-----------------------\n"
          + "The tools *should* handle project locations in any directory. However,\n"
          + "due to bugs, placing projects in directories containing spaces in the\n"
          + "path, or characters like \", ' and &, have had issues. We're working to\n"
          + "eliminate these bugs, but to save yourself headaches you may want to\n"
          + "move your project to a location where this is not a problem.\n";

  private final GradleImport myImporter;
  private File myDestDir;
  private boolean myManifestsMayDiffer;
  private Map<String, List<String>> myNotMigrated = Maps.newHashMap();
  private Map<ImportModule, Map<File, File>> myMoved = Maps.newHashMap();
  private Map<File, GradleCoordinate> myJarDependencies = Maps.newHashMap();
  private Map<String, List<GradleCoordinate>> myLibDependencies = Maps.newHashMap();
  private List<String> myGuessedDependencyVersions = Lists.newArrayList();
  private File myLastGuessedJar;
  private List<String> myIgnoredUserHomeProGuardFiles = Lists.newArrayList();
  private boolean myHasRiskyPathChars;
  private boolean myWrapErrorMessages = true;

  ImportSummary(@NonNull GradleImport importer) {
    myImporter = importer;
  }

  private static boolean isRiskyPathChar(char c) {
    return (c == ' ' || c == '\'' || c == '"' || c == '&');
  }

  /**
   * Writes the summary to the given file. The file should be in a directory which
   * has already been created by the caller.
   */
  public void write(@NonNull File file) throws IOException {
    String summary = createSummary();
    assert file.getParentFile().exists();
    Files.write(summary, file, Charsets.UTF_8);
  }

  public void setDestDir(File destDir) {
    myDestDir = destDir;

    myHasRiskyPathChars = false;
    String path = destDir.getPath();
    for (int i = 0, n = path.length(); i < n; i++) {
      char c = path.charAt(i);
      if (isRiskyPathChar(c)) {
        myHasRiskyPathChars = true;
      }
    }
  }

  @VisibleForTesting
  void setWrapErrorMessages(boolean wrap) {
    myWrapErrorMessages = wrap;
  }

  public void reportManifestsMayDiffer() {
    myManifestsMayDiffer = true;
  }

  public void reportReplacedJar(@NonNull File jar, @NonNull GradleCoordinate dependency) {
    myJarDependencies.put(jar, dependency);
    if (jar.equals(myLastGuessedJar)) {
      boolean replaced = myGuessedDependencyVersions.remove(jar.getName());
      if (replaced) {
        myGuessedDependencyVersions.add(jar.getName() + " => version " +
                                        dependency.getRevision() + " in " + dependency.toString());
      }
      myLastGuessedJar = null;
    }
  }

  public void reportReplacedLib(@NonNull String module, @NonNull List<GradleCoordinate> dependencies) {
    myLibDependencies.put(module, dependencies);
  }

  public void reportGuessedVersion(@NonNull File jar) {
    myGuessedDependencyVersions.add(jar.getName());
    myLastGuessedJar = jar;
  }

  public void reportIgnoredUserHomeProGuardFile(@NonNull String relativePath) {
    myIgnoredUserHomeProGuardFiles.add(relativePath);
  }

  public void reportMoved(@NonNull ImportModule module, @NonNull File from, @NonNull File to) {
    Map<File, File> map = myMoved.get(module);
    if (map == null) {
      map = new LinkedHashMap<File, File>(); // preserve insert order
      myMoved.put(module, map);
    }
    map.put(from, to);
  }

  /**
   * Reports an ignored relative path. (We use a path string rather than a file since
   * we want to include a trailing file separator on directories; these relative paths are
   * not interpreted in any way other than to display in the report.)
   */
  public void reportIgnored(@NonNull String module, @NonNull String path) {
    List<String> list = myNotMigrated.get(module);
    if (list == null) {
      list = Lists.newArrayList();
      myNotMigrated.put(module, list);
    }
    list.add(path);
  }

  /**
   * Provides the summary
   */
  @NonNull
  public String createSummary() {
    StringBuilder sb = new StringBuilder(2000);
    sb.append(MSG_HEADER);

    List<String> problems = Lists.newArrayList();
    problems.addAll(myImporter.getErrors());
    problems.addAll(myImporter.getWarnings());
    if (!problems.isEmpty()) {
      sb.append("\n");
      for (String warning : problems) {
        sb.append(" * ");
        if (myWrapErrorMessages) {
          sb.append(SdkUtils.wrap(warning, 80, "   "));
        }
        else {
          sb.append(warning);
        }
        sb.append("\n");
      }
    }

    if (myHasRiskyPathChars) {
      sb.append(MSG_RISKY_PROJECT_LOCATION);
      String path = myDestDir.getPath();
      sb.append(path).append("\n");
      for (int i = 0, n = path.length(); i < n; i++) {
        char c = path.charAt(i);
        sb.append(isRiskyPathChar(c) ? '-' : ' ');
      }
      sb.append("\n");
    }

    if (myManifestsMayDiffer) {
      sb.append(MSG_MANIFEST);
    }

    if (!myNotMigrated.isEmpty()) {
      sb.append(MSG_UNHANDLED);
      List<String> modules = Lists.newArrayList(myNotMigrated.keySet());
      Collections.sort(modules);
      for (String module : modules) {
        if (modules.size() > 1) {
          sb.append("From ").append(module).append(":\n");
        }
        List<String> sorted = new ArrayList<String>(myNotMigrated.get(module));
        Collections.sort(sorted);
        for (String path : sorted) {
          sb.append("* ").append(path).append("\n");
        }
      }
    }

    if (!myJarDependencies.isEmpty()) {
      sb.append(MSG_REPLACED_JARS);
      // TODO: Also add note here about switching to AAR's potentially also creating
      // compilation errors because it now enforces that app min sdk version is >= library
      // min sdk version, and suggesting that they re-run import with replaceJars=false
      // if this leads to problems.
      List<File> files = Lists.newArrayList(myJarDependencies.keySet());
      Collections.sort(files);
      for (File file : files) {
        String jar = file.getName();
        GradleCoordinate dependency = myJarDependencies.get(file);
        sb.append(jar).append(" => ").append(dependency).append("\n");
      }
    }

    if (!myGuessedDependencyVersions.isEmpty()) {
      sb.append(MSG_GUESSED_VERSIONS);
      Collections.sort(myGuessedDependencyVersions);
      for (String replaced : myGuessedDependencyVersions) {
        sb.append(replaced).append("\n");
      }
    }

    if (!myLibDependencies.isEmpty()) {
      sb.append(MSG_REPLACED_LIBS);
      List<String> modules = Lists.newArrayList(myLibDependencies.keySet());
      Collections.sort(modules);
      for (String module : modules) {
        List<GradleCoordinate> dependencies = myLibDependencies.get(module);
        if (dependencies.size() == 1) {
          sb.append(module).append(" => ").append(dependencies).append("\n");
        }
        else {
          sb.append(module).append(" =>\n");
          for (GradleCoordinate dependency : dependencies) {
            sb.append("    ").append(dependency).append("\n");
          }
        }
      }
    }

    if (!myMoved.isEmpty()) {
      sb.append(MSG_FOLDER_STRUCTURE);
      List<ImportModule> modules = Lists.newArrayList(myMoved.keySet());
      Collections.sort(modules);
      for (ImportModule module : modules) {
        if (modules.size() > 1) {
          sb.append("In ").append(module.getOriginalName()).append(":\n");
        }
        Map<File, File> map = myMoved.get(module);
        List<File> sorted = new ArrayList<File>(map.keySet());
        Collections.sort(sorted);
        for (File from : sorted) {
          sb.append("* ");
          File to = map.get(from);
          assert to != null : from;

          File fromRelative = null;
          File toRelative = null;
          try {
            fromRelative = module.computeProjectRelativePath(from);
            if (myDestDir != null) {
              toRelative = GradleImport.computeRelativePath(myDestDir.getCanonicalFile(), to);
            }
          }
          catch (IOException ioe) {
            // pass; use full path
          }
          if (fromRelative == null) {
            fromRelative = from;
          }
          if (toRelative == null) {
            toRelative = to;
          }
          sb.append(fromRelative.getPath());
          if (from.isDirectory()) {
            sb.append(File.separator);
          }
          sb.append(" => ");
          sb.append(toRelative.getPath());
          if (to.isDirectory()) {
            sb.append(File.separator);
          }
          sb.append("\n");
        }
      }
    }

    if (myImporter.needSupportRepository() && myImporter.isMissingSupportRepository()) {
      sb.append(MSG_MISSING_REPO_1);
      sb.append(myImporter.getSdkLocation()).append("\n");
      sb.append(MSG_MISSING_REPO_2);
    }

    if (myImporter.needGoogleRepository() && myImporter.isMissingGoogleRepository()) {
      sb.append(MSG_MISSING_GOOGLE_REPOSITORY_1);
      sb.append(myImporter.getSdkLocation()).append("\n");
      sb.append(MSG_MISSING_GOOGLE_REPOSITORY_2);
    }

    if (Revision.parseRevision(myImporter.getBuildToolsVersion()).getMajor() < 19) {
      sb.append(MSG_BUILD_TOOLS_VERSION);
    }

    if (!myIgnoredUserHomeProGuardFiles.isEmpty()) {
      sb.append(MSG_USER_HOME_PROGUARD);
      Collections.sort(myIgnoredUserHomeProGuardFiles);
      for (String path : myIgnoredUserHomeProGuardFiles) {
        sb.append(path).append("\n");
      }
    }

    sb.append(MSG_FOOTER);

    // TODO: Add further suggestions:
    // - Consider removing uses-sdk elements and versionName/Code from manifest (such that it's
    //   only in the Gradle file)
    // - Mention that we switched over to compileSdkVersion and buildToolsVersion 19 (to pick
    //   up on necessary gradle support). If the tools relied on building with older APIs,
    //   be aware of changes. (Mention API lint (gradlew lint) to prevent accidental API
    //   usage.)

    return sb.toString().replace("\n", GradleImport.NL);
  }
}
