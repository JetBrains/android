/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcTarget.cc_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcToolchain.cc_toolchain;
import static com.google.idea.blaze.android.targetmapbuilder.NbJavaTarget.java_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder.targetMap;

import com.google.common.collect.ImmutableList;
import com.google.gson.GsonBuilder;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.AndroidAarIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.cpp.CppBlazeRules;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test new target map builders for correctness by comparing the target maps built with the new and
 * old builders.
 */
@RunWith(JUnit4.class)
public class NbTargetMapBuilderTest extends BlazeIntegrationTestCase {
  @Test
  public void testCcTargetMap() throws Exception {
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:armv7a")
                    .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("arm-linux-androideabi")
                            .setCppExecutable(
                                new ExecutionRootPath("bin/arm-linux-androideabi-gcc"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/arm-linux-androideabi/4.8/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:aarch64")
                    .setKind(CppBlazeRules.RuleTypes.CC_TOOLCHAIN.getKind())
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("aarch64-linux-android")
                            .setCppExecutable(
                                new ExecutionRootPath("bin/aarch64-linux-android-gcc"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/aarch64-linux-android/4.9/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib2")
                    .setKind(CppBlazeRules.RuleTypes.CC_LIBRARY.getKind())
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("bazel-out/android-aarch64/genfiles")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/java/jdk/include")))
                            .addSource(source("java/com/google/jni/native2.cc")))
                    .addSource(source("java/com/google/jni/native2.cc"))
                    .addDependency("//android_ndk_linux/toolchains:aarch64"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib")
                    .setKind(CppBlazeRules.RuleTypes.CC_LIBRARY.getKind())
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("bazel-out/android-armv7a/genfiles")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/java/jdk/include")))
                            .addSource(source("java/com/google/jni/native.cc")))
                    .addSource(source("java/com/google/jni/native.cc"))
                    .addDependency("//java/com/google:native_lib2")
                    .addDependency("//android_ndk_linux/toolchains:armv7a"))
            .build();

    TargetMap newTargetMap =
        targetMap(
            cc_toolchain("//android_ndk_linux/toolchains:armv7a")
                .cc_target_name("arm-linux-androideabi")
                .cpp_executable("bin/arm-linux-androideabi-gcc")
                .built_in_include_dirs("lib/gcc/arm-linux-androideabi/4.8/include"),
            cc_toolchain("//android_ndk_linux/toolchains:aarch64")
                .cc_target_name("aarch64-linux-android")
                .cpp_executable("bin/aarch64-linux-android-gcc")
                .built_in_include_dirs("lib/gcc/aarch64-linux-android/4.9/include"),
            cc_library("//java/com/google:native_lib2")
                .transitive_quote_include_dirs(".", "bazel-out/android-aarch64/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native2.cc")
                .dep("//android_ndk_linux/toolchains:aarch64"),
            cc_library("//java/com/google:native_lib")
                .transitive_quote_include_dirs(".", "bazel-out/android-armv7a/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native.cc")
                .dep("//java/com/google:native_lib2", "//android_ndk_linux/toolchains:armv7a"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testJavaTargetMap() throws Exception {
    // Old target map construction.
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind(JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind())
                    .addSource(source("java/com/google/ClassWithUniqueName1.java"))
                    .addSource(source("java/com/google/ClassWithUniqueName2.java"))
                    .addDependency("//java/com/google:dep")
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("java/com/google/lib.jdeps"))
                            .addJar(
                                LibraryArtifact.builder()
                                    .setClassJar(gen("import/import.generated_jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setLabel("//java/com/google:dep")
                    .setKind(JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind())
                    .setJavaInfo(
                        JavaIdeInfo.builder().setJdepsFile(gen("java/com/google/dep.jdeps"))))
            .build();

    // New target map construction.
    TargetMap newTargetMap =
        targetMap(
            java_library("//java/com/google:lib")
                .src("ClassWithUniqueName1.java", "ClassWithUniqueName2.java")
                .dep("//java/com/google:dep")
                .generated_jar("//import/import.generated_jar"),
            java_library("//java/com/google:dep"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testAarTargetMap() throws Exception {
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:aar")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(AndroidBlazeRules.RuleTypes.AAR_IMPORT.getKind())
                    .setAndroidAarInfo(
                        new AndroidAarIdeInfo(
                            ArtifactLocation.builder()
                                .setRelativePath("import/lib_aar.aar")
                                .setIsSource(true)
                                .build(),
                            /*customJavaPackage=*/ null))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("import/aar.jdeps"))
                            .addJar(
                                LibraryArtifact.builder().setClassJar(gen("import/classes.jar")))))
            .build();

    TargetMap newTargetMap =
        targetMap(aar_import("//import:aar").aar("lib_aar.aar").generated_jar("classes.jar"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testAndroidTargetMap() throws Exception {
    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:liba")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder().setJdepsFile(gen("import/liba.jdeps")))
                    .setAndroidInfo(AndroidIdeInfo.builder().setResourceJavaPackage("import"))
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:lib")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .addSource(source("import/Lib.java"))
                    .setJavaInfo(JavaIdeInfo.builder().setJdepsFile(gen("import/lib.jdeps")))
                    .setAndroidInfo(AndroidIdeInfo.builder().setResourceJavaPackage("import"))
                    .addDependency("//import:import")
                    .addDependency("//import:import_android"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//import:import")
                    .setBuildFile(source("import/BUILD"))
                    .setKind(JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind())
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("import/import.jdeps"))
                            .addJar(
                                LibraryArtifact.builder().setClassJar(gen("import/import.jar")))))
            .build();

    // New target map construction.
    TargetMap newTargetMap =
        targetMap(
            android_library("//import:liba")
                .src("Lib.java")
                .dep("//import:import", "//import:import_android"),
            android_library("//import:lib")
                .src("Lib.java")
                .dep("//import:import", "//import:import_android"),
            java_library("//import:import").generated_jar("import.jar"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  @Test
  public void testComplexAndroidTargetMap() throws Exception {
    String recyclerView = "//third_party/recyclerview:recyclerview";
    String constraintLayout = "//third_party/constraint_layout:constraint_layout";
    String quantum = "//third_party/quantum:values";
    String aarFile = "//third_party/aar:an_aar";
    String individualLibrary = "//third_party/individualLibrary:values";
    String guava = "//third_party/guava:java";
    String main = "//java/com/google:app";
    String intermediateDependency = "//java/com/google/intermediate:intermediate";

    TargetMap oldTargetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(main)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind())
                    .setBuildFile(source("java/com/google/BUILD"))
                    .setJavaInfo(
                        javaInfoWithJars("java/com/google/app.jar")
                            .setJdepsFile(gen("java/com/google/app.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("java/com/google/AndroidManifest.xml"))
                            .addResource(source("java/com/google/res"))
                            .addResource(source("third_party/shared/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google"))
                    .addSource(source("java/com/google/app/MainActivity.java"))
                    .addDependency(guava)
                    .addDependency(quantum)
                    .addDependency(aarFile)
                    .addDependency(intermediateDependency))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(individualLibrary)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(source("third_party/individualLibrary/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("third_party/individualLibrary/values.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(
                                source("third_party/individualLibrary/AndroidManifest.xml"))
                            .addResource(source("third_party/individualLibrary/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("third_party.individualLibrary")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(quantum)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(source("third_party/quantum/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder().setJdepsFile(gen("third_party/quantum/values.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(
                                source("third_party/quantum/manifest/AndroidManifest.xml"))
                            .addResource(
                                AndroidResFolder.builder()
                                    .setRoot(source("third_party/quantum/res"))
                                    .setAar(source("third_party/quantum/resources.aar"))
                                    .build())
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("third_party.quantum")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(guava)
                    .setBuildFile(source("third_party/guava/BUILD"))
                    .setKind(JavaBlazeRules.RuleTypes.JAVA_LIBRARY.getKind())
                    .setJavaInfo(
                        javaInfoWithJars("third_party/guava-21.jar")
                            .setJdepsFile(gen("third_party/guava/java.jdeps"))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(aarFile)
                    .setBuildFile(source("third_party/aar/BUILD"))
                    .setKind(AndroidBlazeRules.RuleTypes.AAR_IMPORT.getKind())
                    .setAndroidAarInfo(
                        new AndroidAarIdeInfo(
                            source("third_party/aar/lib_aar.aar"), /*customJavaPackage=*/ null))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("third_party/aar/an_aar.jdeps"))
                            .addJar(
                                LibraryArtifact.builder()
                                    .setClassJar(
                                        gen(
                                            "third_party/aar/"
                                                + "_aar/an_aar/classes_and_libs_merged.jar")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(recyclerView)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(source("third_party/recyclerview/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("third_party/recyclerview/recyclerview.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(source("third_party/recyclerview/AndroidManifest.xml"))
                            .addResource(source("third_party/recyclerview/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("third_party.recyclerview")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(intermediateDependency)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(source("java/com/google/intermediate/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(gen("java/com/google/intermediate/intermediate.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(
                                source("java/com/google/intermediate/AndroidManifest.xml"))
                            .addResource(source("java/com/google/intermediate/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("com.google.intermediate"))
                    .addDependency(constraintLayout))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(constraintLayout)
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_LIBRARY.getKind())
                    .setBuildFile(source("third_party/constraint_layout/BUILD"))
                    .setJavaInfo(
                        JavaIdeInfo.builder()
                            .setJdepsFile(
                                gen("third_party/constraint_layout/constraint_layout.jdeps")))
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(
                                source("third_party/constraint_layout/AndroidManifest.xml"))
                            .addResource(source("third_party/constraint_layout/res"))
                            .setGenerateResourceClass(true)
                            .setResourceJavaPackage("third_party.constraint_layout")))
            .build();

    // New target map construction.
    TargetMap newTargetMap =
        targetMap(
            android_binary(main)
                .source_jar("app.jar")
                .res("res", "//third_party/shared/res")
                .src("app/MainActivity.java")
                .dep(guava, quantum, aarFile, intermediateDependency),
            android_library(individualLibrary).res("res"),
            android_library(quantum)
                .manifest("manifest/AndroidManifest.xml")
                .res_folder("//third_party/quantum/res", "resources.aar"),
            java_library(guava).source_jar("//third_party/guava-21.jar"),
            aar_import(aarFile)
                .aar("lib_aar.aar")
                .generated_jar("_aar/an_aar/classes_and_libs_merged.jar"),
            android_library(recyclerView).res("res"),
            android_library(intermediateDependency).res("res").dep(constraintLayout),
            android_library(constraintLayout).res("res"));

    assertTargetMapEquivalence(newTargetMap, oldTargetMap);
  }

  private static JavaIdeInfo.Builder javaInfoWithJars(String... relativeJarPaths) {
    JavaIdeInfo.Builder builder = JavaIdeInfo.builder();
    for (String relativeJarPath : relativeJarPaths) {
      ArtifactLocation jar = source(relativeJarPath);
      builder.addJar(LibraryArtifact.builder().setClassJar(jar));
    }
    return builder;
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static ArtifactLocation gen(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(BlazeInfoData.DEFAULT.getRootExecutionPathFragment())
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  private static void assertTargetMapEquivalence(TargetMap actual, TargetMap expected)
      throws IOException {
    assertThat(serializeTargetMap(actual)).isEqualTo(serializeTargetMap(expected));
  }

  private static String serializeTargetMap(TargetMap map) throws IOException {
    // We are serializing the target map with Gson because the toString() method of target maps do
    // not print the whole contents of the map.
    // Serializing it to JSON also makes failure comparison a lot better and makes the diff much
    // easier to understand because it preserves
    // the nested object structure of the target map.
    return new GsonBuilder().setPrettyPrinting().create().toJson(map);
  }
}
