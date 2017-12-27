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
package com.android.tools.idea.apk.paths;

import com.google.common.base.Joiner;
import com.intellij.util.SystemProperties;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PathTree}.
 */
public class PathTreeTest {
  private PathTree myTree;

  @Before
  public void setUp() throws Exception {
    myTree = new PathTree();
  }

  @Test
  public void addPath() {
    char separator = '/';
    myTree.addPath("/proc/self/cwd", separator);
    myTree.addPath("/tmp/6ea2a1122b122300e7292df852c9ff80/sysroot/usr/include", separator);
    myTree.addPath("/tmp/ndk-trybka/tmp/build-20450/build-libc++/ndk/sources/android/support/src/musl-locale", separator);
    myTree.addPath("/tmp/ndk-trybka/tmp/build-20450/build-libc++/ndk/sources/cxx-stl/llvm-libc++/libcxx/include", separator);
    myTree.addPath("/tmp/ndk-trybka/tmp/build-20450/build-libc++/ndk/sources/cxx-stl/llvm-libc++/libcxx/src", separator);
    myTree.addPath("/tmp/ndk-trybka/tmp/build-20450/build-libc++/ndk/sources/cxx-stl/llvm-libc++abi/libcxxabi/include", separator);
    myTree.addPath("/tmp/ndk-trybka/tmp/build-20450/build-libc++/ndk/sources/cxx-stl/llvm-libc++abi/libcxxabi/src", separator);
    myTree.addPath("/usr/local/google/buildbot/src/android/gcc/toolchain/gcc/gcc-4.9/include", separator);
    myTree.addPath("/usr/local/google/buildbot/src/android/gcc/toolchain/gcc/gcc-4.9/libgcc", separator);
    myTree.addPath("/usr/local/google/buildbot/tmp/build/toolchain/gcc-4.9/aarch64-linux-android/libgcc", separator);
    myTree.addPath("/usr/local/google/buildbot/tmp/build/toolchain/gcc-4.9/gcc/include", separator);
    myTree.addPath("/usr/local/google/home/trybka/gits/ndk/ndk/sources/android/support/include", separator);
    myTree.addPath("/usr/local/google/home/trybka/gits/ndk/prebuilts/clang/linux-x86/host/3.6/lib/clang/3.6/include", separator);
    myTree.addPath("/usr/local/google/home/trybka/gits/ndk/prebuilts/ndk/current/platforms/android-21/arch-arm64/usr/include", separator);

    List<String> expectedLines = new ArrayList<>();
    expectedLines.add("");
    expectedLines.add("  proc");
    expectedLines.add("    self");
    expectedLines.add("      cwd");
    expectedLines.add("  tmp");
    expectedLines.add("    6ea2a1122b122300e7292df852c9ff80");
    expectedLines.add("      sysroot");
    expectedLines.add("        usr");
    expectedLines.add("          include");
    expectedLines.add("    ndk-trybka");
    expectedLines.add("      tmp");
    expectedLines.add("        build-20450");
    expectedLines.add("          build-libc++");
    expectedLines.add("            ndk");
    expectedLines.add("              sources");
    expectedLines.add("                android");
    expectedLines.add("                  support");
    expectedLines.add("                    src");
    expectedLines.add("                      musl-locale");
    expectedLines.add("                cxx-stl");
    expectedLines.add("                  llvm-libc++");
    expectedLines.add("                    libcxx");
    expectedLines.add("                      include");
    expectedLines.add("                      src");
    expectedLines.add("                  llvm-libc++abi");
    expectedLines.add("                    libcxxabi");
    expectedLines.add("                      include");
    expectedLines.add("                      src");
    expectedLines.add("  usr");
    expectedLines.add("    local");
    expectedLines.add("      google");
    expectedLines.add("        buildbot");
    expectedLines.add("          src");
    expectedLines.add("            android");
    expectedLines.add("              gcc");
    expectedLines.add("                toolchain");
    expectedLines.add("                  gcc");
    expectedLines.add("                    gcc-4.9");
    expectedLines.add("                      include");
    expectedLines.add("                      libgcc");
    expectedLines.add("          tmp");
    expectedLines.add("            build");
    expectedLines.add("              toolchain");
    expectedLines.add("                gcc-4.9");
    expectedLines.add("                  aarch64-linux-android");
    expectedLines.add("                    libgcc");
    expectedLines.add("                  gcc");
    expectedLines.add("                    include");
    expectedLines.add("        home");
    expectedLines.add("          trybka");
    expectedLines.add("            gits");
    expectedLines.add("              ndk");
    expectedLines.add("                ndk");
    expectedLines.add("                  sources");
    expectedLines.add("                    android");
    expectedLines.add("                      support");
    expectedLines.add("                        include");
    expectedLines.add("                prebuilts");
    expectedLines.add("                  clang");
    expectedLines.add("                    linux-x86");
    expectedLines.add("                      host");
    expectedLines.add("                        3.6");
    expectedLines.add("                          lib");
    expectedLines.add("                            clang");
    expectedLines.add("                              3.6");
    expectedLines.add("                                include");
    expectedLines.add("                  ndk");
    expectedLines.add("                    current");
    expectedLines.add("                      platforms");
    expectedLines.add("                        android-21");
    expectedLines.add("                          arch-arm64");
    expectedLines.add("                            usr");
    expectedLines.add("                              include");
    expectedLines.add("");

    String expected = Joiner.on(SystemProperties.getLineSeparator()).join(expectedLines);
    assertEquals(expected, myTree.print());
  }
}