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
package com.android.tools.idea.templates.recipe;

import com.android.tools.idea.templates.FreemarkerUtils;
import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.parse.StringFileAdapter;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static com.android.SdkConstants.DOT_FTL;

/**
 * A recipe is a list of instructions which, when executed, modify a project.
 */
@XmlRootElement(name = "recipe")
public final class Recipe {
  // @formatter:off
  @XmlElements({
    @XmlElement(name = "copy", type = CopyInstruction.class),
    @XmlElement(name = "instantiate", type = InstantiateInstruction.class),
    @XmlElement(name = "merge", type = MergeInstruction.class),
    @XmlElement(name = "mkdir", type = MkDirInstruction.class),
    @XmlElement(name = "dependency", type = DependencyInstruction.class),
    @XmlElement(name = "open", type = OpenInstruction.class),
    @XmlElement(name = "execute", type = ExecuteInstruction.class)
  })
  private List<RecipeInstruction> instructions = Lists.newArrayList();
  // @formatter:on

  /**
   * Handles parsing a recipe.xml file. A recipe file specifies a bunch of file-related actions
   * to take after a template is processed, such as copying files over, merging them, or opening them
   * in the main editor. Once parsed, you should remember to {@link #execute(RecipeExecutor)} it.
   */
  public static Recipe parse(@NotNull Reader xmlReader) throws JAXBException {
    Recipe recipe = unmarshal(xmlReader);
    recipe.instructions.add(new UpdateAndSyncGradleInstruction());

    return recipe;
  }

  private static Recipe unmarshal(@NotNull Reader xmlReader) throws JAXBException {
    return (Recipe)JAXBContext.newInstance(Recipe.class).createUnmarshaller().unmarshal(xmlReader);
  }

  @NotNull
  private static File cloneWithoutFreemarkerSuffix(@NotNull File file) {
    File clone = new File(file.getPath());
    return TemplateUtils.stripSuffix(clone, DOT_FTL);
  }

  /**
   * Execute this recipe's instructions.
   */
  public void execute(@NotNull RecipeExecutor recipeExecutor) throws TemplateProcessingException {
    for (RecipeInstruction instruction : instructions) {
      instruction.execute(recipeExecutor);
    }
  }

  /**
   * A single instruction in a recipe. Each implementation corresponds to a tag in recipe.xml
   */
  private static abstract class RecipeInstruction {
    abstract void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException;
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class CopyInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull RecipeExecutor executor) {
      assert to != null; // Will be non-null after afterUnmarshal is called
      executor.copy(from, to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithoutFreemarkerSuffix(from);
      }
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class InstantiateInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
      assert to != null; // Will be non-null after afterUnmarshal is called
      executor.instantiate(from, to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithoutFreemarkerSuffix(from);
      }
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class MergeInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
      assert to != null; // Will be non-null after afterUnmarshal is called
      executor.merge(from, to);
    }

    private void afterUnmarshal(Unmarshaller u, Object parent) {
      if (to == null || to.getPath().isEmpty()) {
        to = cloneWithoutFreemarkerSuffix(from);
      }
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class MkDirInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File at;

    @Override
    void execute(@NotNull RecipeExecutor executor) {
      executor.mkDir(at);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class OpenInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File file;

    @Override
    void execute(@NotNull RecipeExecutor executor) {
      executor.addFilesToOpen(file);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class DependencyInstruction extends RecipeInstruction {
    @XmlAttribute(required = true)
    @NotNull
    private String mavenUrl;

    @Override
    void execute(@NotNull RecipeExecutor executor) {
      executor.addDependency(mavenUrl);
    }
  }

  /**
   * Execute another recipe file from within the current recipe file
   */
  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class ExecuteInstruction extends RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class) @XmlAttribute(required = true) @NotNull private File file;

    @Override
    void execute(@NotNull final RecipeExecutor executor) throws TemplateProcessingException {
      executor.processTemplate(file, new FreemarkerUtils.TemplatePostProcessor() {
        @Override
        public void process(@NotNull String content) throws TemplateProcessingException {
          try {
            Recipe child = unmarshal(new StringReader(XmlUtils.stripBom(content)));
            child.execute(executor);
          }
          catch (JAXBException e) {
            throw new TemplateProcessingException(e);
          }
        }
      });
    }
  }

  /**
   * This should only be executed by the root recipe and only at the end.
   */
  private static final class UpdateAndSyncGradleInstruction extends RecipeInstruction {
    @Override
    void execute(@NotNull RecipeExecutor executor) {
      executor.updateAndSyncGradle();
    }
  }
}
