// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android;

import com.intellij.openapi.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.android.util.ResourceFileData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.ValidityState;

public final class AndroidAptValidityState implements ValidityState {
  private static final int SIGNATURE = 0xDEADBEEF;
  private static final byte VERSION = 1;

  private final Map<String, ResourceFileData> myResources;
  private final Object2LongMap<String> myValueResourceFilesTimestamps;
  private final List<ResourceEntry> myManifestElements;
  private final String myPackageName;
  private final Set<Pair<String, String>> myLibRTxtFilesAndPackages;
  private final String myProguardOutputCfgFile;
  private final String myRTxtOutputDir;
  private final boolean myLibrary;

  public AndroidAptValidityState(@NotNull Map<String, ResourceFileData> resources,
                                 @NotNull Object2LongMap<String> valueResourceFilesTimestamps,
                                 @NotNull List<ResourceEntry> manifestElements,
                                 @NotNull Collection<Pair<String, String>> libRTxtFilesAndPackages,
                                 @NotNull String packageName,
                                 @Nullable String proguardOutputCfgFile,
                                 @Nullable String rTxtOutputDir,
                                 boolean library) {
    myResources = resources;
    myValueResourceFilesTimestamps = valueResourceFilesTimestamps;
    myManifestElements = manifestElements;
    myLibRTxtFilesAndPackages = new HashSet<>(libRTxtFilesAndPackages);
    myPackageName = packageName;
    myProguardOutputCfgFile = proguardOutputCfgFile != null ? proguardOutputCfgFile : "";
    myRTxtOutputDir = rTxtOutputDir != null ? rTxtOutputDir : "";
    myLibrary = library;
  }

  public AndroidAptValidityState(@NotNull DataInput in) throws IOException {
    final int signature = in.readInt();

    if (signature != SIGNATURE) {
      throw new IOException("incorrect signature");
    }
    final byte version = in.readByte();

    if (version != VERSION) {
      throw new IOException("old version");
    }
    myPackageName = in.readUTF();

    final int filesCount = in.readInt();
    myResources = new HashMap<>(filesCount);

    for (int i = 0; i < filesCount; i++) {
      final String filePath = in.readUTF();

      final int entriesCount = in.readInt();
      final List<ResourceEntry> entries = new ArrayList<>(entriesCount);

      for (int j = 0; j < entriesCount; j++) {
        final String resType = in.readUTF();
        final String resName = in.readUTF();
        final String resContext = in.readUTF();
        entries.add(new ResourceEntry(resType, resName, resContext));
      }
      final long timestamp = in.readLong();
      myResources.put(filePath, new ResourceFileData(entries, timestamp));
    }

    final int manifestElementCount = in.readInt();
    myManifestElements = new ArrayList<>(manifestElementCount);

    for (int i = 0; i < manifestElementCount; i++) {
      final String elementType = in.readUTF();
      final String elementName = in.readUTF();
      final String elementContext = in.readUTF();
      myManifestElements.add(new ResourceEntry(elementType, elementName, elementContext));
    }

    final int libPackageCount = in.readInt();
    myLibRTxtFilesAndPackages = new HashSet<>(libPackageCount);

    for (int i = 0; i < libPackageCount; i++) {
      final String libRTxtFilePath = in.readUTF();
      final String libPackage = in.readUTF();
      myLibRTxtFilesAndPackages.add(Pair.create(libRTxtFilePath, libPackage));
    }

    myProguardOutputCfgFile = in.readUTF();
    myRTxtOutputDir = in.readUTF();
    myLibrary = in.readBoolean();

    final int valueResourceFilesCount = in.readInt();
    myValueResourceFilesTimestamps = new Object2LongOpenHashMap<>(valueResourceFilesCount);

    for (int i = 0; i < valueResourceFilesCount; i++) {
      final String filePath = in.readUTF();
      final long timestamp = in.readLong();
      myValueResourceFilesTimestamps.put(filePath, timestamp);
    }
  }

  @Override
  public boolean equalsTo(ValidityState otherState) {
    if (!(otherState instanceof AndroidAptValidityState)) {
      return false;
    }
    // we do not compare myValueResourceFilesTimestamps maps here, because we don't run apt if some value resource xml files were changed,
    // but whole set of value resources was not. These maps are checked by AndroidSourceGeneratingBuilder for optimization only
    final AndroidAptValidityState otherAndroidState = (AndroidAptValidityState)otherState;
    return otherAndroidState.myPackageName.equals(myPackageName) &&
           otherAndroidState.myResources.equals(myResources) &&
           otherAndroidState.myManifestElements.equals(myManifestElements) &&
           otherAndroidState.myLibRTxtFilesAndPackages.equals(myLibRTxtFilesAndPackages) &&
           otherAndroidState.myProguardOutputCfgFile.equals(myProguardOutputCfgFile) &&
           otherAndroidState.myRTxtOutputDir.equals(myRTxtOutputDir) &&
           otherAndroidState.myLibrary == myLibrary;
  }

  @Override
  public void save(DataOutput out) throws IOException {
    out.writeInt(SIGNATURE);
    out.writeByte(VERSION);
    out.writeUTF(myPackageName);
    out.writeInt(myResources.size());

    for (Map.Entry<String, ResourceFileData> entry : myResources.entrySet()) {
      out.writeUTF(entry.getKey());

      final ResourceFileData fileData = entry.getValue();
      final List<ResourceEntry> resources = fileData.getValueResources();
      out.writeInt(resources.size());

      for (ResourceEntry resource : resources) {
        out.writeUTF(resource.getType());
        out.writeUTF(resource.getName());
        out.writeUTF(resource.getContext());
      }
      out.writeLong(fileData.getTimestamp());
    }
    out.writeInt(myManifestElements.size());

    for (ResourceEntry manifestElement : myManifestElements) {
      out.writeUTF(manifestElement.getType());
      out.writeUTF(manifestElement.getName());
      out.writeUTF(manifestElement.getContext());
    }
    out.writeInt(myLibRTxtFilesAndPackages.size());

    for (Pair<String, String> pair : myLibRTxtFilesAndPackages) {
      out.writeUTF(pair.getFirst());
      out.writeUTF(pair.getSecond());
    }
    out.writeUTF(myProguardOutputCfgFile);
    out.writeUTF(myRTxtOutputDir);
    out.writeBoolean(myLibrary);

    out.writeInt(myValueResourceFilesTimestamps.size());

    for (String key : myValueResourceFilesTimestamps.keySet()) {
      out.writeUTF(key);
      out.writeLong(myValueResourceFilesTimestamps.getLong(key));
    }
  }

  public Map<String, ResourceFileData> getResources() {
    return myResources;
  }

  public Object2LongMap<String> getValueResourceFilesTimestamps() {
    return myValueResourceFilesTimestamps;
  }
}
