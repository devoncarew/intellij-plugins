/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.osmorc.manifest.lang.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.osmorc.manifest.lang.psi.AssignmentExpression;
import org.jetbrains.lang.manifest.psi.HeaderValuePart;

/**
 * @author <a href="mailto:robert@beeger.net">Robert F. Beeger</a>
 */
public abstract class AbstractAssignmentExpression extends ASTWrapperPsiElement implements AssignmentExpression {
  public AbstractAssignmentExpression(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String getName() {
    HeaderValuePart namePsi = getNameElement();
    String result = namePsi != null ? namePsi.getUnwrappedText() : null;
    return result != null ? result : "<unnamed>";
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    // TODO: Implement
    throw new IncorrectOperationException();
  }

  @Override
  public HeaderValuePart getNameElement() {
    return PsiTreeUtil.getChildOfType(this, HeaderValuePart.class);
  }

  @Override
  public HeaderValuePart getValueElement() {
    HeaderValuePart namePsi = getNameElement();
    return namePsi != null ? PsiTreeUtil.getNextSiblingOfType(namePsi, HeaderValuePart.class) : null;
  }

  @Override
  public String getValue() {
    HeaderValuePart valuePsi = getValueElement();
    String result = valuePsi != null ? valuePsi.getUnwrappedText() : null;
    return result != null ? result : "";
  }
}
