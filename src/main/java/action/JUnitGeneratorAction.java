package action;

import ai.Graph;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.InfoWindowPanel;
import ui.InfoWindowPanel.StatusType;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

public class JUnitGeneratorAction extends AnAction {

    private static final Logger log = LoggerFactory.getLogger(JUnitGeneratorAction.class);

    private InfoWindowPanel infoPanel;
    private ChatLanguageModel model;

    // ID univoci per gli elementi UI dedicati alle diverse fasi del processo
    private static final String DEPENDENCY_PHASE_ID = "dependency_analysis";
    private static final String CONTEXT_PHASE_ID = "context_analysis";
    private static final String JUNIT_PHASE_ID = "junit_generation";

    // Costruttore predefinito senza parametri
    public JUnitGeneratorAction() {
        super();
        log.info("Inizializzazione JUnitGeneratorAction");
        initChatModel();
    }

    // Specifica che update dovrebbe essere eseguito in background
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        log.info("Azione JUnitGeneratorAction avviata");
        Project currentProject = event.getProject();
        if (currentProject == null) {
            log.error("Progetto non trovato nell'AnActionEvent");
            return;
        }
        log.debug("Progetto: {}", currentProject.getName());

        // Ottieni il file selezionato prima di operazioni asincrone
        final VirtualFile selectedFile = event.getData(CommonDataKeys.VIRTUAL_FILE);

        // Usa un riferimento atomico per la finestra degli strumenti
        AtomicReference<ToolWindow> toolWindowRef = new AtomicReference<>();

        // Assicurati che la finestra degli strumenti sia visibile
        ToolWindow toolWindow = ToolWindowManager.getInstance(currentProject)
                .getToolWindow("JUnitGenerator"); // ID corrispondente a quello definito in plugin.xml

        toolWindowRef.set(toolWindow);

        if (toolWindow == null) {
            log.error("ToolWindow 'JUnitGenerator' non trovata");
            return;
        }

