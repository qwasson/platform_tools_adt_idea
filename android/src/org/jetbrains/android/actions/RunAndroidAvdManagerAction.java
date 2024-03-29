/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.SdkConstants;
import com.android.tools.idea.avdmanager.AvdListDialog;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidAvdManagerAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.RunAndroidAvdManagerAction");

  public RunAndroidAvdManagerAction() {
    super(getName());
  }

  public RunAndroidAvdManagerAction(String name) {
    super(name);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    openAvdManager();
  }

  public static void openAvdManager() {
    AvdListDialog dialog = new AvdListDialog(null);
    dialog.init();
    dialog.show();
  }

  public static String getName() {
    return AndroidBundle.message("android.run.avd.manager.action.text");
  }
}
