package testplugin.handlers;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map; 

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class RefactorHandler extends AbstractHandler {

	public static List<ICompilationUnit> classes = new ArrayList<>();
	public static IProgressMonitor monitor;
	// public static Logger logger = Logger.getLogger(RefactorHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		ISelection selection = window.getSelectionService().getSelection();
		try {
			selectionChanged(selection, window);
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings({ "unchecked" })
	public void selectionChanged(ISelection selection, IWorkbenchWindow window) throws JavaModelException {
		List<IJavaElement> select = null;
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection strut = ((IStructuredSelection) selection);
			if (strut.getFirstElement() instanceof IJavaElement) {
				select = (List<IJavaElement>) strut.toList();
				for (IJavaElement cu : select) {
					findCompilationUnits(cu);
				}
				for (ICompilationUnit icu : classes) {
					String path = Platform.getLocation().toString();
					String classname = icu.getElementName().substring(0, icu.getElementName().length() - 5);
					String iunitName = "I" + classname;
					String iunitClassName = iunitName + ".java";
					String iunitClassPath = path + icu.getParent().getPath().toString() + "/" + iunitClassName;
					path = path + icu.getPath().toString();
					CompilationUnit cu = getCompilationUnit(path);
					List<TypeDeclaration> typeDeclaration = cu.types();
					if (typeDeclaration.size() != 0) {
						if (!(typeDeclaration.get(0)).isInterface()) {
							File file = new File(iunitClassPath);
							Boolean flag = file.exists();
							Boolean overwrite = false;
							if (flag) {
								overwrite = MessageDialog.openQuestion(window.getShell(), "Prompt",
										"File " + iunitClassName + " already exists, do you want to overwrite it ?");
							}
							if ((!flag) || overwrite) {
								ASTNode root = cu.getRoot();
								String builder = getBuilder(icu, iunitName, root).toString();
								IPackageFragment packageFragment = (IPackageFragment) icu.getParent();

								Name clazz = root.getAST().newName(iunitName);
								SimpleType typeParameter = root.getAST().newSimpleType(clazz);
								Boolean addImplememnt = true;
								for (Object ob : typeDeclaration.get(0).superInterfaceTypes()) {
									if (ob.toString().equals(iunitName)) {
										addImplememnt = false;
										break;
									}
								}
								if (addImplememnt) {
									typeDeclaration.get(0).superInterfaceTypes().add(typeParameter);
								}
								icu = packageFragment.getCompilationUnit(classname + ".java");
								ISourceRange source = icu.getSourceRange();
								icu.getBuffer().replace(source.getOffset(), source.getLength(), root.toString());
								icu.commitWorkingCopy(true, monitor);
								if (overwrite) {
									ICompilationUnit n = packageFragment.getCompilationUnit(iunitClassName);
									ISourceRange sourceRange = n.getSourceRange();
									n.getBuffer().replace(sourceRange.getOffset(), sourceRange.getLength(), builder);
									n.commitWorkingCopy(true, monitor);
									formatCode(n);
								} else if (!flag) {
									ICompilationUnit n = packageFragment.createCompilationUnit(iunitClassName, builder,
											false, monitor);
									formatCode(n);
								}
							}
							overwrite = false;
							flag = false;
						}
					}
				}
			}
		}
		classes.clear();
	}

	private StringBuilder getBuilder(ICompilationUnit icu, String iunitName, ASTNode root) {
		Map<MethodDeclaration, Boolean> changes = new LinkedHashMap<MethodDeclaration, Boolean>();
		changes = getMethods(root);
		StringBuilder builder = new StringBuilder();
		try {
			for (IPackageDeclaration packageDeclaration : icu.getPackageDeclarations()) {
				builder = builder.append("package ").append(packageDeclaration.getElementName()).append(";");
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		builder = builder.append(" public interface ").append(iunitName).append("{");
		for (MethodDeclaration method : changes.keySet()) {
			if (changes.get(method)) {
				builder = builder.append(method.getReturnType2().toString()).append(" ").append(method.getName())
						.append("();");
			}
		}
		builder = builder.append("}");
		return builder;
	}

	private Map<MethodDeclaration, Boolean> getMethods(ASTNode cuu) {
		Map<MethodDeclaration, Boolean> changes = new LinkedHashMap<MethodDeclaration, Boolean>();
		cuu.accept(new ASTVisitor() {
			@SuppressWarnings("unchecked")
			public boolean visit(MethodDeclaration node) {
				int modifier = node.getModifiers();
				if (Modifier.isPublic(modifier) && (!Modifier.isStatic(modifier))) {
					AST ast = node.getAST();
					MarkerAnnotation na = ast.newMarkerAnnotation();
					na.setTypeName(ast.newSimpleName("Override"));
					if (!node.modifiers().toString().contains("Override")) {
						node.modifiers().add(0, na);
					}
					changes.put(node, true);
				} else {
					changes.put(node, false);
				}
				return false;
			}
		});
		return changes;
	}

	@SuppressWarnings("restriction")
	private ICompilationUnit formatCode(ICompilationUnit icu) throws JavaModelException {
		ICompilationUnit copyCU = icu;
		copyCU.becomeWorkingCopy(monitor);
		String lineDelimiter = StubUtility.getLineDelimiterUsed(copyCU.getJavaProject());
		IBuffer buffer = copyCU.getBuffer();
		ISourceRange sourceRange = copyCU.getSourceRange();
		String originalContent = buffer.getText(sourceRange.getOffset(), sourceRange.getLength());
		String formattedContent = CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT, originalContent, 0,
				lineDelimiter, copyCU.getJavaProject());
		formattedContent = Strings.trimLeadingTabsAndSpaces(formattedContent);
		buffer.replace(sourceRange.getOffset(), sourceRange.getLength(), formattedContent);
		copyCU.commitWorkingCopy(true, monitor);
		return copyCU;
	}

	private void findCompilationUnits(IJavaElement cu) throws JavaModelException {
		if (cu.getElementType() == IJavaElement.COMPILATION_UNIT) {
			classes.add((ICompilationUnit) cu);
		} else {
			IJavaElement[] elements;
			elements = ((IPackageFragment) cu).getChildren();
			for (IJavaElement element : elements) {
				if (element.getElementType() == IJavaElement.COMPILATION_UNIT) {
					classes.add((ICompilationUnit) element);
				} else if (element.getElementType() < IJavaElement.COMPILATION_UNIT) {
					findCompilationUnits(element);
				}
			}
		}

	}

	private static CompilationUnit getCompilationUnit(String javaFilePath) {
		byte[] input = null;
		try {
			BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(javaFilePath));
			input = new byte[bufferedInputStream.available()];
			bufferedInputStream.read(input);
			bufferedInputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		@SuppressWarnings("deprecation")
		ASTParser astParser = ASTParser.newParser(AST.JLS3);
		astParser.setResolveBindings(true);
		astParser.setBindingsRecovery(true);
		astParser.setSource(new String(input).toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);

		CompilationUnit result = (CompilationUnit) (astParser.createAST(null));
		return result;
	}
}
