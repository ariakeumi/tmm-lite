/*
 * Copyright 2012 - 2026 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinymediamanager.ui.components.table;

import javax.swing.ImageIcon;

import org.tinymediamanager.core.PostProcess;
import org.tinymediamanager.core.TmmResourceBundle;
import org.tinymediamanager.ui.IconManager;

import ca.odell.glazedlists.EventList;

/**
 * The class {@link PostProcessTable} is used to display / edit post-process entries
 *
 * @author Manuel Laggner
 */
public abstract class PostProcessTable extends TmmEditorTable {
  protected final EventList<PostProcess> postProcessList;

  /**
   * this constructor is used to edit the post-process entries
   *
   * @param postProcesses
   *          an eventlist containing the post-process entries
   */
  public PostProcessTable(EventList<PostProcess> postProcesses) {
    super();

    this.postProcessList = postProcesses;

    setModel(new TmmTableModel<>(postProcessList, new PostProcessTableFormat()));

    adjustColumnPreferredWidths(3);
  }

  /**
   * helper classes
   */
  private static class PostProcessTableFormat extends TmmTableFormat<PostProcess> {
    private PostProcessTableFormat() {
      /*
       * name
       */
      Column col = new Column(TmmResourceBundle.getString("Settings.processname"), "name", PostProcess::getName, String.class);
      addColumn(col);

      /*
       * path
       */
      col = new Column(TmmResourceBundle.getString("metatag.path"), "path", PostProcess::getPath, String.class);
      addColumn(col);

      /*
       * command
       */
      col = new Column(TmmResourceBundle.getString("Settings.commandname"), "command", PostProcess::getCommand, String.class);
      addColumn(col);

      /*
       * show result
       */
      col = new Column(TmmResourceBundle.getString("Settings.showoutput"), "showoutput", PostProcess::isShowOutput, Boolean.class);
      col.setColumnResizeable(false);
      col.setHeaderIcon(IconManager.NFO);
      addColumn(col);

      /*
       * abort on error
       */
      col = new Column(TmmResourceBundle.getString("Settings.abortonfailure"), "abortonfailure", PostProcess::isAbortOnFailure, Boolean.class);
      col.setColumnResizeable(false);
      col.setHeaderIcon(IconManager.STOP_HEADER);
      addColumn(col);

      /*
       * edit
       */
      col = new Column(TmmResourceBundle.getString("Button.edit"), "edit", postProcess -> IconManager.EDIT, ImageIcon.class);
      col.setColumnResizeable(false);
      col.setHeaderIcon(IconManager.EDIT_HEADER);
      addColumn(col);
    }
  }
}
