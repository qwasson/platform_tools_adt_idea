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
package com.android.tools.idea.gradle.output.parser.aapt;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error4Parser extends AbstractAaptOutputParser {
  /**
   * First line of dual-line aapt error.
   * <pre>
   * ERROR parsing XML file &lt;path&gt;
   * &lt;error&gt; at line &lt;line&gt;
   * </pre>
   */
  private static final List<Pattern> MSG_PATTERNS =
    ImmutableList.of(Pattern.compile("^Error\\s+parsing\\s+XML\\s+file\\s(.+)$"), Pattern.compile("^(.+)\\s+at\\s+line\\s+(\\d+)$"));

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    Matcher m = MSG_PATTERNS.get(0).matcher(line);
    if (!m.matches()) {
      return false;
    }
    String sourcePath = m.group(1);

    m = getNextLineMatcher(reader, MSG_PATTERNS.get(1));
    if (m == null) {
      throw new ParsingFailedException();
    }
    String msgText = m.group(1);
    String lineNumber = m.group(2);

    GradleMessage msg = createMessage(GradleMessage.Kind.ERROR, msgText, sourcePath, lineNumber);
    messages.add(msg);
    return true;
  }
}
