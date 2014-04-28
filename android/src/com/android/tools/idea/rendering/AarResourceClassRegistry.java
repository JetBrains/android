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
package com.android.tools.idea.rendering;

import com.android.io.FileWrapper;
import com.android.xml.AndroidManifest;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_AAR;
import static org.jetbrains.android.facet.ResourceFolderManager.EXPLODED_AAR;

/**
 * A registry for class lookup of resource classes (R classes) in AAR libraries.
 */
public class AarResourceClassRegistry {
  /** TODO: Turn this into an ApplicationComponent */
  private static AarResourceClassRegistry ourInstance = new AarResourceClassRegistry();

  private final Map<String,AarResourceClassGenerator> myGeneratorMap = Maps.newHashMap();

  public static AarResourceClassRegistry get() {
    return ourInstance;
  }

  public void addLibrary(AppResourceRepository appResources, File aarDir) {
    String path = aarDir.getPath();
    if (path.endsWith(DOT_AAR) || path.contains(EXPLODED_AAR)) {
      FileResourceRepository repository = appResources.findRepositoryFor(aarDir);
      if (repository != null) {
        String pkg = getAarPackage(aarDir);
        if (pkg != null) {
          AarResourceClassGenerator generator = AarResourceClassGenerator.create(appResources, repository);
          if (generator != null) {
            myGeneratorMap.put(pkg, generator);
          }
        }
      }
    }
  }

  @Nullable
  private static String getAarPackage(@NotNull File aarDir) {
    File manifest = new File(aarDir, ANDROID_MANIFEST_XML);
    if (manifest.exists()) {
      try {
        // TODO: Come up with something more efficient! A pull parser can do this quickly
        return AndroidManifest.getPackage(new FileWrapper(manifest));
      }
      catch (Exception e) {
        // No go
        return null;
      }
    }

    return null;
  }

  /** Looks up a class definition for the given name, if possible */
  @Nullable
  public byte[] findClassDefinition(@NotNull String name) {
    int index = name.lastIndexOf('.');
    if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
      String pkg = name.substring(0, index);
      AarResourceClassGenerator generator = myGeneratorMap.get(pkg);
      if (generator != null) {
        return generator.generate(name);
      }
    }

    return null;
  }
}