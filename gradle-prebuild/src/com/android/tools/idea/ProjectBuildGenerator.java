/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.tools.idea;

import com.android.tools.asdriver.tests.AndroidSystem;
import com.android.tools.asdriver.tests.MavenRepo;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates built project files used for pre-building optimization.
 * It takes a pre-indexed and pre-synced project model as input,
 * copies it to a tmp directory, runs gradle assembleDebug, and copies it back.
 */
public class ProjectBuildGenerator {

  private static final String GRADLE_OPTIMIZATIONS =
      "\nsystemProp.kotlin.daemon.custom.run.files.path.for.tests=build/kotlin-daemon-runfiles\n" +
      "org.gradle.jvmargs=-Xmx2048M -XX:+UseParallelGC -Dkotlin.daemon.jvm.options\\=\"-Xmx2048M\"\n" +
      "org.gradle.tooling.parallel=true\n";

  private static final String EMPTY_DIR_SCRIPT_KTS = "\n" +
      "val emptyDirsFile = File(rootDir, \"empty_directories.txt\")\n" +
      "if (emptyDirsFile.exists()) {\n" +
      "    emptyDirsFile.forEachLine { line ->\n" +
      "        File(rootDir, line).mkdirs()\n" +
      "    }\n" +
      "}\n";

  private static final String EMPTY_DIR_SCRIPT_GROOVY = "\n" +
      "def emptyDirsFile = new File(rootDir, 'empty_directories.txt')\n" +
      "if (emptyDirsFile.exists()) {\n" +
      "    emptyDirsFile.eachLine { line ->\n" +
      "        new File(rootDir, line).mkdirs()\n" +
      "    }\n" +
      "}\n";

  public static void main(String[] args) throws Exception {
    if (args.length < 5) {
      System.err.println("Usage: ProjectBuildGenerator <output_dir> <input_model_dir> <project_name> <manifest_path> <gradle_zip_path>");
      System.exit(1);
    }

    Path outputDir = Path.of(args[0]);
    Path inputModelDir = Path.of(args[1]);
    String projectName = args[2];
    String manifestPath = args[3];
    Path gradleZipPath = Path.of(args[4]);

    AndroidSystem system = AndroidSystem.standardWithTmpDir();
    Path tempDir = system.getInstallation().getTmpDir();

    Path projectDir = tempDir.resolve(projectName);
    if (Files.exists(inputModelDir.resolve(projectName))) {
        FileUtils.copyDirectory(inputModelDir.toFile(), tempDir.toFile());
    } else {
        Files.createDirectories(projectDir);
        FileUtils.copyDirectory(inputModelDir.toFile(), projectDir.toFile());
    }
    copyGradleDistribution(gradleZipPath, tempDir);
    injectGradle(projectDir, gradleZipPath);
    writeLocalProperties(projectDir, system.getInstallation().getSdkDir());
    String repoUrl = setupOfflineMavenRepo(tempDir, manifestPath, system);
    Path initScript = createInitScript(tempDir, repoUrl);
    configureGradleCaching(projectDir);
    addGradleBuildOptimizations(projectDir);
    executeGradleBuild(projectDir, tempDir, initScript);
    recordEmptyDirectories(projectDir);

    cleanupTempDir(projectDir, tempDir);

    Files.createDirectories(outputDir);
    FileUtils.copyDirectory(tempDir.toFile(), outputDir.toFile());
  }

  private static void copyGradleDistribution(Path gradleZipPath, Path tempDir) throws IOException {
    // Copy gradle distribution from runfiles to tempDir
    // because gradle-wrapper.properties expects it at ../../../gradle-<version>-bin.zip
    if (Files.exists(gradleZipPath)) {
        System.out.println("Found gradle zip: " + gradleZipPath);
        Path dest = tempDir.resolve(gradleZipPath.getFileName());
        if (!Files.exists(dest)) {
            Files.copy(gradleZipPath, dest);
            System.out.println("Copied to: " + dest);
        }
    } else {
        System.err.println("Warning: Gradle zip not found at " + gradleZipPath);
    }
  }

