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

import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.JBColor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class NavigationEditorUtils {
  public static final Dimension ZERO_SIZE = new Dimension(0, 0);
  static final Color TRANSITION_LINE_COLOR = new JBColor(new Color(80, 80, 255), new Color(40, 40, 255));

  public static Point sum(Point p1, Point p2) {
    return new Point(p1.x + p2.x, p1.y + p2.y);
  }

  public static Point diff(Point p1, Point p2) {
    return new Point(p1.x - p2.x, p1.y - p2.y);
  }

  public static double length(Point p) {
    return Math.sqrt(p.x * p.x + p.y * p.y);
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
    return scale(sum(p1, p2), 0.5f);
  }

  public static Point midPoint(Dimension size) {
    return point(scale(size, 0.5f));
  }

  public static Point point(Dimension d) {
    return new Point(d.width, d.height);
  }

  public static Point project(Point p, Rectangle r) {
    return new Point(ResourceHelper.clamp(p.x, x1(r), x2(r)), ResourceHelper.clamp(p.y, y1(r), y2(r)));
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
    Project project = module.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = module.getModuleWithLibrariesScope();
    return facade.findClass(className, scope);
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

  public static VirtualFile mkDirs(VirtualFile dir, String path) throws IOException {
    for(String dirName : path.split("/")) {
      VirtualFile existingDir = dir.findFileByRelativePath(dirName);
      //noinspection ConstantConditions
      dir = (existingDir != null) ? existingDir : dir.createChildDirectory(null, dirName);
    }
    return dir;
  }

  public static VirtualFile getNavigationFile(final VirtualFile baseDir, String moduleName, String deviceQualifier, final String fileName) {
    final String relativePathOfNavDir = NavigationEditor.NAVIGATION_DIRECTORY + "/" + moduleName + "/" + deviceQualifier;
    VirtualFile navFile = baseDir.findFileByRelativePath(relativePathOfNavDir + "/" + fileName);
    if (navFile == null) {
      navFile = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          try {
            VirtualFile dir = mkDirs(baseDir, relativePathOfNavDir);
            //noinspection ConstantConditions
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

  @Nullable
  public static VirtualFile findLayoutFile(List<VirtualFile> resourceDirectories, String navigationDirectoryName) {
    String qualifier = removePrefixIfPresent(NavigationEditor.DEFAULT_RESOURCE_FOLDER, navigationDirectoryName);
    String layoutDirName = NavigationEditor.LAYOUT_DIR_NAME + qualifier;
    for (VirtualFile root : resourceDirectories) {
      for (VirtualFile dir : root.getChildren()) {
        if (dir.isDirectory() && dir.getName().equals(layoutDirName)) {
          for (VirtualFile file : dir.getChildren()) {
            String fileName = file.getName();
            if (!fileName.startsWith(".") && fileName.endsWith(".xml")) { // Ignore files like .DS_store on mac
              return file;
            }
          }
        }
      }
    }
    return null;
  }

  private static String removePrefixIfPresent(String prefix, String s) {
    return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
  }

  @NotNull
  public static Module[] getAndroidModules(Project project) {
    if (project == null) {
      return new Module[0];
    }
    Module[] modules = ModuleManager.getInstance(project).getModules();
    List<Module> result = new ArrayList<Module>(modules.length);
    for (Module module : modules) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        List<VirtualFile> resourceDirectories = facet.getAllResourceDirectories();
        VirtualFile layoutFile = findLayoutFile(resourceDirectories, NavigationEditor.DEFAULT_RESOURCE_FOLDER);
        if (layoutFile != null) {
          result.add(module);
        }
      }
    }
    return result.toArray(new Module[result.size()]);
  }

  @Nullable
  public static Module findModule(@NotNull Module[] modules, @NotNull String name) {
    for (Module module : modules) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }

  public static Graphics2D createLineGraphics(Graphics g, int lineWidth) {
    Graphics2D g2D = (Graphics2D)g.create();
    g2D.setColor(TRANSITION_LINE_COLOR);
    g2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2D.setStroke(new BasicStroke(lineWidth));
    return g2D;
  }

  static Rectangle getCorner(Point a, int cornerDiameter) {
    int cornerRadius = cornerDiameter / 2;
    return new Rectangle(a.x - cornerRadius, a.y - cornerRadius, cornerDiameter, cornerDiameter);
  }

  static void drawLine(Graphics g, Point a, Point b) {
    g.drawLine(a.x, a.y, b.x, b.y);
  }

  static void drawArrow(Graphics g, Point a, Point b, int lineWidth) {
    drawArrow(g, a.x, a.y, b.x, b.y, lineWidth);
  }

  static void drawRectangle(Graphics g, Rectangle r) {
    g.drawRect(r.x, r.y, r.width, r.height);
  }

  private static int x1(Rectangle src) {
    return src.x;
  }

  private static int x2(Rectangle dst) {
    return dst.x + dst.width;
  }

  private static int y1(Rectangle src) {
    return src.y;
  }

  private static int y2(Rectangle dst) {
    return dst.y + dst.height;
  }

  private static boolean overlaps(int min1, int max1, int min2, int max2) {
      return !(max1 < min2 || max2 < min1);
  }

  static Line getMidLine(Rectangle src, Rectangle dst) {
    boolean xOverlap = overlaps(x1(src), x2(src), x1(dst), x2(dst));
    boolean yOverlap = overlaps(y1(src), y2(src), y1(dst), y2(dst));
    int dx = Math.min(Math.abs(x1(src) - x2(dst)), Math.abs(x1(dst) - x2(src)));
    int dy = Math.min(Math.abs(y1(src) - y2(dst)), Math.abs(y1(dst) - y2(src)));
    //noinspection SimplifiableConditionalExpression
    boolean horizontal = xOverlap ? yOverlap ? dx >= dy : false : yOverlap ? true : dx >= dy;

    int middle;
    if (horizontal) {
      middle = x1(src) - x2(dst) > 0 ? (x2(dst) + x1(src)) / 2 : (x2(src) + x1(dst)) / 2;
    }
    else {
      middle = y1(src) - y2(dst) > 0 ? (y2(dst) + y1(src)) / 2 : (y2(src) + y1(dst)) / 2;
    }

    Point midSrc = centre(src);
    Point a = horizontal ? new Point(middle, midSrc.y) : new Point(midSrc.x, middle);

    Point b = horizontal ? new Point(middle, ResourceHelper.clamp(midSrc.y, y1(dst), y2(dst)))
                         : new Point(ResourceHelper.clamp(midSrc.x, x1(dst), x2(dst)), middle);

    return new Line(a, b, horizontal);
  }

  static class Line {
    public final Point a;
    public final Point b;
    public final boolean horizontal;

    Line(Point a, Point b, boolean horizontal) {
      this.a = a;
      this.b = b;
      this.horizontal = horizontal;
    }
  }
}
