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
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * Configures the IDE's proxy settings to "No Proxy."
 */
public class DisableIdeProxySettingsHyperlink extends NotificationHyperlink {
  public DisableIdeProxySettingsHyperlink() {
    super("disable.proxy.settings", "Disable the IDE's proxy settings and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    disableProxySettings(HttpConfigurable.getInstance());
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }

  @VisibleForTesting
  static void disableProxySettings(@NotNull HttpConfigurable proxySettings) {
    proxySettings.USE_HTTP_PROXY = false;
  }
}
