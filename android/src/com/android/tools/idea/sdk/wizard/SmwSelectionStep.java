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
package com.android.tools.idea.sdk.wizard;

import com.android.sdklib.internal.repository.IDescription;
import com.android.sdklib.internal.repository.IListDescription;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.wizard.TemplateWizardStep;
import com.intellij.icons.AllIcons;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;

/**
 * This page shows 2 things:
 * - The list of all SDK packages installed and whether an update is available.
 * - The list of all SDK packages available on remote server that the user could install.
 *
 * The UX workflow is that each package has a checkbox.
 * All checked items are either installed or to be installed. A user can:
 * - Uncheck a box to indicate a package needs to be removed.
 * - Check a box to indicate a package needs to be installed.
 *
 * The output of the wizard is thus a list of actions (cf {@link Action}).
 * - Install some new package
 * - Update some existing package
 * - Remove some existing package.
 *
 * Once the user hits Next, s/he ends up in the "Confirmation" page that displays
 * the actual list of packages to install or remove. That list also includes
 * transient dependencies needed to fulfill the user install choice, displays the
 * package license and it has its own accept/refuse state for each package.
 *
 * The current UX workflow for updates: since the base package is already installed,
 * the package is shown with a checked stated. By default it will be marked for
 * updates. If the user doesn't want to update, the current model is for the user
 * to do that in the *next* page (SmwConfirmationStep) by not de-selecting the update
 * (so essentially it's an opt-out.)
 * <p/>
 *
 * TODO: This wizard is a placeholder. It lacks several things:
 * - Each item's display could be made much better. Ideally I'd like something like
 *   the IJ settings > Settings > Plugin > Browse Repository where some of the
 *   package attributes (version, API) are right-aligned with a smaller font.
 * - We need a way to sort & filter the list using an ActionBar icon set at the top of the page:
 *    - Typical options would: display only what's installed, not installed, new & updates.
 *    - Have a boolean state to hide obsoleted packages by default.
 *    - Have a way to hide "older" Android releases by default. There should be an easy threshold
 *      set in constants somewhere so that we can easily adjust it (client side, not server side)
 *      and then hide APIs (say < 14) if they have nothing installed.
 *      Another option is to have 3 states (latest, common, all) where common would by default
 *      display APIs that match the android dashboard (e.g. 10 and >= 15).
 *      Also do something similar for build-tools so that we don't crowd that much the display.
 * - The layout is open to discussion.
 *   - One model is to have a small text area at the bottom under the list that displays information
 *     on the selected package: source URL, long-description from the XML if present, download side,
 *     SHA1, etc. (For an example, see what the current SDK Manager does in the tooltip on a package.)
 *   - Another model is to do like the IJ > Settings > Plugin table and have a display on the right
 *     side. I think it's only justified if we have more than a couple lines of info to display, which
 *     right now is not the case.
 * - The sdklib API used to retrieve package informtion does not currently provide the description
 *   meta-data from the remote XML. It needs to provide the description and the new list-display fields
 *   (this is pending another CL that adds the feature to sdklib.)
 */
public class SmwSelectionStep extends TemplateWizardStep implements Disposable {
  private final SmwState myWizardState;
  private JPanel myContentPanel;
  private JLabel myTextDescription;  // TODO: display details based on selection
  private JBLabel myLabelSdkPath;
  private JBTable myTable;
  private JPanel myToolbarPanel;
  private JLabel myErrorLabel;

  private SmwSelectionTableModel myTableModel;
  private boolean myInitOnce = true;

