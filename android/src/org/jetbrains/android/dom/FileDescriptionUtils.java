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
package org.jetbrains.android.dom;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceFolderType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public final class FileDescriptionUtils {
  private FileDescriptionUtils() {
  }

  /**
   * Returns if a file is of the given folderType and the root tag is any of the passed tag names. If rootTags is empty, the method will
   * return true if the file is of the given {@link ResourceFolderType}.
   */
  public static boolean isResourceOfTypeWithRootTag(@NotNull XmlFile file,
                                                    @NotNull ResourceFolderType folderType,
                                                    @NotNull Collection<String> rootTags) {
    return ApplicationManager.getApplication().runReadAction(newResourceTypeVerifier(file, folderType, rootTags));
  }

  /**
   * Returns the passed tag and all the children as a stream.
   */
  @NotNull
  private static Stream<XmlTag> asStream(@NotNull XmlTag rootTag) {
    return Stream.concat(Stream.of(rootTag),
                         Arrays.stream(rootTag.getSubTags())
                           .filter(XmlTag.class::isInstance)
                           .map(XmlTag.class::cast)
                           .flatMap(FileDescriptionUtils::asStream));
  }

  /**
   * Same as {@link #isResourceOfTypeWithRootTag(XmlFile, ResourceFolderType, Collection)} but checks all the XML nodes and not only the root element
   * to see if any of the given tags are contained in the file.
   */
  public static boolean isResourceOfTypeContainingTag(@NotNull XmlFile file,
                                                      @NotNull ResourceFolderType folderType,
                                                      @NotNull Collection<String> tagNames) {
    Condition<XmlTag> tagCondition = tagNames.isEmpty() ?
                                      Condition.TRUE :
                                      rootTag -> asStream(rootTag)
                                        .anyMatch(tag -> tagNames.contains(tag.getName()));

    return ApplicationManager.getApplication().runReadAction(newResourceTypeVerifier(file, folderType, tagCondition));
  }

  /**
   * Returns a {@link Computable} that returns whether the given file is from the given folderType and tagVerifier {@link Condition} returns
   * true.
   * The tagVerifier can be used to check if certain tags are contained in the file. It will be called with the root {@link XmlTag}.
   */
  @NotNull
  private static Computable<Boolean> newResourceTypeVerifier(@NotNull XmlFile file,
                                                             @NotNull ResourceFolderType folderType,
                                                             @NotNull Condition<XmlTag> tagVerifier) {
    return () -> {
      if (file.getProject().isDisposed()) {
        return false;
      }

      if (!AndroidResourceUtil.isInResourceSubdirectory(file, folderType.getName())) {
        return false;
      }

      XmlTag rootTag = file.getRootTag();
      assert rootTag != null;

      return tagVerifier.value(rootTag);
    };
  }

  /**
   * Returns a {@link Computable} that returns whether the given file is from the given folderType and the root tag name is one of the
   * given rootTags collection.
   */
  @NotNull
  @VisibleForTesting
  static Computable<Boolean> newResourceTypeVerifier(@NotNull XmlFile file,
                                                     @NotNull ResourceFolderType folderType,
                                                     @NotNull Collection<String> rootTags) {
    //noinspection unchecked
    return rootTags.isEmpty() ?
           newResourceTypeVerifier(file, folderType, Condition.TRUE) :
           newResourceTypeVerifier(file, folderType, element -> rootTags.contains(element.getName()));
  }
}
