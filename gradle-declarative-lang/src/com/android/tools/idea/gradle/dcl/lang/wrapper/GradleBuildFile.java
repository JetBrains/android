// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dcl.lang.wrapper;

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeEntry;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePsiFactory;
import org.jetbrains.annotations.*;

public class GradleBuildFile {
  private static final Logger log = LoggerFactory.getLogger(GradleBuildFile.class);
  private DeclarativeFile dFile;
  private DeclarativeBlock javaBlock;

  public GradleBuildFile(String filePath, Project project) {
      try {
        Path path = Path.of(filePath);
        String fileContents = Files.readString(path)
          .replace("\r\n", "\n").replace("\r", "\n");
        dFile = new DeclarativePsiFactory(project).createFile(fileContents);
        javaBlock = findBlock("javaApplication", dFile.getEntries());
        if(javaBlock == null) {
          log.error("No javaPlatform block found in file: {}", filePath);
        }
      } catch (IOException e) {
        log.error("Error reading file: {}", e.getMessage());
        dFile = null;
      }
    }

  public int getJavaVersion() {
    DeclarativeAssignment version = findAssignment("javaVersion", javaBlock.getEntries());
    if(version == null) {
      log.error("No javaVersion assignment found in javaPlatform block");
      return -1;
    }
    if(version.getLiteral() == null) {
      log.error("No javaVersion literal found in javaPlatform block");
      return -1;
    }
    if(version.getLiteral().getIntegerLiteral() == null) {
      log.error("No javaVersion integer literal found in javaPlatform block");
      return -1;
    }
    return Integer.parseInt(version.getLiteral().getIntegerLiteral().getText());
  }

  public String getJavaMainClass() {
    DeclarativeAssignment mainClass = findAssignment("mainClass", javaBlock.getEntries());
    if(mainClass == null) {
      log.error("No mainClass assignment found in javaPlatform block");
      return null;
    }
    if(mainClass.getLiteral() == null) {
      log.error("No mainClass literal found in javaPlatform block");
      return null;
    }
    Object o = mainClass.getLiteral().getValue();
    if(o instanceof String) {
      return (String)o;
    } else {
      log.error("mainClass is not a string literal");
      return null;
    }
  }

  public List<String> getJavaDependencies() {
    DeclarativeBlock dependenciesBlock = findBlock("dependencies", javaBlock.getEntries());
    if(dependenciesBlock == null) {
      log.error("No dependencies block found in javaPlatform block");
      return null;
    }
    List<DeclarativeEntry> dependencies = dependenciesBlock.getEntries();
    return ContainerUtil.map(dependencies, d -> d.getText());
  }

  @Nullable
  private static DeclarativeAssignment findAssignment(String id, List<DeclarativeEntry> entries) {
    return (DeclarativeAssignment)ContainerUtil.find(entries, e -> e instanceof DeclarativeAssignment
                                                                   && ((DeclarativeAssignment)e).getIdentifier().textMatches(id));
  }

  @Nullable
  private static DeclarativeBlock findBlock(String id, List<DeclarativeEntry> entries) {
    return (DeclarativeBlock)ContainerUtil.find(entries, e -> e instanceof DeclarativeBlock
                                                              && ((DeclarativeBlock)e).getIdentifier() != null
                                                              && ((DeclarativeBlock)e).getIdentifier().textMatches(id));
  }
}
