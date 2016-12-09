package com.jetbrains.lang.dart.ide.errorTreeView;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.sdk.DartSdk;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.ide.actions.SendFeedbackAction.getDescription;

public class DefaultDartFeedbackBuilder extends DartFeedbackBuilder {

  public String prompt() {
    return "Create an issue on GitHub?";
  }

  public void sendFeedback(@Nullable Project project, @Nullable String errorMessage) {
    final ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    boolean eap = appInfo.isEAP();
    String ijBuild = eap ? appInfo.getBuild().asStringWithoutProductCode() : appInfo.getBuild().asString();
    String sdkVersion = getSdkVersion(project);
    String platformDescription = getDescription().replaceAll(";", " ").trim();

    if (errorMessage != null) {
      String template = DartBundle.message("dart.feedback.error.template",
                                           ijBuild, sdkVersion, platformDescription, errorMessage.trim());
      try {
        File file = FileUtil.createTempFile("report", ".md");
        FileUtil.writeToFile(file, template);
        String url = DartBundle.message("dart.feedback.error.url", file.getAbsolutePath());
        openBrowserOnFeedbackForm(url, project);
      }
      catch (IOException e) {
        // ignore it
      }
    }
    else {
      String url = DartBundle.message("dart.feedback.url", ijBuild, sdkVersion);
      openBrowserOnFeedbackForm(url, project);
    }
  }

  public static void openBrowserOnFeedbackForm(String urlTemplate, Project project) {
    BrowserUtil.browse(urlTemplate, project);
  }

  protected String getSdkVersion(@Nullable Project project) {
    DartSdk sdk = getSdk(project);
    return sdk == null ? "<NO SDK>" : sdk.getVersion();
  }

  protected DartSdk getSdk(@Nullable Project project) {
    return project == null ? DartSdk.getGlobalDartSdk() : DartSdk.getDartSdk(project);
  }
}
