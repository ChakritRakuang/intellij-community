// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class JavaEditablePostfixTemplate extends EditablePostfixTemplate {
  private static final Condition<PsiElement> PSI_ERROR_FILTER = element -> !PsiTreeUtil.hasErrorElements(element);

  @NotNull private final List<JavaPostfixTemplateExpressionCondition> myExpressionConditions;
  @NotNull private final LanguageLevel myMinimumLanguageLevel;
  private final boolean myUseTopmostExpression;

  public JavaEditablePostfixTemplate(@NotNull String key,
                                     @NotNull List<JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull String templateText,
                                     @NotNull PostfixEditableTemplateProvider provider) {
    super(key, templateText, provider);
    myExpressionConditions = expressionConditions;
    myMinimumLanguageLevel = minimumLanguageLevel;
    myUseTopmostExpression = useTopmostExpression;
  }

  @NotNull
  public List<JavaPostfixTemplateExpressionCondition> getExpressionConditions() {
    return myExpressionConditions;
  }

  @NotNull
  public LanguageLevel getMinimumLanguageLevel() {
    return myMinimumLanguageLevel;
  }

  public boolean isUseTopmostExpression() {
    return myUseTopmostExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    JavaEditablePostfixTemplate template = (JavaEditablePostfixTemplate)o;
    return myUseTopmostExpression == template.myUseTopmostExpression &&
           Objects.equals(myExpressionConditions, template.myExpressionConditions) &&
           myMinimumLanguageLevel == template.myMinimumLanguageLevel;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myExpressionConditions, myMinimumLanguageLevel);
  }

  @Override
  protected List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
    if (!PsiUtil.getLanguageLevel(context).isAtLeast(myMinimumLanguageLevel)) {
      return Collections.emptyList();
    }
    List<PsiElement> expressions;
    if (myUseTopmostExpression) {
      expressions = ContainerUtil.createMaybeSingletonList(JavaPostfixTemplatesUtils.getTopmostExpression(context));
    }
    else {
      PsiFile file = context.getContainingFile();
      expressions = ContainerUtil.newArrayList(IntroduceVariableBase.collectExpressions(file, document, Math.max(offset - 1, 0), false));
    }

    if (DumbService.getInstance(context.getProject()).isDumb()) return Collections.emptyList();

    if (!expressions.isEmpty()) return expressions;

    return ContainerUtil.filter(expressions, e -> {
      if (!PSI_ERROR_FILTER.value(e) || !(expressions instanceof PsiExpression)) {
        return false;
      }
      for (JavaPostfixTemplateExpressionCondition condition : myExpressionConditions) {
        if (condition.value((PsiExpression)e)) {
          return true;
        }
      }
      return myExpressionConditions.isEmpty();
    });
  }

  @NotNull
  @Override
  protected Function<PsiElement, String> getElementRenderer() {
    return JavaPostfixTemplatesUtils.getRenderer();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }
}