        // Attiva e mostra la toolWindow prima di procedere
        ApplicationManager.getApplication().invokeLater(() -> {
            // Assicurati che la toolWindow sia visibile
            if (!toolWindow.isVisible()) {
                toolWindow.show(() -> {
                    processFileAfterToolWindowActivation(currentProject, toolWindow, selectedFile);
                });
            } else {
                // La toolWindow è già visibile, attivala
                toolWindow.activate(() -> {
                    processFileAfterToolWindowActivation(currentProject, toolWindow, selectedFile);
                }, true);
            }
        });
    }

    /**
     * Processa il file dopo che la toolWindow è stata attivata
     */
    private void processFileAfterToolWindowActivation(Project currentProject, ToolWindow toolWindow, VirtualFile selectedFile) {
        ApplicationManager.getApplication().invokeLater(() -> {
            // Aggiorna o crea il pannello info
            infoPanel = createNewInfoPanel(currentProject, toolWindow);
            log.debug("InfoWindowPanel ottenuto/creato: {}", "sì");

            if (infoPanel == null) {
                log.error("Impossibile ottenere/creare InfoWindowPanel");
                return;
            }

            // Verifica che il file sia valido
            if (selectedFile == null) {
                log.error("Nessun file selezionato");
                infoPanel.addNewItem("junit_error", StatusType.ERROR, "No file selected!", null, true);
                refreshUI();
                return;
            }
            log.debug("File selezionato: {}", selectedFile.getPath());

            // Verifica che sia un file Java
            if (!selectedFile.isDirectory() && "java".equals(selectedFile.getExtension())) {
                log.info("Elaborazione del file Java: {}", selectedFile.getName());
                // Pulisce completamente il pannello prima di iniziare la generazione dei test
                infoPanel.clearAll();
                processJavaFileForJUnit(selectedFile, currentProject);
            } else {
                log.error("Il file selezionato non è un file Java: {}", selectedFile.getName());
                infoPanel.addNewItem("junit_error", StatusType.ERROR,
                        "Only Java files can be processed for JUnit test generation", null, true);
                refreshUI();
            }
        });
    }

    /**
     * Crea un nuovo pannello info e lo aggiunge alla toolWindow
     */
    private InfoWindowPanel createNewInfoPanel(Project project, ToolWindow toolWindow) {
        log.debug("Creazione nuovo InfoWindowPanel");

        // Crea un nuovo pannello
        InfoWindowPanel panel = new InfoWindowPanel();

        // Usa la versione non deprecata di ContentFactory
        Content content = ContentFactory.getInstance().createContent(panel, "JUnit Tests", false);

        // Rimuovi eventuali contenuti esistenti
        toolWindow.getContentManager().removeAllContents(true);

        // Aggiungi il nuovo contenuto
        toolWindow.getContentManager().addContent(content);
        toolWindow.getContentManager().setSelectedContent(content);

        log.debug("Nuovo InfoWindowPanel creato e aggiunto alla toolWindow");
        return panel;
    }

    /**
     * Forza l'aggiornamento dell'UI
     */
    private void refreshUI() {
        if (infoPanel != null) {
            ApplicationManager.getApplication().invokeLater(() -> {
                infoPanel.revalidate();
                infoPanel.repaint();

                // Forza anche l'aggiornamento del parent per maggiore sicurezza
                Container parent = infoPanel.getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint();

                    // Aggiorna anche il parent del parent
                    Container grandParent = parent.getParent();
                    if (grandParent != null) {
                        grandParent.revalidate();
                        grandParent.repaint();
                    }

                    log.debug("UI aggiornata a tutti i livelli");
                }
            });
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Verifica se c'è un progetto aperto
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // Ottieni il file selezionato
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        // Mostra l'opzione solo se è un file Java (non una directory)
        boolean isJavaFile = !selectedFile.isDirectory() && "java".equals(selectedFile.getExtension());
        e.getPresentation().setEnabledAndVisible(isJavaFile);

        if (isJavaFile) {
            log.trace("Azione abilitata per file: {}", selectedFile.getPath());
        }
    }

    /**
     * Processa un file Java per generare test JUnit
     */
    void processJavaFileForJUnit(VirtualFile file, Project project) {
        // ID univoco per questo file
        final String fileId = "junit_" + file.getName();
        log.info("Avvio elaborazione per generazione JUnit, file ID: {}", fileId);

        // Aggiungi un elemento per il file principale (assicurandoci che sia sulla EDT)
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                infoPanel.addNewItem(fileId, StatusType.LOADING,
                        "Analyzing " + file.getName() + " for JUnit test generation", null, true);

                // Aggiungi elementi UI per le diverse fasi del processo
                infoPanel.addNewItem(DEPENDENCY_PHASE_ID, StatusType.LOADING,
                        "Dependency analysis - Analyzing project dependencies...",
                        "Looking for JUnit, Mockito, and other testing libraries", false);

                infoPanel.addNewItem(CONTEXT_PHASE_ID, StatusType.WAITING,
                        "Context analysis - Waiting...",
                        "Will analyze class structure and relationships", false);

                infoPanel.addNewItem(JUNIT_PHASE_ID, StatusType.WAITING,
                        "JUnit generation - Waiting...",
                        "Will generate test cases based on analysis", false);

                refreshUI();
                log.debug("Elementi di stato aggiunti al panel");
            } catch (Exception e) {
                log.error("Errore durante l'aggiunta dell'elemento al panel", e);
            }
        });

        // Avvia un thread separato per non bloccare l'UI
        Thread thread = new Thread(() -> {
            try {
                log.debug("Thread di elaborazione avviato per {}", file.getName());

                // Aggiorna lo stato iniziale
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        infoPanel.updateItemText(fileId, "Reading " + file.getName());
                        refreshUI();
                        log.debug("Aggiornato testo elemento a 'Reading {}'", file.getName());
                    } catch (Exception e) {
                        log.error("Errore durante l'aggiornamento dell'elemento", e);
                    }
                });

                // Leggi il contenuto del file
                log.debug("Lettura del contenuto del file: {}", file.getPath());
                String fileContent = getFileContent(file);
                log.debug("Contenuto letto, dimensione: {} caratteri", fileContent.length());

                // Verifica che il file sia valido
                if (fileContent.isEmpty()) {
                    log.error("Contenuto del file vuoto: {}", file.getPath());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Error: Empty file or could not read content");

                            // Aggiorna stati delle fasi
                            infoPanel.updateItemStatus(DEPENDENCY_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemStatus(CONTEXT_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemStatus(JUNIT_PHASE_ID, StatusType.ERROR);

                            refreshUI();
                        } catch (Exception e) {
                            log.error("Errore durante l'aggiornamento dello stato dell'elemento", e);
                        }
                    });
                    return;
                }

                // Aggiorna con un messaggio intermedio
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        infoPanel.updateItemText(fileId, "Analyzing " + file.getName());
                        refreshUI();
                    } catch (Exception e) {
                        log.error("Errore durante l'aggiornamento dell'elemento", e);
                    }
                });

                // Crea il workflow per la generazione dei test con il listener di progresso
                log.info("Creazione workflow per generazione test");


                // Crea un'istanza del grafo con il listener per gli eventi
                Graph workflow;
                try {
                    workflow = new Graph(model, project);
                } catch (Exception e) {
                    handleGraphError("Graph initialization error", "Failed to initialize test generation workflow", e);
                    return;
                }

                // Aggiungi un observer per monitorare le transizioni del grafo
                workflow.addGraphProgressListener(node -> {
                    String nodeName = node.toLowerCase();
                    log.info("Workflow transitioned to node: {}", nodeName);

                    if (nodeName.contains("dependency")) {
                        updatePhaseStatus(DEPENDENCY_PHASE_ID, "Analyzing project dependencies...", StatusType.LOADING);
                        updatePhaseStatus(CONTEXT_PHASE_ID, "Context analysis - Waiting...", StatusType.WAITING);
                        updatePhaseStatus(JUNIT_PHASE_ID, "JUnit generation - Waiting...", StatusType.WAITING);
                    }
                    else if (nodeName.contains("context")) {
                        updatePhaseStatus(DEPENDENCY_PHASE_ID, "Dependency analysis - Completed", StatusType.SUCCESS);
                        updatePhaseStatus(CONTEXT_PHASE_ID, "Analyzing class structure and relationships...", StatusType.LOADING);
                        updatePhaseStatus(JUNIT_PHASE_ID, "JUnit generation - Waiting...", StatusType.WAITING);
                    }
                    else if (nodeName.contains("junit")) {
                        updatePhaseStatus(DEPENDENCY_PHASE_ID, "Dependency analysis - Completed", StatusType.SUCCESS);
                        updatePhaseStatus(CONTEXT_PHASE_ID, "Context analysis - Completed", StatusType.SUCCESS);
                        updatePhaseStatus(JUNIT_PHASE_ID, "Generating JUnit tests...", StatusType.LOADING);
                    }
                });

                // Aggiungi un listener per gli errori del grafo
                workflow.addErrorListener(error -> {
                    String phase = error.phase();
                    String errorMessage = error.message();
                    log.error("Graph error in phase {}: {}", phase, errorMessage, error.exception());

                    // Determina la fase in cui si è verificato l'errore
                    if (phase.contains("dependency") || phase.equals("dependency_checker")) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            infoPanel.updateItemStatus(DEPENDENCY_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemText(DEPENDENCY_PHASE_ID, "Dependency analysis - Failed: " + errorMessage);
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Error in dependency analysis: " + errorMessage);
                            refreshUI();
                        });
                    }
                    else if (phase.contains("context") || phase.equals("context_analyzer")) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            infoPanel.updateItemStatus(CONTEXT_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemText(CONTEXT_PHASE_ID, "Context analysis - Failed: " + errorMessage);
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Error in context analysis: " + errorMessage);
                            refreshUI();
                        });
                    }
                    else if (phase.contains("junit") || phase.equals("junit_generator")) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            infoPanel.updateItemStatus(JUNIT_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemText(JUNIT_PHASE_ID, "JUnit generation - Failed: " + errorMessage);
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Error in JUnit generation: " + errorMessage);
                            refreshUI();
                        });
                    }
                    else {
                        // Errore generico o di inizializzazione
                        ApplicationManager.getApplication().invokeLater(() -> {
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Error in test generation: " + errorMessage);
                            refreshUI();
                        });
                    }
                });

                // Aggiorna lo stato
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        infoPanel.updateItemText(fileId, "Generating JUnit tests for " + file.getName() + "...");
                        infoPanel.updateItemText(DEPENDENCY_PHASE_ID, "Starting dependency analysis...");
                        refreshUI();
                    } catch (Exception e) {
                        log.error("Errore durante l'aggiornamento dell'elemento", e);
                    }
                });

                // Esegui il workflow per generare i test
                log.info("Esecuzione workflow...");
                String generatedTests;
                try {
                    generatedTests = workflow.execute(fileContent);
                    log.debug("Test generati, dimensione: {} caratteri", generatedTests.length());
                } catch (Exception e) {
                    handleGraphError("Execution error", "Error executing test generation workflow", e);
                    return;
                }

                if (generatedTests.isEmpty() || generatedTests.contains("Nessun test JUnit generato") || generatedTests.startsWith("Errore")) {
                    log.error("Generazione test fallita per: {}", file.getName());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Failed to generate tests for " + file.getName() +
                                    (generatedTests.startsWith("Errore") ? ": " + generatedTests : ""));

                            // Aggiorna stati delle fasi
                            infoPanel.updateItemStatus(JUNIT_PHASE_ID, StatusType.ERROR);
                            infoPanel.updateItemText(JUNIT_PHASE_ID, "JUnit generation - Failed");

                            refreshUI();
                        } catch (Exception e) {
                            log.error("Errore durante l'aggiornamento dello stato dell'elemento", e);
                        }
                    });
                    return;
                }

                // Aggiorna lo stato delle fasi finali
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        infoPanel.updateItemStatus(DEPENDENCY_PHASE_ID, StatusType.SUCCESS);
                        infoPanel.updateItemText(DEPENDENCY_PHASE_ID, "Dependency analysis - Completed");

                        infoPanel.updateItemStatus(CONTEXT_PHASE_ID, StatusType.SUCCESS);
                        infoPanel.updateItemText(CONTEXT_PHASE_ID, "Context analysis - Completed");

                        infoPanel.updateItemStatus(JUNIT_PHASE_ID, StatusType.SUCCESS);
                        infoPanel.updateItemText(JUNIT_PHASE_ID, "JUnit generation - Completed");

                        infoPanel.updateItemText(fileId, "Creating test file for " + file.getName());
                        refreshUI();
                    } catch (Exception e) {
                        log.error("Errore durante l'aggiornamento dell'elemento", e);
                    }
                });

                // Crea il file di test
                log.info("Creazione file di test per: {}", file.getName());
                final TestFileInfo testFileInfo;
                try {
                    testFileInfo = createTestFile(project, file, generatedTests);
                } catch (Exception e) {
                    handleGraphError("File creation error", "Error creating test file", e);
                    return;
                }

                if (testFileInfo == null) {
                    log.error("Creazione file di test fallita per: {}", file.getName());
                    ApplicationManager.getApplication().invokeLater(() -> {
                        try {
                            infoPanel.updateItemStatus(fileId, StatusType.ERROR);
                            infoPanel.updateItemText(fileId, "Failed to create test file for " + file.getName());
                            refreshUI();
                        } catch (Exception e) {
                            log.error("Errore durante l'aggiornamento dello stato dell'elemento", e);
                        }
                    });
                    return;
                }

                log.info("File di test creato con successo: {}", testFileInfo.filePath);

                // Finalizza con successo
                ApplicationManager.getApplication().invokeLater(() -> {
                    try {
                        infoPanel.updateItemStatus(fileId, StatusType.SUCCESS);
                        infoPanel.updateItemText(fileId, "JUnit tests generated: " + testFileInfo.className);

                        // Aggiungi un pulsante per visualizzare i test
                        log.debug("Aggiunta pulsante 'View Tests'");
                        infoPanel.addButtonToItem(fileId, "View Tests", actionEvent -> {
                            log.debug("Pulsante 'View Tests' cliccato, apertura file: {}", testFileInfo.filePath);
                            openTestFile(project, testFileInfo.filePath);
                        });

                        refreshUI();
                    } catch (Exception e) {
                        log.error("Errore durante l'aggiornamento finale dell'elemento", e);
                    }
                });

            } catch (Exception e) {
                // Gestisce gli errori generali
                handleGraphError("General error", "Error generating JUnit tests", e);
            }
        });
        thread.start();
        log.debug("Thread di elaborazione avviato per generazione JUnit test");
    }

    /**
     * Gestisce un errore del grafo e lo visualizza nel pannello
     */
    private void handleGraphError(String errorType, String errorDescription, Exception e) {
        log.error("{}: {}", errorType, errorDescription, e);
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // Aggiungi un elemento di errore specifico per questo errore
                String errorId = "error_" + System.currentTimeMillis();
                infoPanel.addNewItem(errorId, StatusType.ERROR,
                        errorDescription,
                        "Error details: " + e.getMessage(), true);

                // Aggiorna lo stato delle fasi
                updatePhaseStatus(DEPENDENCY_PHASE_ID, "Dependency analysis - Error occurred", StatusType.ERROR);
                updatePhaseStatus(CONTEXT_PHASE_ID, "Context analysis - Error occurred", StatusType.ERROR);
                updatePhaseStatus(JUNIT_PHASE_ID, "JUnit generation - Error occurred", StatusType.ERROR);

                refreshUI();
            } catch (Exception ex) {
                log.error("Errore durante la visualizzazione dell'errore nel pannello", ex);
            }
        });
    }

    /**
     * Aggiorna lo stato di una fase nel pannello
     */
    private void updatePhaseStatus(String phaseId, String message, StatusType status) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                infoPanel.updateItemStatus(phaseId, status);
                infoPanel.updateItemText(phaseId, message);
                refreshUI();
            } catch (Exception e) {
                log.error("Errore durante l'aggiornamento della fase {}: {}", phaseId, e.getMessage());
            }
        });
    }

    /**
     * Classe per tenere traccia delle informazioni sul file di test
     */
    public record TestFileInfo(String className, String filePath) {
    }

    /**
     * Crea il file di test e restituisce informazioni su di esso
     */
    TestFileInfo createTestFile(Project project, VirtualFile sourceFile, String testCode) {
        log.debug("Inizio creazione file di test per: {}", sourceFile.getPath());
        try {
            // Ottieni informazioni sulla classe dal file sorgente
            PsiFile psiFile = ApplicationManager.getApplication().runReadAction(
                    (Computable<PsiFile>) () -> PsiManager.getInstance(project).findFile(sourceFile)
            );

            if (!(psiFile instanceof PsiJavaFile javaFile)) {
                log.error("Il file non è un PsiJavaFile valido: {}", sourceFile.getPath());
                return null;
            }

            // Esegui queste operazioni all'interno di una ReadAction
            String className = ApplicationManager.getApplication().runReadAction(
                    (Computable<String>) () -> {
                        PsiClass[] classes = javaFile.getClasses();
                        if (classes.length == 0) {
                            return null;
                        }
                        return classes[0].getName();
                    }
            );

            if (className == null) {
                log.error("Nessuna classe trovata nel file: {}", sourceFile.getPath());
                return null;
            }

            String packageName = ApplicationManager.getApplication().runReadAction(
                    (Computable<String>) javaFile::getPackageName
            );

            String testClassName = className + "Test";

            log.debug("Classe: {}, Package: {}, TestClassName: {}", className, packageName, testClassName);

            // Determina il percorso base del progetto
            String projectBasePath = project.getBasePath();
            if (projectBasePath == null) {
                log.error("Impossibile determinare il percorso base del progetto");
                return null;
            }

            // Crea il percorso per il file di test in src/test/java
            String testPath = projectBasePath + "/src/test/java/";

            // Aggiungi il percorso del package
            if (packageName != null && !packageName.isEmpty()) {
                testPath += packageName.replace('.', '/') + "/";
            }

            // Aggiungi il nome del file con suffisso Test
            testPath += testClassName + ".java";

            log.debug("Percorso file di test: {}", testPath);

            // Crea le directory necessarie
            File testFile = new File(testPath);
            File parentDir = testFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                log.debug("Creazione directory per test: {}", parentDir.getAbsolutePath());
                boolean dirCreated = parentDir.mkdirs();
                if (!dirCreated) {
                    log.warn("Impossibile creare directory per test: {}", parentDir.getAbsolutePath());
                }
            }

            // Scrivi il contenuto nel file
            log.debug("Scrittura contenuto in: {}", testPath);
            Files.writeString(Paths.get(testPath), testCode);
            log.info("File di test scritto con successo: {}", testPath);

            // Aggiorna il filesystem di IntelliJ
            VirtualFile testVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(testPath);
            if (testVirtualFile != null) {
                log.debug("File virtuale trovato nel filesystem di IntelliJ: {}", testVirtualFile.getPath());
            } else {
                log.warn("File virtuale non trovato, potrebbe essere necessario un refresh manuale");
            }

            return new TestFileInfo(testClassName, testPath);

        } catch (Exception e) {
            log.error("Errore durante la creazione del file di test", e);
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * Apre il file di test nell'editor
     */
    void openTestFile(Project project, String filePath) {
        log.debug("Tentativo di apertura file: {}", filePath);
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile testFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
            if (testFile != null) {
                log.info("Apertura file nell'editor: {}", testFile.getPath());
                FileEditorManager.getInstance(project).openFile(testFile, true);
            } else {
                log.error("Impossibile trovare il file: {}", filePath);
            }
        });
    }

    /**
     * Legge il contenuto di un file
     */
    private String getFileContent(VirtualFile file) {
        try {
            log.debug("Lettura contenuto da: {}", file.getPath());
            String content = new String(file.contentsToByteArray(), file.getCharset());
            log.trace("Contenuto letto, dimensione: {} caratteri", content.length());
            return content;
        } catch (IOException e) {
            log.error("Errore durante la lettura del file: {}", file.getPath(), e);
            log.error(e.getMessage());
            return "";
        }
    }

    /**
     * Inizializza il modello di chat
     */
    private void initChatModel() {
        String apiKey = "";
        log.debug("Inizializzazione modello di chat");
        try {
            this.model = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .logResponses(true)
                    .modelName("gpt-4o")
                    .maxRetries(2)
                    .build();
            log.info("Modello di chat inizializzato con successo");
        } catch (Exception e) {
            log.error("Errore durante l'inizializzazione del modello di chat", e);
        }
    }
}