  private static void injectGradle(Path projectDir, Path gradleZipPath) throws IOException {
    Path wrapper = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
    if (!Files.exists(wrapper)) {
        System.err.println("Warning: gradle-wrapper.properties not found at " + wrapper);
        return;
    }
    String content = Files.readString(wrapper);
    String distributionFileName = gradleZipPath.getFileName().toString();
    Path projectRootDist = projectDir.getParent().resolve(distributionFileName);

    Path wrapperDir = wrapper.getParent();
    String relativeUrl = wrapperDir.relativize(projectRootDist).toString().replace('\\', '/');
    String newContent = content.replaceAll("distributionUrl=.*", java.util.regex.Matcher.quoteReplacement("distributionUrl=" + relativeUrl));
    if (!newContent.equals(content)) {
        Files.writeString(wrapper, newContent);
        System.out.println("Injected Gradle distribution " + relativeUrl + " into " + wrapper);
    }
  }

  private static void writeLocalProperties(Path projectDir, Path sdkDir) throws IOException {
    Path localProperties = projectDir.resolve("local.properties");
    String content = "sdk.dir=" + sdkDir.toAbsolutePath().toString().replace("\\", "/");
    Files.writeString(localProperties, content);
    System.out.println("Wrote local.properties to: " + localProperties);
  }

  private static String setupOfflineMavenRepo(Path tempDir, String manifestPath, AndroidSystem system) throws Exception {
    MavenRepo repo = new MavenRepo(manifestPath);
    HashMap<String, String> env = new HashMap<>();
    repo.install(tempDir, system.getInstallation(), env);
    return new File(env.get("STUDIO_CUSTOM_REPO")).toURI().toString();
  }

  private static Path createInitScript(Path tempDir, String repoUrl) throws IOException {
    Path initScript = tempDir.resolve("init.gradle");
    String initScriptContent = "allprojects {\n" +
        "  buildscript {\n" +
        "    repositories {\n" +
        "      maven { url '" + repoUrl + "' }\n" +
        "    }\n" +
        "  }\n" +
        "  repositories {\n" +
        "    maven { url '" + repoUrl + "' }\n" +
        "  }\n" +
        "}\n" +
        "settingsEvaluated { settings ->\n" +
        "  settings.pluginManagement {\n" +
        "    repositories {\n" +
        "      maven { url '" + repoUrl + "' }\n" +
        "    }\n" +
        "  }\n" +
        "}\n";
    Files.writeString(initScript, initScriptContent);
    return initScript;
  }

  private static void addGradleBuildOptimizations(Path projectDir) throws IOException {
    Path gradleProperties = projectDir.resolve("gradle.properties");
    String existingProps = Files.exists(gradleProperties) ? Files.readString(gradleProperties) : "";
    Files.writeString(gradleProperties, existingProps + GRADLE_OPTIMIZATIONS);

    // Some test projects (like kmpapp) configure kotlin.daemon.custom.run.files.path.for.tests
    // to build/kotlin-daemon-runfiles, but the folder may not exist yet when the daemon starts.
    Files.createDirectories(projectDir.resolve("build").resolve("kotlin-daemon-runfiles"));
  }

  private static void configureGradleCaching(Path projectDir) throws IOException {
    Path gradleProperties = projectDir.resolve("gradle.properties");
    String existingProps = Files.exists(gradleProperties) ? Files.readString(gradleProperties) : "";
    Files.writeString(gradleProperties, existingProps + "\norg.gradle.caching=true\n");

    Path settingsGradle = projectDir.resolve("settings.gradle");
    Path settingsGradleKts = projectDir.resolve("settings.gradle.kts");

    if (Files.exists(settingsGradleKts)) {
        String existingSettings = Files.readString(settingsGradleKts);
        String cacheConfig = "\nbuildCache {\n  local {\n    directory = File(rootDir, \".gradle-build-cache\")\n  }\n}\n";
        Files.writeString(settingsGradleKts, existingSettings + cacheConfig);
    } else {
        String existingSettings = Files.exists(settingsGradle) ? Files.readString(settingsGradle) : "";
        String cacheConfig = "\nbuildCache {\n  local {\n    directory = new File(rootDir, '.gradle-build-cache')\n  }\n}\n";
        Files.writeString(settingsGradle, existingSettings + cacheConfig);
    }
  }

