/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.ui.treeStructure.treetable.TreeTableTree;
import com.intellij.util.Alarm;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import java.text.SimpleDateFormat;

public class FlutterLogView extends JPanel implements ConsoleView, DataProvider, FlutterLog.Listener {

  private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

  private final SimpleToolWindowPanel toolWindowPanel;

  // TODO(pq): migrate to defining columninfo objects and then add rendering to them.
  // see: ListTreeTableModelOnColumns use PropertiesPanel setup
  // TODO(pq): render tooltip with complete event details.
  private enum LogTreeColumn {
    TIME(100, "time", String.class) {
      @Override
      Object getValue(Object node) {
        if (node instanceof FlutterEventNode) {
          return TIMESTAMP_FORMAT.format(((FlutterEventNode)node).entry.getTimestamp());
        }
        return super.getValue(node);
      }
    },
    CATEGORY(110, "category", String.class) {
      @Override
      Object getValue(Object node) {
        if (node instanceof FlutterEventNode) {
          return ((FlutterEventNode)node).entry.getCategory();
        }
        return super.getValue(node);
      }
    },
    MSG(100, "message", String.class) {
      @Override
      Object getValue(Object node) {
        if (node instanceof FlutterEventNode) {
          return ((FlutterEventNode)node).entry.getMessage();
        }
        return super.getValue(node);
      }

      @Override
      void setBounds(TableColumn column) {
        // Just min; can grow.
        column.setMinWidth(width);
      }
    };

    final int width;
    final String name;
    final Class cls;

    LogTreeColumn(int width, String label, Class cls) {
      this.width = width;
      this.name = label;
      this.cls = cls;
    }

    Object getValue(Object node) {
      return null;
    }

    void setBounds(TableColumn column) {
      column.setMinWidth(width);
      column.setMaxWidth(width);
    }
  }

  private class ClearLogAction extends AnAction {
    ClearLogAction() {
      super("Clear All", "Clear the log", AllIcons.Actions.GC);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      ApplicationManager.getApplication().invokeLater(() -> {
        model.getRoot().removeAllChildren();
        model.update();
      });
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(model.getRoot().getChildCount() > 0);
    }
  }

  private class ScrollToEndAction extends ToggleAction {
    ScrollToEndAction() {
      super("Scroll to the end", "Scroll to the end", AllIcons.RunConfigurations.Scroll_down);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return model.autoScrollToEnd;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ApplicationManager.getApplication().invokeLater(() -> {
        model.autoScrollToEnd = state;
        model.scrollToEnd();
      });
    }
  }

  @NotNull final FlutterApp app;
  final FlutterLogTreeTableModel model;
  private final FlutterLogTreeTable treeTable;
  private SimpleTreeBuilder builder;

  public FlutterLogView(@NotNull FlutterApp app) {
    this.app = app;

    final FlutterLog flutterLog = app.getFlutterLog();
    flutterLog.addListener(this, this);

    final DefaultActionGroup toolbarGroup = createToolbar();

    final Content content = ContentFactory.SERVICE.getInstance().createContent(null, null, false);
    content.setCloseable(false);

    toolWindowPanel = new SimpleToolWindowPanel(true, true);
    content.setComponent(toolWindowPanel);

    final ActionToolbar windowToolbar = ActionManager.getInstance().createActionToolbar("FlutterLogViewToolbar", toolbarGroup, true);
    toolWindowPanel.setToolbar(windowToolbar.getComponent());

    model = new FlutterLogTreeTableModel(flutterLog, this);
    treeTable = new FlutterLogTreeTable(model);

    // TODO(pq): add speed search
    //new TreeTableSpeedSearch(treeTable).setComparator(new SpeedSearchComparator(false));

    treeTable.setTableHeader(null);
    treeTable.setRootVisible(false);

    // TODO(pq): setup selection and auto-scroll
    treeTable.setExpandableItemsEnabled(true);
    treeTable.getTree().setScrollsOnExpand(true);

    // Set bounds.
    for (LogTreeColumn logTreeColumn : LogTreeColumn.values()) {
      logTreeColumn.setBounds(treeTable.getColumnModel().getColumn(logTreeColumn.ordinal()));
    }

    final JScrollPane pane = ScrollPaneFactory.createScrollPane(treeTable,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    model.setScrollPane(pane);
    toolWindowPanel.setContent(pane);
  }

  private DefaultActionGroup createToolbar() {
    //noinspection UnnecessaryLocalVariable
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    // TODO(pq): add toolbar items.
    return toolbarGroup;
  }

  @Override
  public void onEvent(@NotNull FlutterLogEntry entry) {
    model.onEvent(entry);
  }

  @Override
  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType) {

  }

