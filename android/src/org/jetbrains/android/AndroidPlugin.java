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
package org.jetbrains.android;

import com.android.tools.idea.ddms.adb.AdbService;
import com.android.tools.idea.templates.TemplateManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {
  public static Key<Runnable> EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.build");
  public static Key<Runnable> EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.sync.task");
  public static Key<String[]> GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY = Key.create("gradle.sync.command.line.options");

  private static boolean ourGuiTestingMode;
  private static GuiTestSuiteState ourGuiTestSuiteState;

  @Override
  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    AdbService.terminateDdmlib();
  }

  public static boolean isGuiTestingMode() {
    return ourGuiTestingMode;
  }

  public static void setGuiTestingMode(boolean guiTestingMode) {
    ourGuiTestingMode = guiTestingMode;
    if (guiTestingMode) {
      ourGuiTestSuiteState = new GuiTestSuiteState();
    }
  }

  // Ideally we would have this class in IdeTestApplication. The problem is that IdeTestApplication and UI tests run in different
  // ClassLoaders and UI tests are unable to see the same instance of IdeTestApplication.
  @Nullable
  public static GuiTestSuiteState getGuiTestSuiteState() {
    return ourGuiTestSuiteState;
  }

  public static class GuiTestSuiteState {
    private boolean myOpenProjectWizardAlreadyTested;
    private boolean myImportProjectWizardAlreadyTested;

    public boolean isOpenProjectWizardAlreadyTested() {
      return myOpenProjectWizardAlreadyTested;
    }

    public void setOpenProjectWizardAlreadyTested(boolean openProjectWizardAlreadyTested) {
      myOpenProjectWizardAlreadyTested = openProjectWizardAlreadyTested;
    }

    public boolean isImportProjectWizardAlreadyTested() {
      return myImportProjectWizardAlreadyTested;
    }

    public void setImportProjectWizardAlreadyTested(boolean importProjectWizardAlreadyTested) {
      myImportProjectWizardAlreadyTested = importProjectWizardAlreadyTested;
    }
  }
}
