/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.coldFusion.mxunit;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CfmlStackTraceFilterProvider implements Filter {
  private Project myProject;

  public CfmlStackTraceFilterProvider(Project project) {
    myProject = project;
  }

  private static final TextAttributes HYPERLINK_ATTRIBUTES =
    EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES);

  // Disk name for Windows
  private static final String DISK_PATTERN = "\\p{Alpha}:";
  private static final String FILENAME_PATTERN = "[^:/\\\\]+";
  public static final String EXT_PATTERN = "\\.[\\p{Graph}^:/\\\\\\?]+";
  private static final String LINE_NUM_PATTERN = "(\\d+)";
  private static final String PATTERN = "(?:/|\\\\)[^:]*" + FILENAME_PATTERN + "(?:" + EXT_PATTERN + ")?";
  private static Pattern WIN_MXUNIT_PATTERN_ERROR =
    Pattern.compile("\\s*((" + DISK_PATTERN + PATTERN + ")[(](" + LINE_NUM_PATTERN + ")[)])[^\n]*\n");
  private static Pattern UNIX_MXUNIT_PATTERN_ERROR =
    Pattern.compile("\\s*((" + PATTERN + ")[(](" + LINE_NUM_PATTERN + ")[)])[^\n]*\n");

  public Result applyFilter(String line, int entireLength) {
    Pattern p = WIN_MXUNIT_PATTERN_ERROR;
    Matcher m = p.matcher(line);

    int numberOfReferenceGroup = 0;
    String fileName = "";
    String lineNumberString = "";

    if (!m.find()) {
      p = UNIX_MXUNIT_PATTERN_ERROR;
      m = p.matcher(line);
      if (!m.find()) {
        return null;
      }
    }
    numberOfReferenceGroup = 1;
    fileName = m.group(2);
    lineNumberString = m.group(3);

    if (fileName == null) {
      return null;
    }
    final File file = new File(fileName);

    int lineNumber = StringUtil.isEmpty(lineNumberString) ? 0 : Integer.parseInt(lineNumberString) - 1;
    if (lineNumber == 0) {
      return null;
    }
    final Ref<VirtualFile> vFile = new Ref<>();

    if (!file.isFile()) {
      return null;
    }

    try {
      GuiUtils.runOrInvokeAndWait(() -> vFile.set(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)));
    }
    catch (InvocationTargetException e) {
      // skip
    }
    catch (InterruptedException e) {
      // skip
    }

    if (vFile.isNull()) {
      return null;
    }

    final int textStartOffset = entireLength - line.length();
    final int highlightStartOffset = textStartOffset + m.start(numberOfReferenceGroup);
    final int highlightEndOffset = textStartOffset + m.end(numberOfReferenceGroup);
    final OpenFileHyperlinkInfo info = new OpenFileHyperlinkInfo(myProject, vFile.get(), lineNumber);

    TextAttributes attributes = HYPERLINK_ATTRIBUTES.clone();
    if (!ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(vFile.get())) {
      Color color = UIUtil.getInactiveTextColor();
      attributes.setForegroundColor(color);
      attributes.setEffectColor(color);
    }
    return new Result(highlightStartOffset, highlightEndOffset, info, attributes);
  }
}
