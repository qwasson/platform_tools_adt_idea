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
package org.jetbrains.android.dom.transition;

import com.android.resources.ResourceType;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class TransitionDomUtil {
  public static final String TRANSITION_MANAGER_TAG = "transitionManager";
  public static final String TRANSITION_TAG = "transition";
  public static final String TRANSITION_SET_TAG = "transitionSet";
  public static final String FADE_TAG = "fade";
  public static final String TARGETS_TAG = "targets";
  public static final String TARGET_TAG = "target";
  public static final String CHANGE_BOUNDS_TAG = "changeBounds";
  public static final String AUTO_TRANSITION_TAG = "autoTransition";

  public static final String DEFAULT_ROOT = TRANSITION_MANAGER_TAG;
  private static final String[] ROOTS =
    new String[] {
      TRANSITION_MANAGER_TAG,
      TRANSITION_SET_TAG,
      FADE_TAG,
      CHANGE_BOUNDS_TAG,
      AUTO_TRANSITION_TAG,
      TARGETS_TAG
    };

  private TransitionDomUtil() {
  }

  public static List<String> getPossibleRoots() {
    return Arrays.asList(ROOTS);
  }

  public static boolean isTransitionResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.doIsMyFile(file, new String[]{ResourceType.TRANSITION.getName()});
  }
}
