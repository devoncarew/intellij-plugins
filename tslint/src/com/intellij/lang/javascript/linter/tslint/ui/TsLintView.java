package com.intellij.lang.javascript.linter.tslint.ui;

import com.intellij.javascript.nodejs.util.NodePackage;
import com.intellij.lang.javascript.linter.JSLinterBaseView;
import com.intellij.lang.javascript.linter.NodeModuleConfigurationView;
import com.intellij.lang.javascript.linter.tslint.config.TsLintConfiguration;
import com.intellij.lang.javascript.linter.tslint.config.TsLintState;
import com.intellij.lang.javascript.linter.tslint.ide.TsLintConfigFileType;
import com.intellij.lang.javascript.linter.ui.JSLinterConfigFileTexts;
import com.intellij.lang.javascript.linter.ui.JSLinterConfigFileView;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;

/**
 * @author Irina.Chernushina on 6/3/2015.
 */
public class TsLintView extends JSLinterBaseView<TsLintState> {
  private static final JSLinterConfigFileTexts CONFIG_TEXTS = getConfigTexts();

  private final Project myProject;
  private final boolean myDialog;
  private final NodeModuleConfigurationView myNodeModuleConfigurationView;
  private final JSLinterConfigFileView myConfigFileView;
  private TextFieldWithBrowseButton myRules;

  public TsLintView(@NotNull Project project, boolean fullModeDialog) {
    super(fullModeDialog);
    myProject = project;
    myDialog = fullModeDialog;
    myConfigFileView = new JSLinterConfigFileView(project, CONFIG_TEXTS, TsLintConfigFileType.INSTANCE);
    myConfigFileView.setAdditionalConfigFilesProducer(() -> {
      final String home = System.getProperty("user.home");
      final LocalFileSystem lfs = LocalFileSystem.getInstance();
      final File homeFile = new File(home);
      VirtualFile homeVf = lfs.findFileByIoFile(homeFile);
      if (homeVf == null) {
        homeVf = lfs.refreshAndFindFileByIoFile(homeFile);
      }
      if (homeVf == null) return Collections.emptyList();
      VirtualFile config = homeVf.findChild(TsLintConfiguration.TSLINT_JSON);
      if (config == null) {
        config = lfs.refreshAndFindFileByIoFile(new File(homeFile, TsLintConfiguration.TSLINT_JSON));
      }
      if (config == null) return Collections.emptyList();
      return Collections.singletonList(config);
    });
    myNodeModuleConfigurationView = new NodeModuleConfigurationView(project, "tslint", "TSLint", null);
  }

  @Nullable
  @Override
  protected Component createTopRightComponent() {
    return null;
  }

  @NotNull
  @Override
  protected Component createCenterComponent() {
    myRules = new TextFieldWithBrowseButton();
    SwingHelper.installFileCompletionAndBrowseDialog(myProject, myRules, "Select additional rules directory",
                                                     FileChooserDescriptorFactory.createSingleFolderDescriptor());
    final FormBuilder nodeFieldsWrapperBuilder = FormBuilder.createFormBuilder()
      .setAlignLabelOnRight(true)
      .setHorizontalGap(UIUtil.DEFAULT_HGAP)
      .setVerticalGap(UIUtil.DEFAULT_VGAP)
      .setFormLeftIndent(UIUtil.DEFAULT_HGAP)
      .addLabeledComponent("&Node interpreter:", myNodeModuleConfigurationView.getNodeInterpreterField())
      .addLabeledComponent("TSLint package:", myNodeModuleConfigurationView.getPackageField());

    JPanel panel = FormBuilder.createFormBuilder()
      .setAlignLabelOnRight(true)
      .setHorizontalGap(UIUtil.DEFAULT_HGAP)
      .setVerticalGap(UIUtil.DEFAULT_VGAP)
      .setFormLeftIndent(UIUtil.DEFAULT_HGAP)
      .addComponent(nodeFieldsWrapperBuilder.getPanel())
      .addComponent(myConfigFileView.getComponent())
      .addSeparator(4)
      .addVerticalGap(4)
      .addLabeledComponent("Additional rules directory:", myRules)
      .getPanel();
    final JPanel centerPanel = SwingHelper.wrapWithHorizontalStretch(panel);
    centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
    return centerPanel;
  }

  @Override
  protected void handleEnableStatusChanged(boolean enabled) {
    enableCustomConfigPath(enabled);
  }

  @NotNull
  @Override
  protected TsLintState getState() {
    final TsLintState.Builder builder = new TsLintState.Builder()
      .setNodePath(myNodeModuleConfigurationView.getNodeInterpreterField().getInterpreterRef())
      .setPackagePath(myNodeModuleConfigurationView.getPackageField().getSelected().getSystemDependentPath())
      .setCustomConfigFileUsed(myConfigFileView.isCustomConfigFileUsed())
      .setCustomConfigFilePath(myConfigFileView.getCustomConfigFilePath());
    if (!StringUtil.isEmptyOrSpaces(myRules.getText())) {
      builder.setRulesDirectory(myRules.getText().trim());
    }
    return builder.build();
  }

  @Override
  protected void setState(@NotNull TsLintState state) {
    myNodeModuleConfigurationView.getNodeInterpreterField().setInterpreterRef(state.getInterpreterRef());
    myNodeModuleConfigurationView.getPackageField().setSelected(new NodePackage(state.getPackagePath()));

    myConfigFileView.setCustomConfigFileUsed(state.isCustomConfigFileUsed());
    myConfigFileView.setCustomConfigFilePath(StringUtil.notNullize(state.getCustomConfigFilePath()));
    if (! StringUtil.isEmptyOrSpaces(state.getRulesDirectory())) {
      myRules.setText(state.getRulesDirectory());
    }

    resizeOnSeparateDialog();
  }

  private void resizeOnSeparateDialog() {
    if (isFullModeDialog()) {
      myNodeModuleConfigurationView.setPreferredWidthToComponents();
      myConfigFileView.setPreferredWidthToComponents();
    }
  }

  private void enableCustomConfigPath(boolean enabled) {
    myConfigFileView.onEnabledStateChanged(enabled);
  }

  private static JSLinterConfigFileTexts getConfigTexts() {
    return new JSLinterConfigFileTexts("Search for tslint.json",
                                       "When linting a TypeScript file, TSLint looks for tslint.json starting from the file's folder and then moving up to the filesystem root" +
                                       " or in the user's home directory.",
                                       "Select TSLint configuration file (*.json)");
  }
}
