/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.android.annotations.NonNull;
import com.android.tools.profgen.Apk;
import com.android.tools.profgen.ArtProfile;
import com.android.tools.profgen.ArtProfileKt;
import com.android.tools.profgen.DexDataKt;
import com.android.tools.profgen.ObfuscationMap;
import com.android.tools.profgen.ProfileDumperKt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;


public class BaselineProfilePrettyPrinter {
  @NonNull
  public static String prettyPrint(@NonNull VirtualFile apkFile, @NonNull Path p, @NonNull byte[] content) throws IOException {
    return prettyPrint(apkFile.contentsToByteArray(), p, content);
  }

  @NonNull
  public static String prettyPrint(@NonNull byte[] apkContent, @NonNull Path p, @NonNull byte[] content) throws IOException {
    ByteArrayInputStream stream = new ByteArrayInputStream(content);

    ArtProfile profile = ArtProfileKt.ArtProfile(stream);
    if (profile != null) {
      // TODO: Apply obfuscation map? They're not in the APK, but in the
      // dex file viewer we allow users to point to them; either do that
      // here too, or perhaps try to use the same one if already set
      // on the dex file viewer: DexFileViewer.getProguardMappings()
      ObfuscationMap obfuscationMap = ObfuscationMap.Companion.getEmpty();

      StringBuilder sb = new StringBuilder();

      try {
        Apk apk = DexDataKt.Apk(apkContent, p.getFileName().toString());
        ProfileDumperKt.dumpProfile(sb, profile, apk, obfuscationMap, false);
        return sb.toString();
      } catch (Throwable t) {
        throw new IOException("Error decoding baseline profile entry \"" + p + "\" from archive:\n" + t.getMessage());
      }
    } else {
      return "";
    }
  }
}
