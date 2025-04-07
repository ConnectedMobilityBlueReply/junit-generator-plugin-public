package ai.tools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import dev.langchain4j.agent.tool.Tool;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencySearchTool {

    Logger log = LoggerFactory.getLogger(DependencySearchTool.class);

    private final Project project;

    public DependencySearchTool(Project project) {
        this.project = project;
    }

    @Tool(name = "analyzePomXml", value = "Finds the pom.xml file in the project, extracts its content and dependencies")
    public Map<String, Object> analyzePomXml() {
        Map<String, Object> result = new HashMap<>();
        log.info("Starting analysis of pom.xml");

        try {
            // Per la ricerca del pom.xml, avvolgiamo in una read action
            String basePath = project.getBasePath();
            if (basePath == null) {
                log.warn("Base path del progetto non trovato");
                result.put("found", false);
                result.put("message", "Base path del progetto non trovato");
                return result;
            }

            VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir == null) {
                log.warn("Base directory non trovato");
                result.put("found", false);
                result.put("message", "Base directory non trovato");
                return result;
            }

            VirtualFile pomFile = ReadAction.compute(() -> findPomXmlFile(baseDir));

            if (pomFile == null) {
                log.warn("pom.xml not found in the project");
                result.put("found", false);
                result.put("message", "pom.xml not found in the project");
                return result;
            }

            log.info("pom.xml found: {}", pomFile.getPath());

            // File found
            result.put("found", true);

            // Extract raw content - Questo può essere fatto al di fuori della read action
            String rawContent = extractPomContent(pomFile);
            result.put("rawContent", rawContent);

            // Utilizzo di Computable per le operazioni di lettura PSI
            List<Map<String, String>> dependencies = ApplicationManager.getApplication().runReadAction(
                    (Computable<List<Map<String, String>>>) () -> parseDependencies(pomFile)
            );

            Map<String, String> projectInfo = ApplicationManager.getApplication().runReadAction(
                    (Computable<Map<String, String>>) () -> parseProjectInfo(pomFile)
            );

            result.put("dependencies", dependencies);
            result.put("projectInfo", projectInfo);

            log.info("Analysis of pom.xml completed successfully");
            return result;
        } catch (Exception e) {
            log.error("Error while analyzing pom.xml", e);
            result.put("found", false);
            result.put("message", "Error while analyzing pom.xml: " + e.getMessage());
            return result;
        }
    }

    private VirtualFile findPomXmlFile(@NotNull VirtualFile directory) {
        log.debug("Searching for pom.xml in directory: {}", directory.getPath());
        Stack<VirtualFile> stack = new Stack<>();
        stack.push(directory);

        while (!stack.isEmpty()) {
            VirtualFile currentDir = stack.pop();
            VirtualFile pomFile = currentDir.findChild("pom.xml");
            if (pomFile != null && !pomFile.isDirectory()) {
                log.debug("pom.xml found in directory: {}", currentDir.getPath());
                return pomFile;
            }

            for (VirtualFile child : currentDir.getChildren()) {
                if (child.isDirectory()) {
                    stack.push(child);
                }
            }
        }

        log.debug("pom.xml not found in directory: {}", directory.getPath());
        return null;
    }

    private String extractPomContent(@NotNull VirtualFile pomFile) {
        log.debug("Extracting content from pom.xml: {}", pomFile.getPath());
        try {
            return new String(pomFile.contentsToByteArray());
        } catch (Exception e) {
            log.error("Error reading pom.xml content", e);
            return "Error reading pom.xml content: " + e.getMessage();
        }
    }

    private List<Map<String, String>> parseDependencies(@NotNull VirtualFile pomFile) {
        log.debug("Parsing dependencies from pom.xml: {}", pomFile.getPath());
        List<Map<String, String>> dependencies = new ArrayList<>();

        try {
            PsiManager psiManager = PsiManager.getInstance(project);
            XmlFile xmlFile = (XmlFile) psiManager.findFile(pomFile);

            if (xmlFile == null) {
                log.warn("Failed to parse pom.xml: PsiFile is null");
                return dependencies;
            }

            XmlTag rootTag = xmlFile.getRootTag();
            if (rootTag == null) {
                log.warn("Failed to parse pom.xml: Root tag is null");
                return dependencies;
            }

            // Find dependencies section
            XmlTag dependenciesTag = rootTag.findFirstSubTag("dependencies");
            if (dependenciesTag == null) {
                log.warn("No dependencies section found in pom.xml");
                return dependencies;
            }

            // Process each dependency
            for (XmlTag dependencyTag : dependenciesTag.findSubTags("dependency")) {
                Map<String, String> dependency = new HashMap<>();

                XmlTag groupIdTag = dependencyTag.findFirstSubTag("groupId");
                if (groupIdTag != null) {
                    dependency.put("groupId", groupIdTag.getValue().getText());
                }

                XmlTag artifactIdTag = dependencyTag.findFirstSubTag("artifactId");
                if (artifactIdTag != null) {
                    dependency.put("artifactId", artifactIdTag.getValue().getText());
                }

                XmlTag versionTag = dependencyTag.findFirstSubTag("version");
                if (versionTag != null) {
                    dependency.put("version", versionTag.getValue().getText());
                }

                XmlTag scopeTag = dependencyTag.findFirstSubTag("scope");
                if (scopeTag != null) {
                    dependency.put("scope", scopeTag.getValue().getText());
                }

                dependencies.add(dependency);
            }
        } catch (Exception e) {
            log.error("Error parsing dependencies from pom.xml", e);
        }

        return dependencies;
    }

    private Map<String, String> parseProjectInfo(@NotNull VirtualFile pomFile) {
        log.debug("Parsing project info from pom.xml: {}", pomFile.getPath());
        Map<String, String> projectInfo = new HashMap<>();

        try {
            PsiManager psiManager = PsiManager.getInstance(project);
            XmlFile xmlFile = (XmlFile) psiManager.findFile(pomFile);

            if (xmlFile == null || xmlFile.getRootTag() == null) {
                log.warn("Failed to parse project info: PsiFile or root tag is null");
                return projectInfo;
            }

            XmlTag rootTag = xmlFile.getRootTag();

            // Extract basic project information
            String[] infoTags = {"groupId", "artifactId", "version", "name", "description"};
            for (String tagName : infoTags) {
                XmlTag tag = rootTag.findFirstSubTag(tagName);
                if (tag != null) {
                    projectInfo.put(tagName, tag.getValue().getText());
                }
            }

            // Extract properties if available
            XmlTag propertiesTag = rootTag.findFirstSubTag("properties");
            if (propertiesTag != null) {
                Map<String, String> properties = new HashMap<>();
                XmlTag[] propertyTags = propertiesTag.getSubTags();
                for (XmlTag propertyTag : propertyTags) {
                    properties.put(propertyTag.getName(), propertyTag.getValue().getText());
                }
                projectInfo.put("properties", properties.toString());
            }
        } catch (Exception e) {
            log.error("Error parsing project info from pom.xml", e);
        }

        return projectInfo;
    }
}