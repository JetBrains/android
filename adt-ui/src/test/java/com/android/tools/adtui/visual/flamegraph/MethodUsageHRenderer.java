/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui.visual.flamegraph;

import com.android.annotations.NonNull;
import com.android.tools.adtui.chart.hchart.HRenderer;
import com.android.tools.adtui.common.AdtUIUtils;
import com.intellij.ui.JBColor;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Pattern;

public class MethodUsageHRenderer implements HRenderer<MethodUsage> {

    Font mFont;

    private static final Color END_COLOR = new JBColor(new Color(0xFF9F00), new Color(0xFF9F00));
    private static final Color START_COLOR = new JBColor(new Color(0xF0CB35), new Color(0xF0CB35));
    private final int mRedDelta;
    private final int mGreenDelta;
    private final int mBlueDelta;


    // To limit the number of object allocation we reuse the same Rectangle.
    @NonNull
    private RoundRectangle2D.Float mRect;

    private static final Pattern dotPattern = Pattern.compile("\\.");

    public MethodUsageHRenderer() {
        mRect = new RoundRectangle2D.Float();
        mRect.archeight = 5;
        mRect.arcwidth = 5;
        mRedDelta = END_COLOR.getRed() - START_COLOR.getRed();
        mGreenDelta = END_COLOR.getGreen() - START_COLOR.getGreen();
        mBlueDelta = END_COLOR.getBlue() - START_COLOR.getBlue();
    }

    @Override
    public void setFont(Font font) {
        this.mFont = font;
    }

    @Override
    // This method is not thread-safe. In order to limit object allocation, mRect is being re-used.
    public void render(Graphics2D g, MethodUsage method, Rectangle2D drawingArea) {
        mRect.x = (float) drawingArea.getX();
        mRect.y = (float) drawingArea.getY();
        mRect.width = (float) drawingArea.getWidth();
        mRect.height = (float) drawingArea.getHeight();

        Color color = getColor(method);
        // Draw rectangle background
        g.setPaint(color);
        g.fill(mRect);

        // Draw text
        FontMetrics fontMetrics = g.getFontMetrics(mFont);
        String text = generateFittingText(method, drawingArea, fontMetrics);
        int textWidth = fontMetrics.stringWidth(text);
        long middle = (long) drawingArea.getCenterX();
        long textPositionX = middle - textWidth / 2;
        int textPositionY = (int) (drawingArea.getY() + fontMetrics.getAscent());

        Font prevFont = g.getFont();
        g.setFont(mFont);
        g.setPaint(AdtUIUtils.DEFAULT_FONT_COLOR);
        g.drawString(text, textPositionX, textPositionY);
        g.setFont(prevFont);
    }

    Color getColor(MethodUsage method) {
        return new Color(
                (int) (START_COLOR.getRed() + method.getPercentage() * mRedDelta),
                (int) (START_COLOR.getGreen() + method.getPercentage() * mGreenDelta),
                (int) (START_COLOR.getBlue() + method.getPercentage() * mBlueDelta));
    }

    /**
     * Find the best text for the given rectangle constraints.
     */
    private String generateFittingText(MethodUsage method, Rectangle2D rect,
            FontMetrics fontMetrics) {

        if (rect.getWidth() < fontMetrics.stringWidth("...")) {
            return "";
        }

        // Try: java.lang.String.toString
        String fullyQualified = method.getNameSpace() + "." + method.getName();
        if (fontMetrics.stringWidth(fullyQualified) < rect.getWidth()) {
            return fullyQualified;
        }

        // Try: j.l.s.toString
        String abbrevPackage = getShortPackageName(method.getNameSpace()) + "." + method.getName();
        if (fontMetrics.stringWidth(abbrevPackage) < rect.getWidth()) {
            return abbrevPackage;
        }

        // Try: toString
        if (fontMetrics.stringWidth(method.getName()) < rect.getWidth()) {
            return method.getName();
        }

        // TODO
        // Try to show as much as the method name as we can + "..."
        // Try toSr...

        return "";
    }

    private String getShortPackageName(String nameSpace) {
        StringBuilder b = new StringBuilder();
        String[] elements = dotPattern.split(nameSpace);
        String separator = "";
        for (int i = 0; i < elements.length; i++) {
            b.append(separator);
            b.append(elements[i].charAt(0));
            separator = ".";
        }
        return b.toString();
    }
}
