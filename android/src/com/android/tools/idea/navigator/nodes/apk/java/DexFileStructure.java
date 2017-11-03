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
package com.android.tools.idea.navigator.nodes.apk.java;

import com.android.tools.idea.apk.debugging.ApkPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.android.tools.idea.apk.dex.DexFiles.getDexFile;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static com.intellij.debugger.impl.DebuggerUtilsEx.signatureToName;

class DexFileStructure {
  @NotNull private final Future<DexBackedDexFile> myDexFileFuture;

  @NotNull private final ApkPackages myPackages = new ApkPackages();
  private boolean myPackagesComputed;

  DexFileStructure(@NotNull VirtualFile dexFile) {
    ListeningExecutorService executor = listeningDecorator(PooledThreadExecutor.INSTANCE);
    myDexFileFuture = executor.submit(() -> getDexFile(dexFile));
  }

  @NotNull
  Collection<ApkPackage> getPackages() throws ExecutionException, InterruptedException {
    if (!myPackagesComputed) {
      myPackagesComputed = true;
      computePackages();
    }
    return myPackages.values();
  }

  private void computePackages() throws ExecutionException, InterruptedException {
    DexBackedDexFile dexFile = myDexFileFuture.get();
    // Definitions only returns the classes in the app's source code (no JDK or Android platform classes,) but the returned names are not
    // fully qualified names (FQNs.) We need to get the classes FQNs from the method references.
    // For example:
    // For the FQN 'a.b.c.X' the class definition will be 'La/b/c/X;'
    Set<String> definitions = dexFile.getClasses().stream().map(DexBackedClassDef::getType).collect(Collectors.toSet());
    for (int i = 0, m = dexFile.getMethodCount(); i < m; i++) {
      MethodReference methodRef = new DexBackedMethodReference(dexFile, i);
      String className = signatureToName(methodRef.getDefiningClass());
      String definition = "L" + className.replace('.', '/') + ";"; // This is how definitions are set in DexBackedDexFile
      if (definitions.contains(definition)) {
        myPackages.add(className);
      }
    }
  }

  @VisibleForTesting
  static class ApkPackages {
    @NotNull private final Map<String, ApkPackage> myPackagesByName = new HashMap<>();

    void add(@NotNull String classFqn) {
      Pair<String, String> packageAndClassNames = splitName(classFqn);

      String packageName = packageAndClassNames.getFirst();
      List<String> segments = Splitter.on('.').omitEmptyStrings().splitToList(packageName);
      ApkPackage apkPackage;
      int segmentCount = segments.size();
      if (segmentCount == 0) {
        // default package.
        apkPackage = myPackagesByName.computeIfAbsent(packageName, s -> new ApkPackage("", null));
      }
      else {
        String first = segments.get(0);
        ApkPackage existing = myPackagesByName.get(first);
        if (existing == null) {
          // The package has not been added yet.
          ApkPackage newPackage = new ApkPackage(first, null);
          myPackagesByName.put(first, newPackage);
          if (segmentCount > 1) {
            apkPackage = addChildren(newPackage, segments, 1); // start from the second segment. The first one has been already added.
          }
          else {
            apkPackage = newPackage;
          }
        }
        else {
          // The first package segment was found, now find the subpackage that matches 'packageName'.
          apkPackage = findOrCreateMatchingSubpackage(existing, segments);
        }
      }
      apkPackage.addClass(packageAndClassNames.getSecond());
    }

    // Pair: package name, class name.
    @NotNull
    private static Pair<String, String> splitName(@NotNull String classFqn) {
      int lastDotIndex = classFqn.lastIndexOf('.');
      if (lastDotIndex < 0) {
        // default package.
        return Pair.create("", classFqn);
      }
      return Pair.create(classFqn.substring(0, lastDotIndex), classFqn.substring(lastDotIndex + 1));
    }

    @NotNull
    private static ApkPackage findOrCreateMatchingSubpackage(@NotNull ApkPackage apkPackage, @NotNull List<String> segments) {
      ApkPackage current = apkPackage;
      int segmentCount = segments.size();
      // start from the second segment. The first one has been already added.
      for (int i = 1; i < segmentCount; i++) {
        String segment = segments.get(i);
        ApkPackage child = current.findSubpackage(segment);
        if (child != null) {
          // Keep going.
          current = child;
          continue;
        }
        return addChildren(current, segments, i);
      }
      return current;
    }

    @NotNull
    private static ApkPackage addChildren(@NotNull ApkPackage apkPackage, @NotNull List<String> segments, int index) {
      ApkPackage current = apkPackage;
      int segmentCount = segments.size();
      for (int i = index; i < segmentCount; i++) {
        String segment = segments.get(i);
        current = current.addSubpackage(segment);
      }
      return current;
    }

    @NotNull
    Collection<ApkPackage> values() {
      return myPackagesByName.values();
    }

    @Nullable
    ApkPackage findPackage(@NotNull String name) {
      return myPackagesByName.get(name);
    }
  }
}