  @Override
  public void clear() {

  }

  @Override
  public void scrollTo(int offset) {

  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    app.getFlutterLog().listenToProcess(processHandler, this);
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public void setOutputPaused(boolean value) {

  }

  @Override
  public boolean hasDeferredOutput() {
    return false;
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull Runnable runnable) {

  }

  @Override
  public void setHelpId(@NotNull String helpId) {

  }

  @Override
  public void addMessageFilter(@NotNull Filter filter) {

  }

  @Override
  public void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info) {

  }

  @Override
  public int getContentSize() {
    return 0;
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return new AnAction[]{
      new ScrollToEndAction(),
      new ClearLogAction()
    };
  }

  @Override
  public void allowHeavyFilters() {

  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    return null;
  }

  @Override
  public JComponent getComponent() {
    return toolWindowPanel;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return treeTable;
  }

  static class FlutterLogTreeTableModel extends DefaultTreeModel implements TreeTableModel {
    @NotNull
    private final Runnable updateRunnable;
    @NotNull
    private final FlutterLog log;
    @NotNull
    private final Alarm updateAlarm;

    private JScrollPane scrollPane;
    private TreeTable treeTable;
    private boolean autoScrollToEnd;

    public FlutterLogTreeTableModel(@NotNull FlutterLog log, @NotNull Disposable parent) {
      super(new LogRootTreeNode());
      this.log = log;
      // Scroll to end by default.
      autoScrollToEnd = true;

      updateRunnable = () -> {
        ((AbstractTableModel)treeTable.getModel()).fireTableDataChanged();
        reload(getRoot());
        treeTable.updateUI();

        if (autoScrollToEnd) {
          scrollToEnd();
        }
      };

      updateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, parent);
    }

    private void scrollToEnd() {
      if (scrollPane != null) {
        final JScrollBar vertical = scrollPane.getVerticalScrollBar();
        vertical.setValue(vertical.getMaximum());
      }
    }

    private void update() {
      if (!updateAlarm.isDisposed()) {
        updateAlarm.cancelAllRequests();
        updateAlarm.addRequest(updateRunnable, 0, ModalityState.stateForComponent(treeTable));
      }
    }

    @Override
    public LogRootTreeNode getRoot() {
      return (LogRootTreeNode)super.getRoot();
    }

    private LogTreeColumn getColumn(int index) {
      return LogTreeColumn.values()[index];
    }

    @Override
    public int getColumnCount() {
      return LogTreeColumn.values().length;
    }

    @Override
    public String getColumnName(int column) {
      return getColumn(column).name;
    }

    @Override
    public Class getColumnClass(int column) {
      return getColumn(column).cls;
    }

    @Override
    public Object getValueAt(Object node, int column) {
      return getColumn(column).getValue(node);
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
      return false;
    }

    @Override
    public void setValueAt(Object aValue, Object node, int column) {
    }

    public void setScrollPane(JScrollPane scrollPane) {
      this.scrollPane = scrollPane;
    }

    @Override
    public void setTree(JTree tree) {
      treeTable = ((TreeTableTree)tree).getTreeTable();
    }

    public void onEvent(FlutterLogEntry entry) {
      final MutableTreeNode root = getRoot();
      final FlutterEventNode node = new FlutterEventNode(entry);
      ApplicationManager.getApplication().invokeLater(() -> {
        insertNodeInto(node, root, root.getChildCount());
        update();
      });
    }
  }

  static class LogRootTreeNode extends DefaultMutableTreeNode {

  }

  static class FlutterEventNode extends DefaultMutableTreeNode {
    final FlutterLogEntry entry;

    FlutterEventNode(FlutterLogEntry entry) {
      this.entry = entry;
    }
  }

  class FlutterLogTreeTable extends TreeTable {

    public FlutterLogTreeTable(@NotNull FlutterLogTreeTableModel model) {
      super(model);
      model.setTree(this.getTree());
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
      // TODO(pq): add cell renderer
      return super.getCellRenderer(row, column);
    }
  }
}