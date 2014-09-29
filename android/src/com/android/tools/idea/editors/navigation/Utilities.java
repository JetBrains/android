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
package com.android.tools.idea.editors.navigation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;

public class Utilities {
  public static final Dimension ZERO_SIZE = new Dimension(0, 0);

  public static Point add(Point p1, Point p2) {
    return new Point(p1.x + p2.x, p1.y + p2.y);
  }

  public static Point diff(Point p1, Point p2) {
    return new Point(p1.x - p2.x, p1.y - p2.y);
  }

  public static double length(Point p) {
    return Math.sqrt(p.x * p.x + p.y * p.y);
  }

  public static Point max(Point p1, Point p2) {
    return new Point(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
  }

  public static Point scale(Point p, float k) {
    return new Point((int)(k * p.x), (int)(k * p.y));
  }

  public static Dimension scale(Dimension d, float k) {
    return new Dimension((int)(k * d.width), (int)(k * d.height));
  }

  private static int snap(int i, int d) {
    return ((int)Math.round((double)i / d)) * d;
  }

  public static Point snap(Point p, Dimension gridSize) {
    return new Point(snap(p.x, gridSize.width), snap(p.y, gridSize.height));
  }

  public static Point midPoint(Point p1, Point p2) {
    return scale(add(p1, p2), 0.5f);
  }

  public static Point midPoint(Dimension size) {
    return point(scale(size, 0.5f));
  }

  public static Point point(Dimension d) {
    return new Point(d.width, d.height);
  }

  public static Dimension dimension(Point p) {
    return new Dimension(Math.abs(p.x), Math.abs(p.y));
  }

  public static Point project(Point p, Rectangle r) {
    Point centre = centre(r);
    Point diff = diff(p, centre);
    boolean horizontal = Math.abs((float)diff.y / diff.x) < Math.abs((float)r.height / r.width);
    float scale = horizontal ? (float)r.width / 2 / diff.x : (float)r.height / 2 / diff.y;
    return add(centre, scale(diff, Math.abs(scale)));
  }

  public static Point centre(@NotNull Rectangle r) {
    return new Point(r.x + r.width / 2, r.y + r.height / 2);
  }

  /**
   * Translates a Java file name to a XML file name according
   * to Android naming convention.
   * <p/>
   * Doesn't append .xml extension
   *
   * @return XML file name associated with Java file name
   */
  public static String getXmlFileNameFromJavaFileName(String javaFileName) {

    if (javaFileName.endsWith(".java")) {
      // cut off ".java"
      javaFileName = javaFileName.substring(0, javaFileName.length() - 5);
    }

    char[] charsJava = javaFileName.toCharArray();
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < charsJava.length; i++) {
      char currentChar = charsJava[i];
      if (Character.isUpperCase(currentChar) && i != 0) {
        stringBuilder.append('_');
      }
      stringBuilder.append(Character.toLowerCase(currentChar));
    }
    return stringBuilder.toString();
  }