  private static void executeGradleBuild(Path projectDir, Path tempDir, Path initScript) throws IOException, InterruptedException {
    String gradlew = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "gradlew.bat" : "gradlew";
    File gradlewFile = new File(projectDir.toFile(), gradlew);
    if (!gradlewFile.exists()) {
        throw new RuntimeException("gradlew not found in " + projectDir);
    }
    gradlewFile.setExecutable(true);

    ProcessBuilder pb = new ProcessBuilder(
        gradlewFile.getAbsolutePath(),
        "assembleDebug",
        "--stacktrace",
        "-Pandroid.injected.invoked.from.ide=true",
        "--init-script",
        initScript.toAbsolutePath().toString()
    );

    Path gradleUserHome = tempDir.resolve(".gradle");
    Files.createDirectories(gradleUserHome);
    pb.environment().put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());

    Path androidUserHome = tempDir.resolve(".android");
    Files.createDirectories(androidUserHome);
    pb.environment().put("ANDROID_USER_HOME", androidUserHome.toAbsolutePath().toString());

    // Set JAVA_HOME so gradlew can find java
    String javaHome = System.getProperty("java.home");
    pb.environment().put("JAVA_HOME", javaHome);

    pb.directory(projectDir.toFile());
    pb.inheritIO();
    Process process = pb.start();
    int exitCode = process.waitFor();
    if (exitCode != 0) {
        throw new RuntimeException("Gradle build failed with exit code " + exitCode);
    }
  }

  /**
   * Records empty directories to a text file and injects a script into settings.gradle to recreate them.
   * This is necessary because Bazel runfiles do not preserve empty directories. If these directories
   * are missing when the test runs, it can cause cache invalidation or crashes for certain Gradle tasks.
   */
  private static void recordEmptyDirectories(Path projectDir) throws IOException {
    Path emptyDirsFile = projectDir.resolve("empty_directories.txt");
    try (Stream<Path> stream = Files.walk(projectDir)) {
        List<String> emptyDirs = stream
            .filter(Files::isDirectory)
            .filter(dir -> {
                try (Stream<Path> children = Files.list(dir)) {
                    return children.findAny().isEmpty();
                } catch (Exception e) { return false; }
            })
            .map(dir -> projectDir.relativize(dir).toString())
            .map(pathStr -> pathStr.replace("\\", "/"))
            .collect(Collectors.toList());
        if (!emptyDirs.isEmpty()) {
            Files.write(emptyDirsFile, emptyDirs);

            Path sGradle = projectDir.resolve("settings.gradle");
            Path sGradleKts = projectDir.resolve("settings.gradle.kts");
            if (Files.exists(sGradleKts)) {
                Files.writeString(sGradleKts, Files.readString(sGradleKts) + EMPTY_DIR_SCRIPT_KTS);
            } else {
                String existingSettings = Files.exists(sGradle) ? Files.readString(sGradle) : "";
                Files.writeString(sGradle, existingSettings + EMPTY_DIR_SCRIPT_GROOVY);
            }
        }
    }
  }

  private static void cleanupTempDir(Path projectDir, Path tempDir) throws IOException, InterruptedException {
    Path gradleUserHome = tempDir.resolve(".gradle");
    // Stop Gradle daemon before cleaning up
    String gradlew = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "gradlew.bat" : "gradlew";
    File gradlewFile = new File(projectDir.toFile(), gradlew);
    if (gradlewFile.exists()) {
        ProcessBuilder pb = new ProcessBuilder(
            gradlewFile.getAbsolutePath(),
            "--stop"
        );
        pb.environment().put("GRADLE_USER_HOME", gradleUserHome.toAbsolutePath().toString());
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"));
        pb.directory(projectDir.toFile());
        pb.inheritIO();
        Process process = pb.start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            System.err.println("Warning: Gradle daemon failed to stop gracefully in 30s. Killed.");
        }
    }

    FileUtils.deleteRecursivelyIfExists(tempDir.resolve("offline-repo").toFile());
    FileUtils.deleteRecursivelyIfExists(gradleUserHome.toFile());
    Files.deleteIfExists(tempDir.resolve("init.gradle"));
    try (Stream<Path> stream = Files.list(tempDir)) {
        stream.filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete zip file: " + p, e);
            }
        });
    }
  }
}
