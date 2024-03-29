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
package com.android.navigation;

import com.android.annotations.NonNull;

public abstract class State {
  public abstract static class Visitor<T> {
    public abstract T visit(ActivityState state);
    public abstract T visit(MenuState state);
  }

  public abstract <T> T accept(Visitor<T> visitor);

  public abstract String getClassName();

  public abstract String getXmlResourceName();

  @Deprecated
  @NonNull
  public Point getLocation() {
    return Point.ORIGIN;
  }
  @Deprecated
  public void setLocation(@NonNull Point location) {
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
