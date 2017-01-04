package com.jetbrains.lang.dart.ide.runner.server.vmService.frame;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.ide.runner.server.vmService.DartVmServiceDebugProcess;
import com.jetbrains.lang.dart.psi.DartFunctionBody;
import com.jetbrains.lang.dart.psi.DartRecursiveVisitor;
import org.dartlang.vm.service.consumer.GetObjectConsumer;
import org.dartlang.vm.service.element.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DartVmServiceStackFrame extends XStackFrame {

  @NotNull private final DartVmServiceDebugProcess myDebugProcess;
  @NotNull private final String myIsolateId;
  @NotNull private final Frame myVmFrame;
  @Nullable private final InstanceRef myException;
  @Nullable private final XSourcePosition mySourcePosition;

  public DartVmServiceStackFrame(@NotNull final DartVmServiceDebugProcess debugProcess,
                                 @NotNull final String isolateId,
                                 @NotNull final Frame vmFrame,
                                 @Nullable final InstanceRef exception) {
    myDebugProcess = debugProcess;
    myIsolateId = isolateId;
    myVmFrame = vmFrame;
    myException = exception;
    mySourcePosition = debugProcess.getSourcePosition(isolateId, vmFrame.getLocation().getScript(), vmFrame.getLocation().getTokenPos());
  }

  @NotNull
  public String getIsolateId() {
    return myIsolateId;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @NotNull
  public DartVmServiceDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  @NotNull
  public Frame getVmFrame() {
    return myVmFrame;
  }

  @Override
  public void customizePresentation(@NotNull final ColoredTextContainer component) {
    final String name = StringUtil.trimEnd(myVmFrame.getCode().getName(), "="); // trim setter postfix
    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

    if (mySourcePosition != null) {
      final String text = " (" + mySourcePosition.getFile().getName() + ":" + (mySourcePosition.getLine() + 1) + ")";
      component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    component.setIcon(AllIcons.Debugger.StackFrame);
  }

  @NotNull
  @Override
  public Object getEqualityObject() {
    return myVmFrame.getLocation().getScript().getId() + ":" + myVmFrame.getCode().getId();
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (myException != null) {
      final DartVmServiceValue exception = new DartVmServiceValue(myDebugProcess, myIsolateId, "exception", myException, null, null, true);
      node.addChildren(XValueChildrenList.singleton(exception), false);
    }

    final ElementList<BoundVariable> vars = myVmFrame.getVars();

    BoundVariable thisVar = null;
    for (BoundVariable var : vars) {
      if ("this".equals(var.getName())) {
        // in some cases "this" var is not the first one in the list, no idea why
        thisVar = var;
        break;
      }
    }

    addStaticFieldsIfPresentAndThenAllVars(node, thisVar, vars);
  }

  private void addStaticFieldsIfPresentAndThenAllVars(@NotNull final XCompositeNode node,
                                                      @Nullable final BoundVariable thisVar,
                                                      @NotNull final ElementList<BoundVariable> vars) {
    if (thisVar == null) {
      final XValueChildrenList children = new XValueChildrenList(vars.size());
      addVars(children, vars);
      addAutoExpressions(children);
      node.addChildren(children, true);
      return;
    }

    myDebugProcess.getVmServiceWrapper().getObject(myIsolateId, thisVar.getValue().getClassRef().getId(), new GetObjectConsumer() {
      @Override
      public void received(Obj classObj) {
        final SmartList<FieldRef> staticFields = new SmartList<>();
        for (FieldRef fieldRef : ((ClassObj)classObj).getFields()) {
          if (fieldRef.isStatic()) {
            staticFields.add(fieldRef);
          }
        }

        final XValueChildrenList children = new XValueChildrenList(vars.size());
        if (!staticFields.isEmpty()) {
          children.addTopGroup(new DartStaticFieldsGroup(myDebugProcess, myIsolateId, ((ClassObj)classObj).getName(), staticFields));
        }
        addVars(children, vars);
        addAutoExpressions(children);
        node.addChildren(children, true);
      }

      @Override
      public void received(Sentinel sentinel) {
        node.setErrorMessage(sentinel.getValueAsString());
      }

      @Override
      public void onError(RPCError error) {
        node.setErrorMessage(error.getMessage());
      }
    });
  }

  private void addVars(@NotNull final XValueChildrenList children, @NotNull final ElementList<BoundVariable> vars) {
    for (BoundVariable var : vars) {
      final InstanceRef value = var.getValue();
      if (value != null) {
        final DartVmServiceValue.LocalVarSourceLocation varLocation =
          "this".equals(var.getName())
          ? null
          : new DartVmServiceValue.LocalVarSourceLocation(myVmFrame.getLocation().getScript(), var.getDeclarationTokenPos());
        children.add(new DartVmServiceValue(myDebugProcess, myIsolateId, var.getName(), value, varLocation, null, false));
      }
    }
  }

  private static final Pair<Set<String>, Set<TextWithImports>> EMPTY_USED_VARS =
    Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());

  private void addAutoExpressions(@NotNull final XValueChildrenList children) {
    if (!XDebuggerSettingsManager.getInstance().getDataViewSettings().isAutoExpressions()) {
      return;
    }

    //PsiTreeUtil.getParentOfType()
    //PsiRecursiveElementWalkingVisitor

    // TODO: look for referenced globals from the start of the method until the current source location

    // TODO: look for referenced instance fields from the start of the method until the current source location

    // TODO: look for referenced statics from the start of the method until the current source location

    final Set<String> visibleVariables = new HashSet<>();
    final Set<String> visibleLocals = new HashSet<>();

    Pair<Set<String>, Set<TextWithImports>> usedVars = EMPTY_USED_VARS;
    if (mySourcePosition != null) {
      usedVars = ApplicationManager.getApplication().runReadAction(new Computable<Pair<Set<String>, Set<TextWithImports>>>() {
        @Override
        public Pair<Set<String>, Set<TextWithImports>> compute() {
          return findReferencedVars(ContainerUtil.union(visibleVariables, visibleLocals), mySourcePosition);
        }
      });
    }

    DartTextWithImports text = new DartTextWithImports("foobar");

    NodeManagerImpl nodeManager = myDebugProcess.getNodeManager();
    WatchItemDescriptor descriptor = nodeManager.getWatchItemDescriptor(null, text, null);
    DartVmServiceEvaluator evaluator = new DartVmServiceEvaluator(this);

    children.add(new DartNamedValue(descriptor, evaluator, nodeManager));
  }

  @SuppressWarnings("Duplicates")
  private Pair<Set<String>, Set<TextWithImports>> findReferencedVars(Set<String> visibleVars, @NotNull XSourcePosition position) {
    final int line = position.getLine();
    if (line < 0) {
      return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
    }
    final VirtualFile file = position.getFile();
    final PsiFile positionFile = PsiManager.getInstance(myDebugProcess.getSession().getProject()).findFile(file);
    if (!positionFile.isValid() || !positionFile.getLanguage().isKindOf(DartLanguage.INSTANCE)) {
      return Pair.create(visibleVars, Collections.emptySet());
    }

    final VirtualFile vFile = positionFile.getVirtualFile();
    final Document doc = vFile != null ? FileDocumentManager.getInstance().getDocument(vFile) : null;
    if (doc == null || doc.getLineCount() == 0 || line > (doc.getLineCount() - 1)) {
      return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
    }

    PsiElement tempElement = positionFile.findElementAt(doc.getLineStartOffset(line));
    DartFunctionBody functionBody = PsiTreeUtil.getParentOfType(tempElement, DartFunctionBody.class);

    functionBody.accept(new DartRecursiveVisitor() {
      public void visitElement(PsiElement element) {
        super.visitElement(element);
      }
    });

    final TextRange limit = calculateLimitRange(positionFile, doc, line);

    int startLine = Math.max(limit.getStartOffset(), line - 1);
    startLine = Math.min(startLine, limit.getEndOffset());
    while (startLine > limit.getStartOffset() && shouldSkipLine(positionFile, doc, startLine)) {
      startLine--;
    }
    final int startOffset = doc.getLineStartOffset(startLine);

    int endLine = Math.min(line + 2, limit.getEndOffset());
    while (endLine < limit.getEndOffset() && shouldSkipLine(positionFile, doc, endLine)) {
      endLine++;
    }
    final int endOffset = doc.getLineEndOffset(endLine);

    final TextRange lineRange = new TextRange(startOffset, endOffset);
    if (!lineRange.isEmpty()) {
      final int offset = CharArrayUtil.shiftForward(doc.getCharsSequence(), doc.getLineStartOffset(line), " \t");
      PsiElement element = positionFile.findElementAt(offset);
      if (element != null) {
        //PsiMethod method = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class);
        //if (method != null) {
        //  element = method;
        //}
        //else {
        //  PsiField field = PsiTreeUtil.getNonStrictParentOfType(element, PsiField.class);
        //  if (field != null) {
        //    element = field;
        //  }
        //  else {
        //    final PsiClassInitializer initializer = PsiTreeUtil.getNonStrictParentOfType(element, PsiClassInitializer.class);
        //    if (initializer != null) {
        //      element = initializer;
        //    }
        //  }
        //}
        //
        ////noinspection unchecked
        //if (element instanceof PsiCompiledElement) {
        //  return Pair.create(visibleVars, Collections.emptySet());
        //}
        //else {
        //  JavaStackFrame.VariablesCollector collector = new JavaStackFrame.VariablesCollector(visibleVars, adjustRange(element, lineRange));
        //  element.accept(collector);
        //  return Pair.create(collector.getVars(), collector.getExpressions());
        //}
      }
    }

    return Pair.create(Collections.emptySet(), Collections.<TextWithImports>emptySet());
  }

  @SuppressWarnings("Duplicates")
  private static TextRange calculateLimitRange(final PsiFile file, final Document doc, final int line) {
    final int offset = doc.getLineStartOffset(line);
    if (offset > 0) {
      DartFunctionBody method = PsiTreeUtil.getParentOfType(file.findElementAt(offset), DartFunctionBody.class, false);
      if (method != null) {
        final TextRange elemRange = method.getTextRange();
        return new TextRange(doc.getLineNumber(elemRange.getStartOffset()), doc.getLineNumber(elemRange.getEndOffset()));
      }
    }
    return new TextRange(0, doc.getLineCount() - 1);
  }

  private static boolean shouldSkipLine(final PsiFile file, Document doc, int line) {
    final int start = doc.getLineStartOffset(line);
    final int end = doc.getLineEndOffset(line);
    final int _start = CharArrayUtil.shiftForward(doc.getCharsSequence(), start, " \n\t");
    if (_start >= end) {
      return true;
    }

    // TODO: Look for variable references.
    return false;
  }

  //@SuppressWarnings("Duplicates")
  //private static boolean shouldSkipLine(final PsiFile file, Document doc, int line) {
  //  final int start = doc.getLineStartOffset(line);
  //  final int end = doc.getLineEndOffset(line);
  //  final int _start = CharArrayUtil.shiftForward(doc.getCharsSequence(), start, " \n\t");
  //  if (_start >= end) {
  //    return true;
  //  }
  //
  //  TextRange alreadyChecked = null;
  //  for (PsiElement elem = file.findElementAt(_start); elem != null && elem.getTextOffset() <= end && (alreadyChecked == null || !alreadyChecked .contains(elem.getTextRange())); elem = elem.getNextSibling()) {
  //    for (PsiElement _elem = elem; _elem.getTextOffset() >= _start; _elem = _elem.getParent()) {
  //      alreadyChecked = _elem.getTextRange();
  //
  //      if (_elem instanceof PsiDeclarationStatement) {
  //        final PsiElement[] declared = ((PsiDeclarationStatement)_elem).getDeclaredElements();
  //        for (PsiElement declaredElement : declared) {
  //          if (declaredElement instanceof PsiVariable) {
  //            return false;
  //          }
  //        }
  //      }
  //
  //      if (_elem instanceof PsiJavaCodeReferenceElement) {
  //        final PsiElement resolved = ((PsiJavaCodeReferenceElement)_elem).resolve();
  //        if (resolved instanceof PsiVariable) {
  //          return false;
  //        }
  //      }
  //    }
  //  }
  //  return true;
  //}

  @Nullable
  @Override
  public XDebuggerEvaluator getEvaluator() {
    return new DartVmServiceEvaluator(myDebugProcess, myIsolateId, myVmFrame);
  }

  public boolean isInDartSdkPatchFile() {
    return mySourcePosition != null && (mySourcePosition.getFile() instanceof LightVirtualFile);
  }
}

class DartTextWithImports implements TextWithImports {
  private String myText;

  DartTextWithImports(String text) {
    myText = text;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public void setText(String newText) {
    myText = newText;
  }

  @NotNull
  @Override
  public String getImports() {
    return "";
  }

  @Override
  public CodeFragmentKind getKind() {
    return CodeFragmentKind.EXPRESSION;
  }

  @Override
  public boolean isEmpty() {
    return myText.isEmpty();
  }

  @Override
  public String toExternalForm() {
    return getText();
  }

  @Nullable
  @Override
  public FileType getFileType() {
    return DartFileType.INSTANCE;
  }
}

class DartNamedValue extends XNamedValue implements NodeDescriptorProvider {
  private final WatchItemDescriptor myDescriptor;
  private final DartVmServiceEvaluator myEvaluator;
  private final NodeManagerImpl myManager;

  public DartNamedValue(WatchItemDescriptor descriptor, DartVmServiceEvaluator evaluator, NodeManagerImpl manager) {
    super(descriptor.getName());

    myDescriptor = descriptor;
    myEvaluator = evaluator;
    myManager = manager;
  }

  @NotNull
  public String getEvaluationExpression() {
    return myDescriptor.getName();
  }

  @Nullable
  public XInstanceEvaluator getInstanceEvaluator() {
    return (callback, frame) -> myEvaluator.evaluate(getEvaluationExpression(), callback, null);
  }

  @Override
  public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
    myEvaluator.evaluate(getEvaluationExpression(), new XDebuggerEvaluator.XEvaluationCallback() {
      @Override
      public void evaluated(@NotNull XValue result) {
        final Icon watchIcon = AllIcons.Debugger.Watch;

        result.computePresentation(new XValueNode() {
          @Override
          public void setPresentation(@Nullable Icon icon,
                                      @NonNls @Nullable String type,
                                      @NonNls @NotNull String value,
                                      boolean hasChildren) {
            node.setPresentation(watchIcon, type, value, hasChildren);
          }

          @Override
          public void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren) {
            node.setPresentation(watchIcon, presentation, hasChildren);
          }

          @Override
          public void setPresentation(@Nullable Icon icon,
                                      @NonNls @Nullable String type,
                                      @NonNls @NotNull String separator,
                                      @NonNls @Nullable String value,
                                      boolean hasChildren) {
            //noinspection deprecation
            node.setPresentation(watchIcon, type, separator, value, hasChildren);
          }

          @Override
          public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
            node.setFullValueEvaluator(fullValueEvaluator);
          }

          @Override
          public boolean isObsolete() {
            return node.isObsolete();
          }
        }, place);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        node.setPresentation(AllIcons.General.Error, new XErrorValuePresentation(errorMessage), false);
      }
    }, null);
  }

  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public NodeDescriptorImpl getDescriptor() {
    return myDescriptor;
  }
}