  public SmwSelectionStep(@NotNull SmwState wizardState, @Nullable UpdateListener updateListener) {
    super(wizardState, null /*project*/, null /*module*/, null /*sidePanelIcon*/, updateListener);
    myWizardState = wizardState;
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private void createUIComponents() {
    myTable = new SmwSelectionTable();
  }

  /**
   * Called every time step becomes visible (even when using Previous to navigate back to the step).
   * Fills in the SDK path, fills in the selection-step specific table/model and triggers an SDK update
   * the first time.
   */
  @Override
  public void _init() {
    if (!myInitOnce) {
      return;
    }
    myInitOnce = false;

    super._init();

    SdkState sdkState = myWizardState.getSdkState();
    if (sdkState != null) {
      AndroidSdkData sdkData = sdkState.getSdkData();
      //noinspection ConstantConditions
      myLabelSdkPath.setText(sdkData.getLocalSdk().getLocation().getPath());
    }

    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("SdkManager", getActionGroup(true), true);
    final JComponent component = actionToolbar.getComponent();
    myToolbarPanel.add(component, BorderLayout.WEST);

    SmwSelectionTableModel.LabelColumnInfo pkgColumn = new SmwSelectionTableModel.LabelColumnInfo("Package");
    SmwSelectionTableModel.InstallColumnInfo selColumn = new SmwSelectionTableModel.InstallColumnInfo("Installed");
    myTableModel = new SmwSelectionTableModel(pkgColumn, selColumn);
    Disposer.register(this, myTableModel);
    pkgColumn.setModel(myTableModel);
    selColumn.setModel(myTableModel);
    myTable.setModel(myTableModel);

    myTableModel.addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        // Invoked by the table model on the UI thread
        SmwSelectionStep.this.update();
      }
    });

    ListSelectionModel lsm = myTable.getSelectionModel();
    lsm.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        onTableSelection(e);
      }
    });

    onTableSelection(new ListSelectionEvent(lsm, lsm.getMinSelectionIndex(), lsm.getMaxSelectionIndex(), lsm.getValueIsAdjusting()));

    if (sdkState != null) {
      myTableModel.linkToSdkState(sdkState);

      Runnable onSuccess = new Runnable() {
        @Override
        public void run() {
          ListSelectionModel lsm = myTable.getSelectionModel();
          onTableSelection(new ListSelectionEvent(lsm, lsm.getMinSelectionIndex(), lsm.getMaxSelectionIndex(), lsm.getValueIsAdjusting()));
        }
      };

      sdkState.loadAsync(1000 * 60 * 10,  // 10 minutes timeout since last check
                         false,           // canBeCancelled
                         onSuccess,       // onSuccess
                         null);           // onError
    }
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    // We're not using the Description field of the Template Wizard Step here.
    // Since nullable isn't supported, share it with the error label (which is
    // fine since, again, template wizard description field isn't useful here.)
    return myErrorLabel;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myErrorLabel;
  }

  /**
   * Called when user presses "Next", "Previous" or "Finish" button.
   * {@inheritDoc}
   */
  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    myWizardState.setSelectedActions(myTableModel.getActions());
    super._commit(finishChosen);
  }

  @Override
  public boolean validate() {
    if (myTableModel != null && myTableModel.getActions().isEmpty()) {
      return false;
    }

    return super.validate();
  }

  private void onTableSelection(ListSelectionEvent e) {
    Object src = e.getSource();
    if (!(src instanceof ListSelectionModel) || e.getValueIsAdjusting()) {
      return;
    }

    ListSelectionModel lsm = (ListSelectionModel)src;

    StringBuilder sb = new StringBuilder("<html>");

    IListDescription item = lsm.isSelectionEmpty() ? null : myTableModel.getObjectAt(lsm.getMinSelectionIndex());
    if (item == null) {
      sb.append("Please select a package to see its details.");

    } else {

      if (item instanceof IDescription) {
        sb.append(((IDescription)item).getLongDescription());
      } else {
        sb.append(item.getListDescription());
      }

      if (item instanceof LocalPkgInfo) {
        LocalPkgInfo lpi = (LocalPkgInfo)item;
        if (lpi.hasUpdate()) {
          assert lpi.getUpdate() != null;
          sb.append("\n\n").append(lpi.getUpdate().getLongDescription());
        }
      }
    }

    sb.append("</html>");
    myTextDescription.setText(sb.toString().replace("\n", "<br/>\n"));
  }

  protected ActionGroup getActionGroup(boolean inToolbar) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new RefreshAction("Refresh", "Refresh list", AllIcons.Actions.Refresh));
    actionGroup.add(Separator.getInstance());
    actionGroup.add(new SdkStateNeededAction("Install", "Install item", AllIcons.Actions.Install));
    actionGroup.add(new SdkStateNeededAction("Uninstall", "Uninstall item", AllIcons.Actions.Uninstall));
    return actionGroup;
  }

  protected class SdkStateNeededAction extends DumbAwareAction {
    public SdkStateNeededAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      // no-op
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myWizardState.getSdkState() != null);
    }
  }

  protected class RefreshAction extends SdkStateNeededAction {
    public RefreshAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      SdkState sdkState = myWizardState.getSdkState();
      if (sdkState != null) {
        sdkState.loadAsync(0,               // no delay, check now
                           false,           // canBeCancelled
                           null,            // onSuccess
                           null);           // onError
      }
    }
  }


}
