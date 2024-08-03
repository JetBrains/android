/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.studiobot.testing.bazel;

import static com.android.testutils.TestUtils.resolveWorkspacePath;

import com.android.studio.ml.testing.bazel.LastInAipluginTestSuite;
import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * A copy of IdeaTestSuite which is usable by aiplugin without depending on
 * intellij.android.adt.testutils. Only the parts which are used by
 * aiplugin are here, everything else has been removed for simplification.
 */
@JarTestSuiteRunner.FinalizerTest(LastInAipluginTestSuite.class)
public class AipluginTestSuite {
  protected static final String TMP_DIR = System.getProperty("java.io.tmpdir");

  static {
    try {
      System.setProperty("NO_FS_ROOTS_ACCESS_CHECK", "true"); // Bazel tests are sandboxed so we disable VfsRoot checks.
      setProperties();
      setupKotlinPlugin();
    } catch(Throwable e) {
      // See b/143359533 for why we are handling errors here
      System.err.println("ERROR: Error initializing test suite, tests will likely fail following this error");
      e.printStackTrace();
    }
  }

  private static void setProperties() throws IOException {
    System.setProperty("idea.system.path", createTmpDir("idea/system").toString());
    System.setProperty("idea.config.path", createTmpDir("idea/config").toString());
    System.setProperty("idea.force.use.core.classloader", "true");
    System.setProperty("user.home", TMP_DIR);

    // Configure logging.
    System.setProperty("idea.log.path", TestUtils.getTestOutputDir().toString());
    System.setProperty("idea.log.config.properties.file", resolveWorkspacePath("tools/adt/idea/adt-testutils/test-log.properties").toString());
    System.setProperty("idea.split.test.logs", "true"); // For each failing test, write info-level logs to a file instead of stderr.
    System.out.println("See Bazel test outputs for idea.log");

    // Run in headless mode by default. This property is set by the IntelliJ test framework too,
    // but we want to set it sooner before any test initializers have run.
    if (System.getProperty("java.awt.headless") == null) {
      System.setProperty("java.awt.headless", "true");
    }

    // Set roots for java.util.prefs API.
    System.setProperty("java.util.prefs.userRoot", createTmpDir("userRoot").toString());
    System.setProperty("java.util.prefs.systemRoot", createTmpDir("systemRoot").toString());

    // When running tests from the IDE, IntelliJ allows plugin descriptors to be anywhere if a plugin.xml is found in a directory.
    // On bazel we pack each directory in a jar, so we have to tell IJ explicitely that we are still "in directory mode"
    System.setProperty("resolve.descriptors.in.resources", "true");

    // Configure JNA and other native libs.
    Map<String,String>  requiredJvmArgs = readJvmArgsProperties(TestUtils.getBinPath("tools/adt/idea/studio/required_jvm_args.txt"));
    System.setProperty("jna.noclasspath", "true");
    System.setProperty("jna.nosys", "true");
    System.setProperty("jna.boot.library.path",
                       resolveWorkspacePath(requiredJvmArgs.get("jna.boot.library.path")).toString());
    System.setProperty("pty4j.preferred.native.folder",
                       resolveWorkspacePath(requiredJvmArgs.get("pty4j.preferred.native.folder")).toString());

    // TODO(b/213385827): Fix Kotlin script classpath calculation during tests
    System.setProperty("kotlin.script.classpath", "");
  }

  private static Map<String, String> readJvmArgsProperties(Path path) throws IOException {
    Map<String, String> result = new HashMap<>();
    for (String line : Files.readAllLines(path)) {
      int eqIndex = line.indexOf('=');
      if (line.startsWith("-D") && eqIndex > 0) {
        String key = line.substring(2, eqIndex);
        String value = line.substring(eqIndex + 1);
        result.put(key, value);
      }
    }
    return result;
  }

  private static void setupKotlinPlugin() {
    // Run Kotlin in-process for easier control over its JVM args.
    System.setProperty("kotlin.compiler.execution.strategy", "in-process");
  }

  public static Path createTmpDir(String p) {
    Path path = Paths.get(TMP_DIR, p);
    try {
      Files.createDirectories(path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return path;
  }
}
