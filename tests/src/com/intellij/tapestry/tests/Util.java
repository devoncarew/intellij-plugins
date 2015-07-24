package com.intellij.tapestry.tests;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.tapestry.core.TapestryConstants;
import com.intellij.testFramework.builders.WebModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Alexey Chmutov
 *         Date: 03.04.2008
 */
public class Util {
  protected static final String DOT_TML = "." + TapestryConstants.TEMPLATE_FILE_EXTENSION;
  protected static final String DOT_JAVA = ".java";
  protected static final String DOT_GROOVY = ".groovy";
  protected static final String AFTER = "_after";
  public static final String DOT_EXPECTED = ".expected";

  private Util() {
  }

  static String getFileText(final String filePath) {
    try {
      return FileUtil.loadFile(new File(filePath));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected static String getCommonTestDataFileText(@NotNull String fileName) throws IOException {
    return getFileText(getCommonTestDataPath() + "/" + fileName);
  }

  public static IdeaProjectTestFixture getWebModuleFixture(String name) throws Exception {
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name);
    WebModuleFixtureBuilder webBuilder = fixtureBuilder.addModule(WebModuleFixtureBuilder.class);
    webBuilder.addContentRoot(new File("").getAbsoluteFile() + "/src/test/webModule");
    webBuilder.addSourceRoot("src");
    webBuilder.addJdk(System.getProperty("jdk.home"));
    webBuilder.addWebRoot(new File("").getAbsoluteFile() + "/src/test/webModule/resources", "/");
    webBuilder.addWebRoot(new File("").getAbsoluteFile() + "/src/test/webModule/WEB-INF", "/WEB-INF");

    IdeaProjectTestFixture webModuleFixture = fixtureBuilder.getFixture();
    webModuleFixture.setUp();
    return webModuleFixture;
  }

  static String getCommonTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + "/plugins/tapestry/tests/testData/";
  }
}