  /**
   * Translates a XML file name to a Java file name according
   * to Android naming convention.
   * <p/>
   * Doesn't append .java extension
   *
   * @return Java file name associated with XML file name
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  public static String getJavaFileNameFromXmlFileName(String xmlFileName) {

    if (xmlFileName.endsWith(".xml")) {
      // cut off ".xm"
      xmlFileName = xmlFileName.substring(0, xmlFileName.length() - 4);
    }

    char[] charsXml = xmlFileName.toCharArray();
    StringBuilder stringBuilder = new StringBuilder();
    // make the first char upper case
    stringBuilder.append(Character.toUpperCase(charsXml[0]));
    // start looking for '_' at the second char
    for (int i = 1; i < charsXml.length; i++) {
      char currentChar = charsXml[i];
      if (currentChar == '_') {
        // skip '_' and add the next char as upper case
        char toAppend = Character.toUpperCase(charsXml[++i]);
        stringBuilder.append(toAppend);
      }
      else {
        stringBuilder.append(currentChar);
      }
    }
    return stringBuilder.toString();
  }

  static void drawArrow(Graphics g1, int x1, int y1, int x2, int y2, int lineWidth) {
    // x1 and y1 are coordinates of circle or rectangle
    // x2 and y2 are coordinates of circle or rectangle, to this point is directed the arrow
    Graphics2D g = (Graphics2D)g1.create();
    double dx = x2 - x1;
    double dy = y2 - y1;
    double angle = Math.atan2(dy, dx);
    int len = (int)Math.sqrt(dx * dx + dy * dy);
    AffineTransform t = AffineTransform.getTranslateInstance(x1, y1);
    t.concatenate(AffineTransform.getRotateInstance(angle));
    g.transform(t);
    g.drawLine(0, 0, len, 0);
    Dimension arrowHeadSize = new Dimension(lineWidth * 6, lineWidth * 3);
    int basePosition = len - arrowHeadSize.width;
    int height = arrowHeadSize.height;
    g.fillPolygon(new int[]{len, basePosition, basePosition, len}, new int[]{0, -height, height, 0}, 4);
  }

  static <T> Condition<T> not(final Condition<T> condition) {
    return new Condition<T>() {
      @Override
      public boolean value(T t) {
        return !condition.value(t);
      }
    };
  }

  static <T> Condition<T> instanceOf(final Class<?> type) {
    return new Condition<T>() {
      @Override
      public boolean value(Object o) {
        return type.isAssignableFrom(o.getClass());
      }
    };
  }

  static int sign(int x) {
    return x > 0 ? 1 : x < 0 ? -1 : 0;
  }

  static Dimension notNull(@Nullable Dimension d) {
    return d == null ? ZERO_SIZE : d;
  }

  @Nullable
  public static PsiClass getPsiClass(Module module, String className) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    GlobalSearchScope scope = module.getModuleWithDependenciesAndLibrariesScope(false);
    return facade.findClass(className, scope);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(Module module, String className, String signature) {
    PsiClass psiClass = getPsiClass(module, className);
    return psiClass == null ? null : findMethodBySignature(psiClass, signature);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(@NotNull PsiClass psiClass, @NotNull String signature) {
    PsiMethod template = createMethodFromText(psiClass, signature);
    return psiClass.findMethodBySignature(template, false);
  }

  public static PsiMethod createMethodFromText(PsiClass psiClass, String text) {
    return createMethodFromText(psiClass.getProject(), text, psiClass);
  }

  public static PsiMethod createMethodFromText(Project project, String text, @Nullable PsiElement context) {
    return JavaPsiFacade.getInstance(project).getElementFactory().createMethodFromText(text, context);
  }

  public static PsiMethod createMethodFromText(Project project, String text) {
    return createMethodFromText(project, text, null);
  }

  @Nullable
  public static VirtualFile virtualFile(File file) {
    return LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  public static VirtualFile mkDirs(VirtualFile dir, String path) throws IOException {
    for(String dirName : path.split("/")) {
      VirtualFile existingDir = dir.findFileByRelativePath(dirName);
      dir = (existingDir != null) ? existingDir : dir.createChildDirectory(null, dirName);
    }
    return dir;
  }

  public static VirtualFile getNavigationFile(final VirtualFile baseDir, String deviceQualifier, final String fileName) {
    final String relativePathOfNavDir = ".navigation" + "/" + deviceQualifier;
    VirtualFile navFile = baseDir.findFileByRelativePath(relativePathOfNavDir + "/" + fileName);
    if (navFile == null) {
      navFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          try {
            VirtualFile dir = mkDirs(baseDir, relativePathOfNavDir);
            return dir.createChildData(null, fileName);
          }
          catch (IOException e) {
            assert false;
            return null;
          }

        }
      });
    }
    return navFile;
  }
}
