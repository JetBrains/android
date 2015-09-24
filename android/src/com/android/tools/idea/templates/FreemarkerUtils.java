/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Various utility methods to help classes interact with and initialize freemarker with Android
 * specific extensions.
 */
public final class FreemarkerUtils {

  /**
   * Create a parameter map that represents a data model which should be passed into
   * {@link freemarker.template.Template#process(Object, Writer)}. This model will be initialized
   * with various utility methods and data useful for instantiating Android projects and modules.
   *
   * @param args Additional variables that should be added to this data model, if any.
   */
  @NotNull
  public static Map<String, Object> createParameterMap(@NotNull Map<String, Object> args) {
    // Create the data model.
    final Map<String, Object> paramMap = new HashMap<String, Object>();

    // Builtin conversion methods
    paramMap.put("slashedPackageName", new FmSlashedPackageNameMethod());
    paramMap.put("camelCaseToUnderscore", new FmCamelCaseToUnderscoreMethod());
    paramMap.put("underscoreToCamelCase", new FmUnderscoreToCamelCaseMethod());
    paramMap.put("activityToLayout", new FmActivityToLayoutMethod());
    paramMap.put("layoutToActivity", new FmLayoutToActivityMethod());
    paramMap.put("classToResource", new FmClassNameToResourceMethod());
    paramMap.put("escapeXmlAttribute", new FmEscapeXmlAttributeMethod());
    paramMap.put("escapeXmlText", new FmEscapeXmlStringMethod());
    paramMap.put("escapeXmlString", new FmEscapeXmlStringMethod());
    paramMap.put("escapePropertyValue", new FmEscapePropertyValueMethod());
    paramMap.put("extractLetters", new FmExtractLettersMethod());
    paramMap.put("hasDependency", new FmHasDependencyMethod(paramMap));
    paramMap.put("truncate", new FmTruncateStringMethod());
    paramMap.put("compareVersions", new FmCompareVersionsMethod());
    paramMap.put("getApplicationTheme", new FmGetApplicationThemeMethod(paramMap));
    paramMap.put("getAppManifestDir", new FmGetAppManifestDirMethod(paramMap));

    // Dependency list
    paramMap.put(TemplateMetadata.ATTR_DEPENDENCIES_LIST, new LinkedList<String>());

    // Parameters supplied by user
    paramMap.putAll(args);

    return paramMap;
  }

  /**
   * Helper method which processes a target file, running it through the Freemarker engine first,
   * returning its contents as a string.
   * @param processor allows the caller to do some work that may refer to files relative to the
   *                  file being processed. When the method exits relative files will be relative
   *                  to the enclosing file.
   */
  @NotNull
  public static String processFreemarkerTemplate(@NotNull Configuration freemarker,
                                                 @NotNull Map<String, Object> paramMap,
                                                 @NotNull File file,
                                                 @Nullable TemplatePostProcessor processor) throws TemplateProcessingException {
    StudioTemplateLoader loader = (StudioTemplateLoader)freemarker.getTemplateLoader();
    File previousFolder = loader.getTemplateFolder();
    try {
      file = loader.getSourceFile(file);
      loader.setTemplateFolder(file.getParentFile());
      freemarker.template.Template template = freemarker.getTemplate(file.getName());
      StringWriter out = new StringWriter();
      template.process(paramMap, out);
      out.flush();
      String content = out.toString().replace("\r", "");
      if (processor != null) {
        processor.process(content);
      }
      return content;
    }
    catch (TemplateException ex) {
      throw new TemplateProcessingException(ex);
    }
    catch (IOException ex) {
      throw new TemplateProcessingException(ex);
    }
    finally {
      loader.setTemplateFolder(previousFolder);
    }
  }

  /**
   * Callback for processing the content of a template file.
   * Use this if the content may contain file paths relative to the template file.
   */
  public interface TemplatePostProcessor {
    void process(@NotNull String content) throws TemplateProcessingException;
  }

  public static class TemplateProcessingException extends Exception {
    public TemplateProcessingException(Exception inner) {
      super(inner);
    }
  }
}
