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

import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.parse.StringFileAdapter;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.*;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.Reader;
import java.util.List;

import static com.android.SdkConstants.DOT_FTL;

/**
 * A recipe is a list of instructions which, when executed, modify a project.
 */
@XmlRootElement(name = "recipe")
public class Recipe implements RecipeInstruction {
  // @formatter:off
  @XmlElements({
    @XmlElement(name = "copy", type = CopyInstruction.class),
    @XmlElement(name = "instantiate", type = InstantiateInstruction.class),
    @XmlElement(name = "merge", type = MergeInstruction.class),
    @XmlElement(name = "append", type = AppendInstruction.class),
    @XmlElement(name = "mkdir", type = MkDirInstruction.class),
    @XmlElement(name = "dependency", type = DependencyInstruction.class),
    @XmlElement(name = "open", type = OpenInstruction.class),
    @XmlElement(name = "recipe", type = Recipe.class)
  })
  private List<RecipeInstruction> instructions = Lists.newArrayList();
  // @formatter:on

  /**
   * A "folder" attribute can be specified on a recipe tag to handle loading of relative files
   * from a recipe that is included inside another recipe file.
   * If not specified the value will be null.
   */
  @XmlAttribute(name="folder")
  @Nullable
  @SuppressWarnings("unused")
  private String myFolder;

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
    Unmarshaller unmarshaller = JAXBContext.newInstance(Recipe.class).createUnmarshaller();
    unmarshaller.setEventHandler(new ValidationEventHandler() {
      @Override
      public boolean handleEvent(ValidationEvent event) {
        throw new RuntimeException(event.getLinkedException());
      }
    });
    return (Recipe)unmarshaller.unmarshal(xmlReader);
  }

  @NotNull
  private static File cloneWithoutFreemarkerSuffix(@NotNull File file) {
    File clone = new File(file.getPath());
    return TemplateUtils.stripSuffix(clone, DOT_FTL);
  }

  /**
   * Execute this recipe's instructions.
   */
  @Override
  public void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
    if (myFolder == null) {
      executeInstructions(executor);
    }
    else {
      executor.pushFolder(myFolder);
      try {
        executeInstructions(executor);
      }
      finally {
        executor.popFolder();
      }
    }
  }

  private void executeInstructions(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
    for (RecipeInstruction instruction : instructions) {
      instruction.execute(executor);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class CopyInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    public void execute(@NotNull RecipeExecutor executor) {
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
  private static final class InstantiateInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    public void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
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
  private static final class AppendInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @NotNull
    private File to;

    @Override
    public void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
      executor.append(from, to);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class MergeInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File from;

    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute
    @Nullable
    private File to;

    @Override
    public void execute(@NotNull RecipeExecutor executor) throws TemplateProcessingException {
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
  private static final class MkDirInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File at;

    @Override
    public void execute(@NotNull RecipeExecutor executor) {
      executor.mkDir(at);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class OpenInstruction implements RecipeInstruction {
    @XmlJavaTypeAdapter(StringFileAdapter.class)
    @XmlAttribute(required = true)
    @NotNull
    private File file;

    @Override
    public void execute(@NotNull RecipeExecutor executor) {
      executor.addFilesToOpen(file);
    }
  }

  @SuppressWarnings({"NullableProblems", "unused"})
  private static final class DependencyInstruction implements RecipeInstruction {
    @XmlAttribute(required = true)
    @NotNull
    private String mavenUrl;

    @Override
    public void execute(@NotNull RecipeExecutor executor) {
      executor.addDependency(mavenUrl);
    }
  }

  /**
   * This should only be executed by the root recipe and only at the end.
   */
  private static final class UpdateAndSyncGradleInstruction implements RecipeInstruction {
    @Override
    public void execute(@NotNull RecipeExecutor executor) {
      executor.updateAndSyncGradle();
    }
  }
}
