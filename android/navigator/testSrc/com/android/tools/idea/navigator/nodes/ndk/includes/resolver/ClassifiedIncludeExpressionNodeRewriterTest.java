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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver;

import static com.android.tools.idea.navigator.nodes.ndk.includes.resolver.ResolverTests.PATH_TO_NDK;
import static com.android.tools.idea.navigator.nodes.ndk.includes.resolver.ResolverTests.ROOT_OF_RELATIVE_INCLUDE_PATHS;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.navigator.nodes.ndk.includes.RealWorldExamples;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValues;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageFamilyValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.ShadowingIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import com.android.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ClassifiedIncludeExpressionNodeRewriterTest {
  private static void assertContainsInOrder(String value, String... lines) {
    int lastSeen = -1;
    for (String line : lines) {
      int pos = value.indexOf(line);
      assertThat(pos).named(line).isGreaterThan(lastSeen);
      lastSeen = pos;
    }
  }

  @Test
  public void exerciseRealWorldExamples() {
    int i = 0;
    for (List<String> includes : RealWorldExamples.getConcreteCompilerIncludeFlags(PATH_TO_NDK) ) {
      IncludeSet set = new IncludeSet();
      set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
      List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
      assertThat(dependencies).isNotEmpty();
      StringBuilder sb = new StringBuilder();
      printDependencies(sb, dependencies, 0);
      System.out.printf("------ %d\n%s", i, sb);
      ++i;
    }
  }

  @Test
  public void testRenderscriptExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.RENDERSCRIPT_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(2);
    assertContainsInOrder(result,
      "NDK Components (/path/to/ndk-bundle)",
      "    renderscript (NDK Components, /path/to/ndk-bundle, /toolchains/renderscript/prebuilt/android-21-x86_64/lib/clang/3.5/include/)");
  }

  @Test
  public void twoIncludesSameName() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.TWO_INCLUDES_SAME_BASE_NAME);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("Shadow Group\n" +
                                       "        include (Include Folders, /project/include, /)\n" +
                                       "        include (Include Folders, /project/subproject/include, /)");
  }

  @Test
  public void twoWindowsIncludesSameName() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.TWO_WINDOWS_INCLUDES_SAME_BASE_NAME);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("Shadow Group\n" +
                                       "        include (Include Folders, D:/project/include, /)\n" +
                                       "        include (Include Folders, D:/project/subproject/include, /)");
  }

  @Test
  public void twoWindowsIncludesSameNameDoubleSlashes() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.TWO_WINDOWS_INCLUDES_SAME_BASE_NAME_DOUBLE_SLASHES);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("Shadow Group\n" +
                                       "        include (Include Folders, D:/project/include, /)\n" +
                                       "        include (Include Folders, D:/project/subproject/include, /)");
  }

  @Test
  public void twoIdenticalIncludes() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.TWO_IDENTICAL_INCLUDES);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("include (Include Folders, /project/include, /)");
  }

  @Test
  public void testCDepExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.CDEP_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(3);
    assertContainsInOrder(result,
                          "OpenCV (Third Party Packages, /usr/local/google/home/jomof, /third_party/OpenCV/include/)",
                          "NDK Components (/path/to/ndk-bundle)",
                          "    android-15 (NDK Components, /path/to/ndk-bundle, /platforms/android-15/arch-x86/)",
                          "    gnu-libstdc++ (NDK Components, sources/cxx-stl/gnu-libstdc++/4.9)",
                          "        gnu-libstdc++ (NDK Components, /path/to/ndk-bundle, /sources/cxx-stl/gnu-libstdc++/4.9/include/backward/)",
                          "CDep Packages (/usr/local/google/home/jomof/projects/cdep-android-studio-freetype-sample/build/cdep/exploded)",
                          "    protobuf (CDep Packages, com.github.jomof/protobuf/3.2.0-rev0)",
                          "        protobuf (CDep Packages, /usr/local/google/home/jomof/projects/cdep-android-studio-freetype-sample/build/cdep/exploded, /com.github.jomof/protobuf/3.2.0-rev0/protobuf-android-cxx-platform-12.zip/include/)",
                          "    stb/dxt (CDep Packages, /usr/local/google/home/jomof/projects/cdep-android-studio-freetype-sample/build/cdep/exploded, /com.github.jomof/stb/dxt/0.0.0-rev6/stb-dxt-headers.zip/include/)");
  }

  @Test
  public void testDolphinExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.DOLPHIN_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    String rootPath = FileUtils.toSystemIndependentPath(new File("/a/b").getAbsolutePath()); // Account for Windows drive
    assertThat(dependencies).hasSize(3);
    assertContainsInOrder(result,
                          "NDK Components (/path/to/ndk-bundle)",
                          "Externals (" + rootPath + ")",
                          "    SFML (Externals, " + rootPath + ", /Externals/SFML/include/)",
                          "    minizip (Externals, 2 include paths)");
  }

  @Test
  public void testCocosExternalRoot() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.COCOS_EXTERNAL_ROOT_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("external (Include Folders, /usr/local/google/home/jomof/projects/Game/cocos2d/external, /)");
  }

  @Test
  public void testCocosEditorSupportRoot() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.COCOS_EDITOR_SUPPORT_ROOT_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("editor-support (Include Folders, /usr/local/google/home/jomof/projects/Game/cocos2d/cocos/editor-support, /)");
  }

  @Test
  public void testCocosExternal() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.COCOS_EXTERNAL_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertThat(result).contains("xxhash (Cocos Third Party Packages, /usr/local/google/home/jomof/projects/Game/cocos2d, /external/xxhash/)");
  }

  @Test
  public void testNdkSpecialPackagesExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.NDK_SPECIAL_PACKAGES_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(2);
    assertContainsInOrder(result,
                          "jni (Include Folders, /path/to/ndk-bundle/samples/Teapot/jni, /)",
                          "NDK Components (/path/to/ndk-bundle)",
                          "    CPU Features (NDK Components, /path/to/ndk-bundle, /sources/android/cpufeatures/)",
                          "    Helper (NDK Components, /path/to/ndk-bundle, /sources/android/ndk_helper/)",
                          "    Native App Glue (NDK Components, /path/to/ndk-bundle, /sources/android/native_app_glue/)",
                          "    android-21 (NDK Components, /path/to/ndk-bundle, /platforms/android-21/arch-arm64/usr/include/)",
                          "    gabi++ (NDK Components, /path/to/ndk-bundle, /sources/cxx-stl/gabi++/include/)",
                          "    googletest (NDK Components, /path/to/ndk-bundle, /sources/third_party/googletest/googletest/include/)");
  }

  @Test
  public void testNdkSxsExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.NDK_R19_SXS_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertContainsInOrder(result,
                          "NDK r19c (/usr/local/google/home/jomof/Android/Sdk/ndk/19.2.5345600)",
                          "LLVM (NDK r19c, /usr/local/google/home/jomof/Android/Sdk/ndk/19.2.5345600, " +
                          "/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/usr/include/)");
  }

  @Test
  public void testNdkDotZeroSxsExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.NDK_R19_0_SXS_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    // The tag should be NDK r19 not NDK r19a ('a' is elided)
    assertContainsInOrder(result,
                          "NDK r19 (/usr/local/google/home/jomof/Android/Sdk/ndk/19.0.5345600)",
                          "LLVM (NDK r19, /usr/local/google/home/jomof/Android/Sdk/ndk/19.0.5345600, " +
                          "/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/include/usr/include/)");
  }

  @Test
  public void testNdk18SxsExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.NDK_R18_SXS_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertContainsInOrder(result,
                          "NDK r18b (/usr/local/google/home/jomof/Android/Sdk/ndk/18.1.5063045)",
                          "    Support (NDK r18b, /usr/local/google/home/jomof/Android/Sdk/ndk/18.1.5063045, /sources/android/support/include/)",
                          "    Sysroot (NDK, sysroot/usr/include)");
  }

  @Test
  public void testNdkLegacyExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.NDK_R19_LEGACY_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertContainsInOrder(result,
                          "NDK Components (/path/to/ndk-bundle)",
                          " LLVM (NDK Components, /path/to/ndk-bundle, " +
                          "/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include/)");
  }

  @Test
  public void testMiniCocosExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.MINI_COCOS_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(1);
    assertContainsInOrder(result,
                          "poly2tri (Cocos Third Party Packages, external/poly2tri)",
                          "    poly2tri (Cocos Third Party Packages, /usr/local/google/home/jomof/projects/Game/cocos2d, /external/poly2tri/sweep/)");
  }

  @Test
  public void testCocosExample() {
    List<String> includes = RealWorldExamples.getConcreteCompilerIncludeFlags(
      PATH_TO_NDK,
      RealWorldExamples.COCOS_EXAMPLE);
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
    List<? extends IncludeValue> dependencies = getRewrittenDependencies(set);
    StringBuilder sb = new StringBuilder();
    printDependencies(sb, dependencies, 0);
    String result = sb.toString();
    result = FileUtils.toSystemIndependentPath(result); // Account for Windows path separators
    assertThat(dependencies).hasSize(5);
    // myExcludes is a set, so it can be out of order, and it seems to consistently be the latter on Windows
    assertThat(result.contains(
      "Shadow Group\n" +
      "    exclude: /usr/local/google/home/jomof/projects/Game/cocos2d\n" +
      "    exclude: /path/to/ndk-bundle")
               || result.contains(
      "Shadow Group\n" +
      "    exclude: /path/to/ndk-bundle\n" +
      "    exclude: /usr/local/google/home/jomof/projects/Game/cocos2d")).isTrue();
    assertThat(result).contains(
      "Cocos Editor Support Modules (/usr/local/google/home/jomof/projects/Game/cocos2d)\n" +
      "    cocosbuilder (Cocos Editor Support Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/editor-support/cocosbuilder/)\n" +
      "    cocostudio (Cocos Editor Support Modules, cocos/editor-support/cocostudio)\n" +
      "        cocostudio (Cocos Editor Support Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/editor-support/cocostudio/)\n" +
      "        cocostudio (Cocos Editor Support Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/editor-support/cocostudio/WidgetReader/)\n" +
      "    spine (Cocos Editor Support Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/editor-support/spine/)\n");
    assertThat(result).contains(
      "Cocos Modules (/usr/local/google/home/jomof/projects/Game/cocos2d)\n" +
      "    2d (Cocos Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/2d/)\n" +
      "    3d (Cocos Modules, /usr/local/google/home/jomof/projects/Game/cocos2d, /cocos/3d/)\n" +
      "    audio (Cocos Modules, cocos/audio)");
  }

  private static void printDependencies(StringBuilder sb,
                                        List<? extends IncludeValue> dependencies,
                                        int indent) {

    for (IncludeValue dependency : dependencies) {
      sb.append(String.format("%s%s\n",
                        new String(new char[indent]).replace('\0', ' '),
                        dependency.toString()));
      if (dependency instanceof PackageFamilyValue) {
        PackageFamilyValue concrete = (PackageFamilyValue) dependency;
        printDependencies(sb, concrete.myIncludes, indent + 4);
        continue;
      }
      if (dependency instanceof PackageValue) {
        PackageValue concrete = (PackageValue) dependency;
        printDependencies(sb, concrete.getIncludes(), indent + 4);
        continue;
      }
      if (dependency instanceof SimpleIncludeValue) {
        continue;
      }
      if (dependency instanceof ShadowingIncludeValue) {
        ShadowingIncludeValue concrete = (ShadowingIncludeValue) dependency;
        sb.append(String.format("%sShadow Group\n",
                                new String(new char[indent]).replace('\0', ' ')));
        indent += 4;
        for (String exclude : concrete.myExcludes) {
          sb.append(String.format("%sexclude: %s\n", new String(new char[indent]).replace('\0', ' '), exclude));
        }
        printDependencies(sb, concrete.myIncludes, indent + 4);
        indent -= 4;
        continue;
      }
      throw new RuntimeException(dependency.getClass().toString());
    }
  }

  private static List<? extends IncludeValue> getRewrittenDependencies(IncludeSet set) {
    List<SimpleIncludeValue> simpleIncludes = new ArrayList<>();
    for (File include : set.getIncludesInOrder()) {
      IncludeResolver resolver = IncludeResolver.getGlobalResolver(new File(PATH_TO_NDK));
      SimpleIncludeValue resolved = resolver.resolve(include);
      assert resolved != null;
      simpleIncludes.add(resolved);
    }
    return IncludeValues.INSTANCE.organize(simpleIncludes);
  }
}
