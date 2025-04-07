package ai.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchContextTool {

    private final Project project;

    public SearchContextTool(Project project) {
        this.project = project;
    }

    @Tool("Cerca tutti i file Java nel progetto con il nome specificato")
    public List<String> findJavaFilesByName(String fileName) {
        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            List<String> results = new ArrayList<>();

            Collection<VirtualFile> virtualFiles = FilenameIndex.getAllFilesByExt(
                    project,
                    "java",
                    GlobalSearchScope.projectScope(project)
            );

            for (VirtualFile virtualFile : virtualFiles) {
                if (virtualFile.getName().equals(fileName + ".java")) {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile instanceof PsiJavaFile) {
                        results.add(virtualFile.getPath());
                    }
                }
            }

            return results;
        });
    }

    @Tool("Cerca classi Java nel progetto che contengono la stringa specificata nel loro nome - Approccio basato su file")
    public List<String> findClassesByNamePattern(String pattern) {
        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            List<String> results = new ArrayList<>();

            // Ottieni tutte le radici del contenuto del progetto
            VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();

            // Visita tutti i file Java nelle radici del progetto
            for (VirtualFile root : roots) {
                collectMatchingClasses(root, pattern, results);
            }

            return results;
        });
    }

    private void collectMatchingClasses(VirtualFile file, String pattern, List<String> results) {
        if (file.isDirectory()) {
            // Utilizza una pila per evitare la ricorsione
            Stack<VirtualFile> stack = new Stack<>();
            stack.push(file);

            while (!stack.isEmpty()) {
                VirtualFile currentFile = stack.pop();
                if (currentFile.isDirectory()) {
                    for (VirtualFile child : currentFile.getChildren()) {
                        stack.push(child);
                    }
                } else if (currentFile.getName().endsWith(".java")) {
                    processJavaFile(currentFile, pattern, results);
                }
            }
        } else if (file.getName().endsWith(".java")) {
            processJavaFile(file, pattern, results);
        }
    }

    private void processJavaFile(VirtualFile file, String pattern, List<String> results) {
        ApplicationManager.getApplication().runReadAction((Computable<Object>) () -> {
            try {
                String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);

                // Pattern per trovare la dichiarazione del pacchetto
                Pattern packagePattern = Pattern.compile("package\\s+([\\w.]+)");
                Matcher packageMatcher = packagePattern.matcher(content);
                String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";

                // Pattern per trovare dichiarazioni di classe
                Pattern classPattern = Pattern.compile("class\\s+(\\w+)");
                Matcher classMatcher = classPattern.matcher(content);

                while (classMatcher.find()) {
                    String className = classMatcher.group(1);

                    // Se il nome della classe contiene il pattern, aggiungi il nome completo
                    if (className.contains(pattern)) {
                        String qualifiedName = packageName.isEmpty() ?
                                className :
                                packageName + "." + className;
                        results.add(qualifiedName);
                    }
                }
            } catch (IOException e) {
                // Gestione errori di lettura file
            }
            return null;
        });
    }

    @Tool("Ottiene il contenuto di un file Java specificato dal percorso")
    public String getJavaFileContent(String filePath) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            try {
                String basePath = project.getBasePath();
                if (basePath == null) {
                    return "Base path del progetto non trovato";
                }

                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(basePath + "/" + filePath);
                if (virtualFile == null) {
                    return "File non trovato: " + filePath;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile instanceof PsiJavaFile) {
                    return psiFile.getText();
                } else {
                    return "Il file non è un file Java valido";
                }
            } catch (Exception e) {
                return "Errore durante la lettura del file: " + e.getMessage();
            }
        });
    }

    @Tool("Cerca metodi in una classe Java specificata dal nome completo")
    public List<String> findMethodsInClass(String classQualifiedName) {
        // Esegui tutto il codice all'interno di una ReadAction
        return ApplicationManager.getApplication().runReadAction((Computable<List<String>>) () -> {
            List<String> methods = new ArrayList<>();

            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(classQualifiedName, GlobalSearchScope.projectScope(project));

            if (psiClass != null) {
                PsiMethod[] psiMethods = psiClass.getMethods();

                for (PsiMethod method : psiMethods) {
                    StringBuilder methodSignature = new StringBuilder();
                    methodSignature.append(method.getName()).append("(");

                    PsiElement[] parameters = method.getParameterList().getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        PsiElement param = parameters[i];
                        if (param instanceof com.intellij.psi.PsiParameter parameter) {
                            methodSignature.append(parameter.getType().getPresentableText())
                                    .append(" ")
                                    .append(parameter.getName());

                            if (i < parameters.length - 1) {
                                methodSignature.append(", ");
                            }
                        }
                    }

                    methodSignature.append(")");
                    methods.add(methodSignature.toString());
                }
            }

            return methods;
        });
    }
}