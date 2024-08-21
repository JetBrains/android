/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.external.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * An annotation processor that generates a class based on the annotated class that:
 *
 * <ul>
 *   <li>Has the same name as the annotated class with suffix {@code _Generated}.
 *   <li>Implements {@link com.example.external.Interface} where the generic argument is the
 *       annotated class.
 *   <li>Overrides the interface method.
 * </ul>
 */
@SupportedAnnotationTypes({"com.example.external.annotation.Annotation"})
public class Processor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element e : annotatedElements) {
        if (e.getKind() != ElementKind.CLASS) {
          processingEnv
              .getMessager()
              .printMessage(Kind.ERROR, "Annotated element is not a class: " + e);
          continue;
        }
        try {
          writeGeneratedSource((TypeElement) e);
        } catch (IOException ioe) {
          processingEnv
              .getMessager()
              .printMessage(Kind.ERROR, "Failed to write source file: " + ioe);
        }
      }
    }
    return false;
  }

  private void writeGeneratedSource(TypeElement forClass) throws IOException {
    String clsName = forClass.getSimpleName() + "_Generated";
    Element enclosing = forClass.getEnclosingElement();
    while (!(enclosing instanceof PackageElement)) {
      enclosing = enclosing.getEnclosingElement();
    }
    PackageElement pkg = (PackageElement) enclosing;
    JavaFileObject generatedSrc =
        processingEnv.getFiler().createSourceFile(pkg.getQualifiedName() + "." + clsName);
    try (PrintWriter out = new PrintWriter(generatedSrc.openWriter())) {
      out.format("package %s;\n", pkg.getQualifiedName());
      out.format("import com.example.external.Interface;\n");
      out.format("import %s;\n", forClass.getQualifiedName());
      out.format(
          "public final class %s implements Interface<%s> {\n", clsName, forClass.getSimpleName());
      out.format("  private final %s instance;\n", forClass.getSimpleName());
      out.format("  public %s(%s instance) {;\n", clsName, forClass.getSimpleName());
      out.format("    this.instance = instance;");
      out.format("  }\n");
      out.format("  public %s projectClass() { return null; }\n", forClass.getQualifiedName());
      out.format("}\n");
    }
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
