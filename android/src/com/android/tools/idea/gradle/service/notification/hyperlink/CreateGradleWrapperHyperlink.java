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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.io.IOException;

public class CreateGradleWrapperHyperlink extends NotificationHyperlink {
  @NotNull private final String myGradleVersion;

  public CreateGradleWrapperHyperlink(@NotNull String gradleVersion) {
    super("createGradleWrapper", "Migrate to Gradle wrapper and sync project");
    myGradleVersion = gradleVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    File projectDirPath = new File(project.getBasePath());
    try {
      GradleUtil.createGradleWrapper(projectDirPath, myGradleVersion);
      GradleProjectSettings settings = GradleUtil.getGradleProjectSettings(project);
      if (settings != null) {
        settings.setDistributionType(DistributionType.DEFAULT_WRAPPED);
      }
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
    catch (IOException e) {
      // Unlikely to happen.
      Messages.showErrorDialog(project, "Failed to create Gradle wrapper: " + e.getMessage(), "Quick Fix");
    }
  }
}
