package com.intellij.lang.javascript.linter.tslint.execution;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.intellij.lang.javascript.linter.tslint.config.TsLintConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.SemVer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TsLintOutputJsonParser {

  private static final Logger LOG = Logger.getInstance(TsLintConfiguration.LOG_CATEGORY);

  private final String myPath;
  private final boolean myMyZeroBasedRowCol;

  @NotNull
  private final List<TsLinterError> myErrors;

  public TsLintOutputJsonParser(String path, final JsonElement root, boolean zeroBasedRowCol) {
    myPath = path;
    myMyZeroBasedRowCol = zeroBasedRowCol;

    if (root instanceof JsonNull || !root.isJsonArray()) {
      logError("root element is not array");
      myErrors = ContainerUtil.emptyList();
    }
    else {
      final JsonArray array = root.getAsJsonArray();
      final int size = array.size();
      ArrayList<TsLinterError> errors = ContainerUtil.newArrayList();
      for (int i = 0; i < size; i++) {
        final JsonElement element = array.get(i);
        if (!element.isJsonObject()) {
          logError("element under root is not object");
        }
        else {
          final JsonObject object = element.getAsJsonObject();
          errors.addAll(processError(object));
        }
      }
      myErrors = errors;
    }
  }

  private List<TsLinterError> processError(JsonObject object) {
    List<TsLinterError> result = ContainerUtil.newArrayList();
    final JsonElement name = object.get("name");
    if (name == null) {
      logError("no name for error object");
      return result;
    }
    final JsonElement failure = object.get("failure");
    if (failure == null || !(failure.isJsonPrimitive() && failure.getAsJsonPrimitive().isString())) {
      logError("no failure for error object");
      return result;
    }
    final JsonElement startPosition = object.get("startPosition");
    if (startPosition == null || !startPosition.isJsonObject()) {
      logError("no startPosition for error object");
      return result;
    }
    final JsonElement endPosition = object.get("endPosition");
    if (endPosition == null || !endPosition.isJsonObject()) {
      logError("no endPosition for error object");
      return result;
    }
    final JsonElement ruleName = object.get("ruleName");
    if (ruleName == null || !(ruleName.isJsonPrimitive() && ruleName.getAsJsonPrimitive().isString())) {
      logError("no rule name for error object");
      return result;
    }
    final Pair<Integer, Integer> start = parseLineColumn(startPosition.getAsJsonObject());
    final Pair<Integer, Integer> end = parseLineColumn(endPosition.getAsJsonObject());
    if (start == null || end == null) return result;

    result.add(new TsLinterError(myPath,
                                 start.getFirst(),
                                 start.getSecond(),
                                 failure.getAsString(),
                                 ruleName.getAsString(),
                                 end.getFirst(), end.getSecond()));

    return result;
  }

  private Pair<Integer, Integer> parseLineColumn(JsonObject position) {
    final JsonElement line = position.get("line");
    if (line == null || !(line.isJsonPrimitive() && line.getAsJsonPrimitive().isNumber())) {
      logError("no line for position");
      return null;
    }
    final JsonElement character = position.get("character");
    if (character == null || !(character.isJsonPrimitive() && character.getAsJsonPrimitive().isNumber())) {
      logError("no character for position");
      return null;
    }
    if (myMyZeroBasedRowCol) return Pair.create(line.getAsJsonPrimitive().getAsInt(), character.getAsJsonPrimitive().getAsInt());
    return Pair.create(line.getAsJsonPrimitive().getAsInt() + 1, character.getAsJsonPrimitive().getAsInt() + 1);
  }

  @NotNull
  public List<TsLinterError> getErrors() {
    return myErrors;
  }

  private static void logError(String s) {
    LOG.error("TSLint result parsing: " + s);
  }

  public static boolean isVersionZeroBased(SemVer tsLintVersion) {
    return tsLintVersion != null && (tsLintVersion.getMajor() < 2 ||
                                     tsLintVersion.getMajor() == 2 && tsLintVersion.getMinor() <= 1);
  }
}
