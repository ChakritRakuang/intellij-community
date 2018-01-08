// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JavaPostfixTemplateEditor implements PostfixTemplateEditor<JavaEditablePostfixTemplate> {

  @Nullable private final Project myProject;
  @NotNull private final Editor myTemplateEditor;
  private final JBList<JavaPostfixTemplateExpressionCondition> myExpressionTypesList;
  private final DefaultListModel<JavaPostfixTemplateExpressionCondition> myExpressionTypesListModel;

  private JPanel myPanel;
  private JBCheckBox myApplyToTheTopmostJBCheckBox;
  private ComboBox<LanguageLevel> myLanguageLevelCombo;
  private JBLabel myExpressionVariableHint;
  private JPanel myExpressionTypesPanel;
  private JTextField myKeyTextField;
  private JPanel myTemplateEditorPanel;

  public JavaPostfixTemplateEditor(@Nullable Project project) {
    myProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();
    myTemplateEditor = TemplateEditorUtil.createEditor(false, createDocument(myProject), myProject);

    myExpressionTypesListModel = JBList.createDefaultListModel();
    myExpressionTypesList = new JBList<>(myExpressionTypesListModel);
    myExpressionTypesList.setCellRenderer(new ColoredListCellRenderer<JavaPostfixTemplateExpressionCondition>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends JavaPostfixTemplateExpressionCondition> list,
                                           JavaPostfixTemplateExpressionCondition value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        append(value.getPresentableName());
      }
    });
    myExpressionTypesPanel.setLayout(new BorderLayout());
    myExpressionTypesPanel.add(ToolbarDecorator.createDecorator(myExpressionTypesList)
                                 .setAddAction(button -> addExpressionType(button))
                                 .setRemoveAction(button -> ListUtil.removeSelectedItems(myExpressionTypesList))
                                 .createPanel());

    myTemplateEditorPanel.setLayout(new BorderLayout());
    myTemplateEditorPanel.add(myTemplateEditor.getComponent());
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myExpressionVariableHint);
    myExpressionVariableHint.setFontColor(UIUtil.FontColor.BRIGHTER);
  }

  private void createUIComponents() {
    myLanguageLevelCombo = new ComboBox<>(LanguageLevel.values());
    myLanguageLevelCombo.setRenderer(new ColoredListCellRenderer<LanguageLevel>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, LanguageLevel value, int index, boolean selected, boolean hasFocus) {
        append(value.getPresentableText());
      }
    });
  }

  @Override
  public void dispose() {
    TemplateEditorUtil.disposeTemplateEditor(myTemplateEditor);
  }

  @Override
  public void reset(@NotNull JavaEditablePostfixTemplate template) {
    myLanguageLevelCombo.setSelectedItem(template.getMinimumLanguageLevel());
    myApplyToTheTopmostJBCheckBox.setSelected(template.isUseTopmostExpression());
    ApplicationManager.getApplication().runWriteAction(() -> myTemplateEditor.getDocument().setText(template.getTemplateText()));
    myKeyTextField.setText(template.getKey());
  }

  @Override
  public boolean isModified(@NotNull JavaEditablePostfixTemplate template) {
    return !template.getMinimumLanguageLevel().equals(myLanguageLevelCombo.getSelectedItem()) ||
           template.isUseTopmostExpression() != myApplyToTheTopmostJBCheckBox.isSelected() ||
           !myTemplateEditor.getDocument().getText().equals(template.getTemplateText()) ||
           !myKeyTextField.getText().equals(template.getKey());
  }

  @Override
  public void apply(@NotNull JavaEditablePostfixTemplate template) throws ConfigurationException {

  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  private static Document createDocument(@Nullable Project project) {
    if (project == null) {
      return EditorFactory.getInstance().createDocument("");
    }
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createCodeBlockCodeFragment("", psiFacade.findPackage(""), true);
    DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(fragment, false);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  private void addExpressionType(@NotNull AnActionButton button) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()));
    group.add(new AddConditionAction(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateVoidExpressionCondition()));
    group.add(new ChooseClassAction());
    DataContext context = DataManager.getInstance().getDataContext(button.getContextComponent());
    ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    //noinspection ConstantConditions
    popup.show(button.getPreferredPopupPoint());
  }

  private class AddConditionAction extends DumbAwareAction {
    @NotNull
    private final JavaPostfixTemplateExpressionCondition myCondition;

    public AddConditionAction(JavaPostfixTemplateExpressionCondition condition) {
      super(condition.getPresentableName());
      myCondition = condition;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myExpressionTypesListModel.addElement(myCondition);
    }
  }

  private class ChooseClassAction extends DumbAwareAction {
    private final ClassFilter FILTER = new ClassFilter() {
      public boolean isAccepted(PsiClass aClass) {
        return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
      }
    };

    protected ChooseClassAction() {
      super("choose class...");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {

      String fqn = getFqn();
      if (fqn != null) {
        myExpressionTypesListModel.addElement(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(fqn));
      }
    }

    private String getFqn() {
      String title = "Choose Class";
      if (myProject == null) {
        return Messages.showInputDialog(myPanel, title, title, null);
      }
      GlobalSearchScope scope = GlobalSearchScope.projectScope(myProject);
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(title, scope, FILTER, null);
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelected();
      return selectedClass != null ? selectedClass.getQualifiedName() : null; }
  }
}
