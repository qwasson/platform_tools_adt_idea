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
package com.android.tools.idea.editors;

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;

import static com.intellij.openapi.externalSystem.service.notification.NotificationSource.PROJECT_SYNC;

/**
 * Notifies users that a Gradle project "sync" is either being in progress or failed.
 */
public class ProjectSyncStatusNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.gradle.sync.status");

  @NotNull private final Project myProject;

  public ProjectSyncStatusNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!Projects.isGradleProject(myProject)) {
      return null;
    }
    GradleSyncState syncState = GradleSyncState.getInstance(myProject);
    if (syncState.isSyncInProgress()) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("Gradle project sync in progress...");
      return panel;
    }
    if (Projects.lastGradleSyncFailed(myProject)) {
      return new ProjectImportFailedNotificationPanel();
    }
    ThreeState gradleSyncNeeded = syncState.isSyncNeeded();
    if (gradleSyncNeeded == ThreeState.YES) {
      return new StaleGradleModelNotificationPanel();
    }
    return null;
  }

  private class StaleGradleModelNotificationPanel extends EditorNotificationPanel {
    StaleGradleModelNotificationPanel() {
      setText("Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.");

      createActionLabel("Sync Now", new Runnable() {
        @Override
        public void run() {
          GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
        }
      });
    }
  }

  private class ProjectImportFailedNotificationPanel extends EditorNotificationPanel {
    ProjectImportFailedNotificationPanel() {
      setText("Gradle project sync failed. Basic functionality (e.g. editing, debugging) will not work properly.");

      createActionLabel("Try Again", new Runnable() {
        @Override
        public void run() {
          GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
        }
      });

      createActionLabel("Open 'Messages' View", new Runnable() {
        @Override
        public void run() {
          ExternalSystemNotificationManager.getInstance(myProject).openMessageView(GradleConstants.SYSTEM_ID, PROJECT_SYNC);
        }
      });

      createActionLabel("Show Log in " + ShowFilePathAction.getFileManagerName(), new Runnable() {
        @Override
        public void run() {
          File logFile = new File(PathManager.getLogPath(), "idea.log");
          ShowFilePathAction.openFile(logFile);
        }
      });
    }
  }

}
