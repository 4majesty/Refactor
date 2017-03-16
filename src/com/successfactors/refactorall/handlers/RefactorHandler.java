package com.successfactors.refactorall.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.internal.runtime.Activator;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsCreator;
import org.eclipse.jdt.internal.corext.refactoring.structure.constraints.SuperTypeConstraintsModel;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TypeEnvironment;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.osgi.framework.FrameworkUtil;

@SuppressWarnings({ "restriction", "deprecation" })
public class RefactorHandler extends AbstractHandler {
    public static Set<ICompilationUnit> classes = new HashSet<ICompilationUnit>();
    public static IProgressMonitor monitor = new NullProgressMonitor();
    public static ILog log = Platform.getLog(FrameworkUtil.getBundle(Platform.class));
    public static IWorkbenchWindow window;
    protected WorkingCopyOwner fOwner = new WorkingCopyOwner() {
    };
    public ASTNode[] nodes;
    public IType type;
    public String iunitName;
    public String classname;
    public boolean Found = false;
    public List<TypeDeclaration> Class = new ArrayList<>();
    public List otherClass = new ArrayList<>();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
	window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
	ISelection selection = window.getSelectionService().getSelection();
	try {
	    selectionChanged(selection, window);
	} catch (JavaModelException e) {
	    log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, e.getMessage()));
	}
	return null;
    }

    @SuppressWarnings({ "unchecked" })
    public void selectionChanged(ISelection selection, IWorkbenchWindow window) throws JavaModelException {
	List<IJavaElement> select = null;
	String iunitClassName = null;
	StringBuilder logContent = new StringBuilder();
	if (selection instanceof IStructuredSelection) {
	    IStructuredSelection strut = ((IStructuredSelection) selection);
	    if (strut.getFirstElement() instanceof IJavaElement) {
		select = (List<IJavaElement>) strut.toList();
		for (IJavaElement cu : select) {
		    findCompilationUnits(cu);
		}
		if (!classes.isEmpty()) {
		    // Prefix or Suffix
		    // if you want to add prefix, set FixType = "Prefix"
		    // else if you want add suffix, set FixType = "Suffix"
		    String FixType = "Prefix";
		    String FixValue = "I";
		    // System.setProperty("FixType", "Prefix");
		    // System.setProperty("FixValue", "I");
		    if (null != System.getProperties().getProperty("FixType")
			    && null != System.getProperties().getProperty("FixValue")) {
			FixValue = System.getProperties().getProperty("FixValue");
			FixType = System.getProperties().getProperty("FixType");
		    }

		    for (ICompilationUnit icu : classes) {
			long starTime = System.currentTimeMillis();
			IPackageFragment packageFragment = (IPackageFragment) icu.getParent();
			classname = icu.getElementName().substring(0, icu.getElementName().length() - 5);

			ASTParser astParser = ASTParser.newParser(AST.JLS8);
			astParser.setResolveBindings(true);
			astParser.setBindingsRecovery(true);
			astParser.setSource(icu);
			astParser.setKind(ASTParser.K_COMPILATION_UNIT);
			CompilationUnit trycu = (CompilationUnit) (astParser.createAST(null));

			findPublicClass(trycu);
			if (!Found) {
			    logContent.append("\r\n").append("Skip file:\r\n").append(classname)
				    .append("No public class").append("\r\n");
			} else {
			    CompilationUnit cu = trycu;
			    List<TypeDeclaration> typeDeclaration = new ArrayList<>();
			    for (int i = 0; i < cu.types().size(); i++) {
				if (!icu.getTypes()[i].isInterface() && !icu.getTypes()[i].isEnum()
					&& !icu.getTypes()[i].isAnnotation()) {
				    typeDeclaration.add((TypeDeclaration) cu.types().get(i));
				} else {
				    logContent.append("\r\n").append("Skip file:\r\n").append(classname).append("\r\n");
				}
			    }
			    if (typeDeclaration.size() != 0) {
				if (FixType == "Suffix" && null != System.getProperties().getProperty("FixValue")) {
				    iunitName = classname + FixValue;
				} else if (FixType == "Prefix"
					&& null != System.getProperties().getProperty("FixValue")) {
				    iunitName = FixValue + classname;
				} else {
				    iunitName = "I" + classname;
				}
				iunitClassName = iunitName + ".java";
				Boolean flag = packageFragment.getCompilationUnit(iunitClassName).exists();
				Boolean overwrite = true;
				if (null != System.getProperty("overwrite")) {
				    String Overwrite = System.getProperty("overwrite");
				    if (Overwrite == "false") {
					overwrite = false;
				    }
				}
				ASTNode root = cu.getRoot();
				String builder = getBuilder(cu, icu, typeDeclaration, iunitName, root).toString();
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
				icu.becomeWorkingCopy(new NullProgressMonitor());
				String lineDelimiter = StubUtility.getLineDelimiterUsed(icu);
				String formattedContent = CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT,
					root.toString(), 0, lineDelimiter, icu.getJavaProject());
				formattedContent = Strings.trimLeadingTabsAndSpaces(formattedContent);
				icu.getBuffer().setContents(formattedContent);
				JavaModelUtil.reconcile(icu);
				icu.commitWorkingCopy(false, (IProgressMonitor) monitor);
				ICompilationUnit n;
				if (flag) {
				    n = packageFragment.getCompilationUnit(iunitClassName);
				    if (overwrite) {
					n.becomeWorkingCopy(new NullProgressMonitor());
					ISourceRange sourceRange = n.getSourceRange();
					String nLineDel = StubUtility.getLineDelimiterUsed(n);
					String nFormattedContent = CodeFormatterUtil.format(
						CodeFormatter.K_COMPILATION_UNIT, builder, 0, nLineDel,
						n.getJavaProject());
					nFormattedContent = Strings.trimLeadingTabsAndSpaces(nFormattedContent);
					n.getBuffer().replace(sourceRange.getOffset(), sourceRange.getLength(),
						nFormattedContent);
					JavaModelUtil.reconcile(n);
					n.commitWorkingCopy(false, (IProgressMonitor) monitor);
				    }
				} else {
				    builder = CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT, builder, 0,
					    lineDelimiter, icu.getJavaProject());
				    n = packageFragment.createCompilationUnit(iunitClassName, builder, false, monitor);
				}
				try {
				    List<String> removeImports = new ArrayList<String>();
				    removeImports = removeUnusedImports(n, cu.imports());
				    if (null != removeImports && !removeImports.isEmpty()) {
					for (String removeImport : removeImports) {
					    if (n.getImport(removeImport).exists()) {
						n.getImport(removeImport).delete(false, (IProgressMonitor) monitor);
					    }
					}
				    }
				} catch (CoreException e) {
				    e.printStackTrace();
				}

				astParser.setResolveBindings(true);
				astParser.setBindingsRecovery(true);
				astParser.setSource(icu);
				astParser.setKind(ASTParser.K_COMPILATION_UNIT);
				CompilationUnit cunit = (CompilationUnit) (astParser.createAST(null));
				type = icu.getTypes()[0];
				AbstractTypeDeclaration subDeclaration = ASTNodeSearchUtil
					.getAbstractTypeDeclarationNode(type, cunit);
				ITypeBinding subBinding = subDeclaration.resolveBinding();
				ITypeBinding superBinding = subDeclaration.resolveBinding();
				RefactoringStatus status = new RefactoringStatus();
				status.merge(Checks.checkIfCuBroken(type));
				solveSuperTypeConstraints(icu, cunit, type, subBinding, superBinding, monitor, status);
				icu.becomeWorkingCopy(new NullProgressMonitor());
				String icudeli = StubUtility.getLineDelimiterUsed(icu);
				String icuFormattedContent = CodeFormatterUtil.format(CodeFormatter.K_COMPILATION_UNIT,
					root.toString(), 0, icudeli, icu.getJavaProject());
				icuFormattedContent = Strings.trimLeadingTabsAndSpaces(icuFormattedContent);
				icu.getBuffer().setContents(icuFormattedContent);
				JavaModelUtil.reconcile(icu);
				icu.commitWorkingCopy(false, (IProgressMonitor) monitor);

				try {
				    List<String> removeImports = new ArrayList<String>();
				    removeImports = removeUnusedImports(icu, cu.imports());
				    if (null != removeImports && !removeImports.isEmpty()) {
					for (String removeImport : removeImports) {
					    if (icu.getImport(removeImport).exists()) {
						icu.getImport(removeImport).delete(false, (IProgressMonitor) monitor);
					    }
					}
				    }
				} catch (CoreException e) {
				    e.printStackTrace();
				}
				overwrite = false;
				flag = false;
				long endTime = System.currentTimeMillis();
				long Time = endTime - starTime;
				logContent.append("\r\n").append("Time for producing every interface:\r\n")
					.append(iunitClassName).append("       ").append(Time).append("ms\r\n");
			    }
			}
		    }
		}
	    }
	}
	log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, logContent.toString()));
	classes.clear();
    }

    protected void solveSuperTypeConstraints(ICompilationUnit subUnit, CompilationUnit subNode, IType subType,
	    ITypeBinding subBinding, ITypeBinding superBinding, IProgressMonitor monitor, RefactoringStatus status)
	    throws JavaModelException {
	Assert.isNotNull(subType);
	Assert.isNotNull(subBinding);
	Assert.isNotNull(superBinding);
	Assert.isNotNull(monitor);
	Assert.isNotNull(status);
	int level = 3;
	TypeEnvironment environment = new TypeEnvironment();
	SuperTypeConstraintsModel model = new SuperTypeConstraintsModel(environment, environment.create(subBinding),
		environment.create(superBinding));
	SuperTypeConstraintsCreator creator = new SuperTypeConstraintsCreator(model, true);
	monitor.beginTask("", 300);
	monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
	Map firstPass = getReferencingCompilationUnits(subType, new SubProgressMonitor(monitor, 100), status);
	IJavaProject project = null;
	Collection collection = null;
	Object element = null;
	ICompilationUnit current = null;
	SearchResultGroup group = null;
	SearchMatch[] matches = null;
	Map groups = new HashMap<>();
	for (Iterator outer = firstPass.keySet().iterator(); outer.hasNext();) {
	    project = (IJavaProject) outer.next();
	    if (level == 3 && !JavaModelUtil.is50OrHigher(project))
		level = 2;
	    collection = (Collection) firstPass.get(project);
	    if (collection != null) {
		for (Iterator inner = collection.iterator(); inner.hasNext();) {
		    group = (SearchResultGroup) inner.next();
		    matches = group.getSearchResults();
		    for (int index = 0; index < matches.length; index++) {
			element = matches[index].getElement();
			if (element instanceof IMember) {
			    current = ((IMember) element).getCompilationUnit();
			    if (current != null)
				groups.put(current, group);
			}
		    }
		}
	    }
	}
	Set units = null;
	Set processed = new HashSet();
	if (subUnit != null)
	    processed.add(subUnit);
	model.beginCreation();
	IProgressMonitor subMonitor = new SubProgressMonitor(monitor, 120);
	try {
	    Set keySet = firstPass.keySet();
	    subMonitor.beginTask("", keySet.size() * 100);
	    subMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
	    for (Iterator outer = keySet.iterator(); outer.hasNext();) {
		project = (IJavaProject) outer.next();
		collection = (Collection) firstPass.get(project);
		if (collection != null) {
		    units = new HashSet(collection.size());
		    for (Iterator inner = collection.iterator(); inner.hasNext();) {
			group = (SearchResultGroup) inner.next();
			matches = group.getSearchResults();
			for (int index = 0; index < matches.length; index++) {
			    element = matches[index].getElement();
			    if (element instanceof IMember) {
				current = ((IMember) element).getCompilationUnit();
				if (current != null)
				    units.add(current);
			    }
			}
		    }
		    int SIZE_BATCH = 500;
		    List batches = new ArrayList(units);
		    int size = batches.size();
		    int iterations = (size - 1) / SIZE_BATCH + 1;
		    IProgressMonitor subsubMonitor = new SubProgressMonitor(subMonitor, 100);
		    try {
			subsubMonitor.beginTask("", iterations * 100);
			subsubMonitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
			Map options = RefactoringASTParser.getCompilerOptions(project);
			for (int index = 0; index < iterations; index++) {
			    List iteration = batches.subList(index * SIZE_BATCH,
				    Math.min(size, (index + 1) * SIZE_BATCH));
			    ASTParser parser = ASTParser.newParser(AST.JLS8);
			    parser.setWorkingCopyOwner(fOwner);
			    parser.setResolveBindings(true);
			    parser.setProject(project);
			    parser.setCompilerOptions(options);
			    IProgressMonitor subsubsubMonitor = new SubProgressMonitor(subsubMonitor, 100);
			    try {
				int count = iteration.size();
				subsubsubMonitor.beginTask("", count * 100);
				subsubsubMonitor
					.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
				ASTRequestor req = new ASTRequestor() {
				    public void acceptAST(ICompilationUnit unit, CompilationUnit node) {
					if (!processed.contains(unit)) {
					    boolean addImport = false;
					    int Length = 0;
					    performFirstPass(creator, null, groups, unit, node,
						    new SubProgressMonitor(subsubsubMonitor, 100));
					    for (ASTNode astnode : nodes) {
						if (astnode.getParent().getNodeType() == 23) {
						    if (!unit.getParent().equals(subUnit.getParent())) {
							addImport = true;
						    }
						    ASTParser astParser = ASTParser.newParser(AST.JLS8);
						    astParser.setResolveBindings(true);
						    astParser.setBindingsRecovery(true);
						    astParser.setSource(unit);
						    astParser.setKind(ASTParser.K_COMPILATION_UNIT);
						    try {
							subUnit.getPath().toString();

							unit.becomeWorkingCopy(new NullProgressMonitor());
							int astLength = astnode.getStartPosition() + Length;

							unit.getBuffer().replace(astLength, classname.length(),
								iunitName);
							JavaModelUtil.reconcile(unit);
							Length = iunitName.length() - classname.length() + Length;
							unit.commitWorkingCopy(false, (IProgressMonitor) monitor);

						    } catch (JavaModelException e) {
							e.printStackTrace();
						    }
						}
					    }
					    if (addImport) {
						try {
						    unit.createImport(
							    subUnit.getParent().getElementName() + "." + iunitName,
							    null, monitor);
						    unit.commitWorkingCopy(false, (IProgressMonitor) monitor);

						} catch (JavaModelException e) {
						    e.printStackTrace();
						}
					    }
					} else
					    subsubsubMonitor.worked(100);
				    }
				};
				parser.createASTs((ICompilationUnit[]) iteration.toArray(new ICompilationUnit[count]),
					new String[0], req, new NullProgressMonitor());
			    } finally {
				subsubsubMonitor.done();
			    }
			}
		    } finally {
			subsubMonitor.done();
		    }
		}
	    }
	} finally {
	    firstPass.clear();
	    subMonitor.done();
	}
    }

    protected void performFirstPass(SuperTypeConstraintsCreator creator, Map units, Map groups, ICompilationUnit unit,
	    CompilationUnit node, IProgressMonitor monitor) {
	try {
	    monitor.beginTask("", 100);
	    monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
	    node.accept(creator);
	    monitor.worked(20);
	    SearchResultGroup group = (SearchResultGroup) groups.get(unit);
	    if (group != null) {
		nodes = ASTNodeSearchUtil.getAstNodes(group.getSearchResults(), node);
	    }
	} finally {
	    monitor.done();
	}
    }

    protected Map getReferencingCompilationUnits(IType type, IProgressMonitor monitor, RefactoringStatus status)
	    throws JavaModelException {
	try {
	    monitor.beginTask("", 100);
	    monitor.setTaskName(RefactoringCoreMessages.SuperTypeRefactoringProcessor_creating);
	    RefactoringSearchEngine2 engine = new RefactoringSearchEngine2();
	    engine.setOwner(fOwner);
	    engine.setFiltering(true, true);
	    engine.setStatus(status);
	    engine.setScope(RefactoringScopeFactory.create(type));
	    engine.setPattern(SearchPattern.createPattern(type, IJavaSearchConstants.REFERENCES,
		    SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE));
	    engine.searchPattern(monitor);
	    return engine.getAffectedProjects();
	} finally {
	    monitor.done();
	}
    }

    private void findPublicClass(CompilationUnit cu) {
	cu.accept(new ASTVisitor() {
	    public boolean visit(TypeDeclaration node) {
		if (Modifier.isPublic(node.getModifiers())) {
		    Found = true;
		    Class.add(node);
		} else {

		    otherClass.add(node);
		}
		return false;
	    }
	});
    }

    private StringBuilder getBuilder(CompilationUnit cu, ICompilationUnit icu, List<TypeDeclaration> typeDeclaration,
	    String iunitName, ASTNode root) {
	Map<MethodDeclaration, Boolean> changes = new LinkedHashMap<MethodDeclaration, Boolean>();
	changes = getMethods(typeDeclaration, root);
	StringBuilder builder = new StringBuilder();
	try {
	    for (IPackageDeclaration packageDeclaration : icu.getPackageDeclarations()) {
		builder = builder.append("package ").append(packageDeclaration.getElementName()).append(";");
	    }
	    for (int i = 0; i <= cu.imports().size() - 1; i++) {
		builder.append(cu.imports().get(i).toString());
	    }
	} catch (JavaModelException e) {
	    log.log(new Status(IStatus.OK, Activator.PLUGIN_ID, e.getMessage()));
	}
	builder = builder.append(" public interface ").append(iunitName).append("{");
	for (MethodDeclaration method : changes.keySet()) {
	    if (changes.get(method) && null != method) {
		if (!method.typeParameters().isEmpty()) {
		    builder = builder.append("<");
		    for (int i = 0; i < method.typeParameters().size(); i++) {
			if (i > 0) {
			    builder = builder.append(", ");
			}
			builder = builder.append(method.typeParameters().get(i));
		    }
		    builder = builder.append("> ");
		}
		builder = builder.append(method.getReturnType2().toString()).append(" ").append(method.getName());

		if (method.parameters().size() != 0) {
		    for (int i = 0; i <= method.parameters().size() - 1; i++) {
			if (i == 0) {
			    builder.append(" (");
			}
			builder.append(method.parameters().get(i).toString());
			if (i == method.parameters().size() - 1) {
			    builder.append(") ");
			    if (method.thrownExceptionTypes().size() != 0) {
				for (int j = 0; j <= method.thrownExceptionTypes().size() - 1; j++) {
				    if (j == 0) {
					builder.append(" throws ");
				    }
				    builder.append(method.thrownExceptionTypes().get(j).toString());
				    if (j == method.thrownExceptionTypes().size() - 1) {
					builder.append(" ");
				    } else {
					builder.append(", ");
				    }
				}
			    }
			    builder.append(";");
			} else {
			    builder.append(", ");
			}
		    }
		} else {
		    builder.append(" ()");
		    if (method.thrownExceptionTypes().size() != 0) {
			for (int j = 0; j <= method.thrownExceptionTypes().size() - 1; j++) {
			    if (j == 0) {
				builder.append(" throws ");
			    }
			    builder.append(method.thrownExceptionTypes().get(j).toString());
			    if (j == method.thrownExceptionTypes().size() - 1) {
				builder.append(" ");
			    } else {
				builder.append(", ");
			    }
			}
		    }
		    builder.append(";");
		}
	    }
	}
	builder = builder.append("}");
	return builder;
    }

    private Map<MethodDeclaration, Boolean> getMethods(List<TypeDeclaration> typeDeclaration, ASTNode cuu) {
	Map<MethodDeclaration, Boolean> changes = new LinkedHashMap<MethodDeclaration, Boolean>();
	cuu.accept(new ASTVisitor() {
	    public boolean visit(MethodDeclaration node) {
		if (!otherClass.contains(node.getParent())) {
		    int modifier = node.getModifiers();
		    if (Modifier.isPublic(modifier) && (!Modifier.isStatic(modifier))
			    && null != node.getReturnType2()) {
			AST ast = node.getAST();
			MarkerAnnotation na = ast.newMarkerAnnotation();
			na.setTypeName(ast.newSimpleName("Override"));
			Boolean addAnno = true;
			for (Object anno : node.modifiers()) {
			    if (anno.toString().equals("@Override")) {
				addAnno = false;
				break;
			    }
			}
			if (addAnno) {
			    node.modifiers().add(0, na);
			}
			changes.put(node, true);
		    } else {
			changes.put(node, false);
		    }
		}
		return false;
	    }

	});
	return changes;
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

    private List<String> removeUnusedImports(ICompilationUnit icu, List<ASTNode> existingImports) throws CoreException {
	List<String> removeImports = new ArrayList<String>();
	ASTParser parser = ASTParser.newParser(AST.JLS8);
	parser.setSource(icu);
	parser.setResolveBindings(true);
	CompilationUnit root = (CompilationUnit) parser.createAST(null);
	if (root.getProblems().length == 0) {
	    return null;
	}
	List<ASTNode> importsDecls = root.imports();
	if (importsDecls.isEmpty()) {
	    return null;
	}
	int importsEnd = ASTNodes.getExclusiveEnd((ASTNode) importsDecls.get(importsDecls.size() - 1));
	IProblem[] problems = root.getProblems();
	for (int i = 0; i < problems.length; i++) {
	    IProblem curr = problems[i];
	    if (curr.getSourceEnd() < importsEnd) {
		int id = curr.getID();
		if (id == IProblem.UnusedImport || id == IProblem.NotVisibleType) {
		    int pos = curr.getSourceStart();
		    for (int k = 0; k < importsDecls.size(); k++) {
			ImportDeclaration decl = (ImportDeclaration) importsDecls.get(k);
			if (decl.getStartPosition() <= pos && pos < decl.getStartPosition() + decl.getLength()) {
			    if (existingImports.isEmpty() || !existingImports.contains(ASTNodes.asString(decl))) {
				String name = decl.getName().getFullyQualifiedName();
				if (decl.isOnDemand()) {
				    name += ".*";
				}
				removeImports.add(name);
			    }
			    break;
			}
		    }
		}
	    }
	}
	return removeImports;
    }
}
