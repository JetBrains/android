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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.SourcelessResourceItem;
import com.android.ide.common.resources.sampledata.SampleDataHolder;
import com.android.ide.common.resources.sampledata.SampleDataJsonParser;
import com.android.resources.ResourceType;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import org.w3c.dom.Node;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.android.SdkConstants.*;

public class SampleDataResourceItem extends SourcelessResourceItem {
  private static final Cache<String, SampleDataHolder> sSampleDataCache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(2, TimeUnit.MINUTES)
      .softValues()
      .weigher((String key, SampleDataHolder value) -> value.getFileSizeMb()) // length returns unicode codepoints so not exactly in MB
      .maximumWeight(50) // MB
      .build();

  private final Function<OutputStream, Exception> myDataSource;
  private final PsiElement mySourceElement;
  private final Supplier<Long> myDataSourceModificationStamp;

  /**
   * Creates a new {@link SampleDataResourceItem}
   * @param name name of the resource
   * @param dataSource {@link Function} that writes the content to be used for this item to the passed {@link OutputStream}. The function
   *                                   must return any exceptions that happened during the processing of the file.
   * @param dataSourceModificationStamp {@link Supplier} that returns a modification stamp. This stamp should change every time the
   *                                                    content changes. If 0, the content won't be cached.
   * @param sourceElement optional {@link PsiElement} where the content was obtained from. This will be used to display references to the
   *                      content.
   */
  private SampleDataResourceItem(@NonNull String name,
                                 @Nullable String namespace,
                                 @NonNull Function<OutputStream, Exception> dataSource,
                                 @NonNull Supplier<Long> dataSourceModificationStamp,
                                 PsiElement sourceElement) {
    super(name, namespace, ResourceType.SAMPLE_DATA, null, null);

    myDataSource = dataSource;
    myDataSourceModificationStamp = dataSourceModificationStamp;
    // We use SourcelessResourceItem as parent because we don't really obtain a FolderConfiguration or Qualifiers from
    // the source element (since it's not within the resources directory).
    mySourceElement = sourceElement;
  }

  /**
   * Creates a new {@link SampleDataResourceItem}
   * @param name name of the resource
   * @param dataSource {@link Function} that writes the content to be used for this item to the passed {@link OutputStream}. The function
   *                                   must return any exceptions that happened during the processing of the file.
   * @param dataSourceModificationStamp {@link Supplier} that returns a modification stamp. This stamp should change every time the
   *                                                    content changes. If 0, the content won't be cached.
   * @param sourceElement optional {@link PsiElement} where the content was obtained from. This will be used to display references to the
   *                      content.
   */
  private SampleDataResourceItem(@NonNull String name,
                                 @NonNull Function<OutputStream, Exception> dataSource,
                                 @NonNull Supplier<Long> dataSourceModificationStamp,
                                 PsiElement sourceElement) {
    this(name, null, dataSource, dataSourceModificationStamp, sourceElement);
  }

  /**
   * Returns a {@link SampleDataResourceItem} from the given content generator
   */
  @NonNull
  public static SampleDataResourceItem getFromStaticDataSource(@NonNull String name, Function<OutputStream, Exception> source) {
    // Extract namespace
    List<String> sampleDataResource = Splitter.on(':')
      .trimResults()
      .omitEmptyStrings()
      .limit(2)
      .splitToList(name);
    return new SampleDataResourceItem(name, sampleDataResource.size() == 2 ? sampleDataResource.get(0) : null, source, () -> 1L, null);
  }

  @NonNull
  private static SampleDataResourceItem getFromPlainFile(@NonNull PsiFile sourceElement)
    throws IOException {
    return new SampleDataResourceItem(sourceElement.getName(), output -> {
      try {
        output.write(sourceElement.getText().getBytes(Charsets.UTF_8));
      }
      catch (IOException e) {
        return e;
      }
      return null;
    }, () -> sourceElement.getModificationStamp() + 1, sourceElement);
  }

  @NonNull
  private static SampleDataResourceItem getFromDirectory(@NonNull PsiDirectory directory) {
    return new SampleDataResourceItem(directory.getName(), output -> {
      PrintStream printStream = new PrintStream(output);
      Arrays.stream(directory.getFiles())
        .sorted(Comparator.comparing(PsiFileSystemItem::getName))
        .forEach(file -> printStream.println(file.getVirtualFile().getPath()));
      return null;
    }, () -> directory.getVirtualFile().getModificationStamp() + 1, directory);
  }

  @NonNull
  private static SampleDataResourceItem getFromJsonFile(@NonNull PsiFile sourceElement, @NonNull String contentPath)
    throws IOException {
    return new SampleDataResourceItem(sourceElement.getName() + contentPath, output -> {
      if (contentPath.isEmpty()) {
        return null;
      }

      try {
        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(sourceElement.getText().getBytes(Charsets.UTF_8)));
        SampleDataJsonParser parser = SampleDataJsonParser.parse(input);
        if (parser != null) {
          output.write(parser.getContentFromPath(contentPath));
        }
      }
      catch (IOException e) {
        return e;
      }
      return null;
    }, () -> sourceElement.getModificationStamp() + 1, sourceElement);
  }

  @NonNull
  public static List<SampleDataResourceItem> getFromPsiFileSystemItem(@NonNull PsiFileSystemItem sampleDataSource) throws IOException {
    if (!EXT_JSON.equals(sampleDataSource.getVirtualFile().getExtension())) {
      return ImmutableList.of(sampleDataSource instanceof PsiDirectory ?
                              getFromDirectory((PsiDirectory)sampleDataSource) : getFromPlainFile(
        (PsiFile)sampleDataSource));
    }

    SampleDataJsonParser parser = SampleDataJsonParser.parse(new FileReader(VfsUtilCore.virtualToIoFile(sampleDataSource.getVirtualFile())));
    if (parser == null) {
      // Failed to parse the JSON file
      return Collections.emptyList();
    }

    Set<String> possiblePaths = parser.getPossiblePaths();
    ImmutableList.Builder<SampleDataResourceItem> items = ImmutableList.builder();
    for (String path : possiblePaths) {
      items.add(getFromJsonFile((PsiFile)sampleDataSource, path));
    }
    return items.build();
  }

  @Nullable
  @Override
  public Node getValue() {
    throw new UnsupportedOperationException("SampleDataResourceItem does not support getValue");
  }


  @Nullable
  @Override
  public String getValueText() {
    SampleDataHolder value = sSampleDataCache.getIfPresent(getName());
    if (value == null
        || value.getLastModification() == 0
        || value.getLastModification() != myDataSourceModificationStamp.get()) {
      long lastModificationStamp = myDataSourceModificationStamp.get();
      try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        Exception e = myDataSource.apply(output);

        if (e == null) {
          byte[] content = output.toByteArray();
          value = new SampleDataHolder(getName(), lastModificationStamp, content.length / 1_000_000, content);
          sSampleDataCache.put(getName(), value);
        }
      } catch (Exception e) {
        return null;
      }
    }

    return value != null ? new String(value.getContents(), Charsets.UTF_8) : null;
  }

  @NonNull
  @Override
  public String getQualifiers() {
    return "";
  }

  @Nullable
  @Override
  public ResourceValue getResourceValue(boolean isFrameworks) {
    return new ResourceValue(getResourceUrl(isFrameworks), getValueText());
  }

  @Nullable
  public PsiElement getPsiElement() {
    return mySourceElement;
  }
}
