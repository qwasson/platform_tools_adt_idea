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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnknownHostErrorHandler extends AbstractSyncErrorHandler {
  private static final Pattern UNKNOWN_HOST_PATTERN = Pattern.compile("Unknown host '(.*)'(.*)");

  @VisibleForTesting
  static final String GRADLE_PROXY_ACCESS_DOCS_URL =
    "http://www.gradle.org/docs/current/userguide/userguide_single.html#sec:accessing_the_web_via_a_proxy";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);

    Matcher matcher = UNKNOWN_HOST_PATTERN.matcher(firstLine);
    if (matcher.matches()) {
      List<NotificationHyperlink> hyperlinks = Lists.newArrayList();

      HttpConfigurable httpSettings = HttpConfigurable.getInstance();
      String host = matcher.group(1);
      // We offer to disable or edit proxy settings if
      // 1. proxy settings are "on"
      // 2. the proxy host in stored in settings is the same as the one from the error message
      if (httpSettings.USE_HTTP_PROXY && Objects.equal(host, httpSettings.PROXY_HOST)) {
        hyperlinks.add(new DisableIdeProxySettingsHyperlink());
        hyperlinks.add(new OpenHttpSettingsHyperlink());
      }

      NotificationHyperlink enableOfflineMode = ToggleOfflineModeHyperlink.enableOfflineMode(project);
      if (enableOfflineMode != null) {
        hyperlinks.add(enableOfflineMode);
      }
      hyperlinks.add(new OpenUrlHyperlink(GRADLE_PROXY_ACCESS_DOCS_URL, "Learn about configuring HTTP proxies in Gradle"));
      updateNotification(notification, project, error.getMessage(), hyperlinks);
      return true;
    }

    return false;
  }
}
