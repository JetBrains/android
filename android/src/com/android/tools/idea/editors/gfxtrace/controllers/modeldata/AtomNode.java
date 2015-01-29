/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.idea.editors.gfxtrace.renderers.styles.RoundedLineBorder;
import com.android.tools.rpclib.rpc.EnumEntry;
import com.android.tools.rpclib.rpc.EnumInfo;
import com.android.tools.rpclib.rpc.ParameterInfo;
import com.android.tools.rpclib.schema.Atom;
import com.android.tools.rpclib.schema.AtomReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AtomNode {
  private static final Color DEFAULT_PARAM_COLOR = new JBColor(new Color(240, 40, 240), new Color(240, 40, 240));
  private static final Color DROPDOWN_PARAM_COLOR = new JBColor(new Color(0, 240, 0), new Color(0, 240, 0));
  @NotNull private static final Logger LOG = Logger.getInstance(AtomNode.class);
  private static Color foregroundColor = UIUtil.getTableForeground();
  private static String foregroundColorString = toHtmlString(foregroundColor);
  private static Color foregroundSelectedColor = UIUtil.getTableSelectionForeground();
  private static String foregroundSelectedColorString = toHtmlString(foregroundSelectedColor);
  private static Color highlightForegroundColor = UIUtil.getHeaderActiveColor();
  private static String highlightForegroundColorString = toHtmlString(highlightForegroundColor);
  private static Font treeFont = UIUtil.getTreeFont();
  private long myId;

  public AtomNode(long id) {
    this.myId = id;
  }

  @NotNull
  private static String toHtmlString(@NotNull Color color) {
    return String.format("#%x", color.getRGB() & 0xffffff);
  }

  public long getRepresentativeAtomId() {
    return myId;
  }

  @Nullable
  public Component getComponent(@NotNull EnumInfoCache enumInfoCache,
                                @NotNull AtomReader atomReader,
                                @NotNull final JTree tree,
                                @NotNull final TreeNode node,
                                boolean isSelected) {
    Atom atom;
    try {
      atom = atomReader.read(this.myId);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }

    JPanel baseContainer = new JPanel();
    FlowLayout flowLayout = (FlowLayout)baseContainer.getLayout();
    flowLayout.setHgap(1);
    flowLayout.setVgap(1);
    flowLayout.setAlignment(FlowLayout.LEFT);

    JBLabel commandName = new JBLabel(String.format("<html><font color='%s'>%s</font> <font color='%s'>%s</font></html>",
                                                    highlightForegroundColorString, this.myId,
                                                    isSelected ? foregroundSelectedColorString : foregroundColorString,
                                                    atom.info.getName() + "("));
    commandName.setFont(treeFont);
    baseContainer.add(commandName);

    for (int i = 0; i < atom.info.getParameters().length; ++i) {
      if (i != 0) {
        JLabel commaLabel = new JLabel(", ");
        commaLabel.setForeground(isSelected ? foregroundSelectedColor : foregroundColor);
        baseContainer.add(commaLabel);
      }

      ParameterInfo parameterInfo = atom.info.getParameters()[i];
      Object parameterValue = atom.parameters[i].value;
      JBLabel argLabel;

      switch (parameterInfo.getType().getKind()) {
        case Enum:
          argLabel = populateEnumLabel(parameterValue, enumInfoCache.getInfo(parameterInfo.getType().getName()), tree, node, isSelected);
          break;

        default:
          argLabel = new JBLabel(atom.parameters[i].value.toString());
          argLabel.setForeground(DEFAULT_PARAM_COLOR);
          break;
      }

      argLabel.setFont(treeFont);
      baseContainer.add(argLabel);
    }

    JBLabel endParen = new JBLabel(")");
    endParen.setFont(treeFont);
    endParen.setForeground(isSelected ? foregroundSelectedColor : foregroundColor);
    baseContainer.add(endParen);

    return baseContainer;
  }

  protected JBLabel populateEnumLabel(@NotNull Object value,
                                      @NotNull EnumInfo info,
                                      @NotNull final JTree tree,
                                      @NotNull final TreeNode node,
                                      boolean isSelected) {
    final JBLabel label = new JBLabel(value.toString());
    label.setForeground(DROPDOWN_PARAM_COLOR);

    if (info.getEntries().length > 1) {
      label.setBorder(new RoundedLineBorder(UIUtil.getBoundsColor(isSelected), 3, true));

      List<String> popupStrings = new ArrayList<String>(info.getEntries().length);
      for (EnumEntry entry : info.getEntries()) {
        popupStrings.add(entry.getName());
      }
      final JBList popupList = new JBList(popupStrings);

      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent mouseEvent) {
          super.mousePressed(mouseEvent);
          if (mouseEvent.getClickCount() != 1) {
            return;
          }
          JBPopupFactory.getInstance().createListPopupBuilder(popupList).setItemChoosenCallback(new Runnable() {
            @Override
            public void run() {
              label.setText((String)popupList.getSelectedValue());
            }
          }).setCancelOnClickOutside(true).createPopup().showUnderneathOf(label);
        }
      });

      label.addPropertyChangeListener("text", new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
          ((DefaultTreeModel)tree.getModel()).nodeChanged(node);
        }
      });
    }

    return label;
  }
}
