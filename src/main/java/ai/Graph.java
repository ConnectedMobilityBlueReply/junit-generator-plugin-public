package ai;

import ai.agents.ContextAnalizerAgent;
import ai.agents.DependencyAgent;
import ai.agents.JunitGeneratorAgent;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

public class Graph {

    Logger log = LoggerFactory.getLogger(Graph.class);

    private final CompiledGraph<State> compiledGraph;

    // Lista di listener per monitorare il progresso del grafo
    private final List<Consumer<String>> progressListeners = new ArrayList<>();

    // Lista di listener per gli errori
    private final List<Consumer<GraphError>> errorListeners = new ArrayList<>();

    public Graph(ChatLanguageModel model, Project project) throws GraphStateException {
        var memory = new MemorySaver();
        try {
            ContextAnalizerAgent contextAnalyzer = new ContextAnalizerAgent(model, project);
            JunitGeneratorAgent junitGenerator = new JunitGeneratorAgent(model);
            DependencyAgent dependencyAgent = new DependencyAgent(model, project);

            // Creiamo un flusso lineare: dependency_checker -> context_analyzer -> junit_generator -> END
            StateGraph<State> workflow = new StateGraph<>(State.SCHEMA, new StateSerializer())
                    .addNode("dependency_checker", AsyncNodeAction.node_async(dependencyAgent))
                    .addNode("context_analyzer", AsyncNodeAction.node_async(contextAnalyzer))
                    .addNode("junit_generator", AsyncNodeAction.node_async(junitGenerator))
                    .addEdge(START, "dependency_checker")
                    .addEdge("dependency_checker", "context_analyzer")
                    .addEdge("context_analyzer", "junit_generator")
                    .addEdge("junit_generator", END);

            var compileConfig = CompileConfig.builder()
                    .checkpointSaver(memory)
                    .build();

            compiledGraph = workflow.compile(compileConfig);
        } catch (Exception e) {
            log.error("Errore durante l'inizializzazione del grafo", e);
            notifyErrorListeners(new GraphError("initialization", "Errore durante l'inizializzazione del grafo: " + e.getMessage(), e));
            throw e;
        }
    }

    /**
     * Classe per rappresentare un errore del grafo
     */
    public record GraphError(String phase, String message, Exception exception) {
    }

    /**
     * Aggiunge un listener per monitorare il progresso dell'esecuzione del grafo
     *
     * @param listener Consumer che riceverà il nome del nodo corrente
     */
    public void addGraphProgressListener(Consumer<String> listener) {
        progressListeners.add(listener);
    }

    /**
     * Aggiunge un listener per gli errori del grafo
     *
     * @param listener Consumer che riceverà l'errore
     */
    public void addErrorListener(Consumer<GraphError> listener) {
        errorListeners.add(listener);
    }

    /**
     * Notifica tutti i listener del progresso
     *
     * @param nodeName Nome del nodo corrente
     */
    private void notifyProgressListeners(String nodeName) {
        for (Consumer<String> listener : progressListeners) {
            try {
                listener.accept(nodeName);
            } catch (Exception e) {
                log.error("Errore durante la notifica del listener di progresso: {}", e.getMessage());
            }
        }
    }

    /**
     * Notifica tutti i listener degli errori
     *
     * @param error Errore da notificare
     */
    private void notifyErrorListeners(GraphError error) {
        for (Consumer<GraphError> listener : errorListeners) {
            try {
                listener.accept(error);
            } catch (Exception e) {
                log.error("Errore durante la notifica del listener di errore: {}", e.getMessage());
            }
        }
    }

    public String execute(String message) {
        log.info("Iniziando l'esecuzione del grafo con messaggio di lunghezza: {}", message.length());

        var runnableConfig = RunnableConfig.builder()
                .threadId(UUID.randomUUID().toString())
                .build();

        try {
            // Inizializziamo lo stato con il codice dell'utente
            Map<String, Object> initialState = Map.of("messages", UserMessage.from(message), "code", message);

            AsyncGenerator<NodeOutput<State>> result = compiledGraph.stream(initialState, runnableConfig);

            ChatMessage generation = null;
            for (NodeOutput<State> r : result) {
                String nodeName = r.node();
                log.info("Esecuzione nodo: '{}'", nodeName);

                // Notifica i listener del progresso
                notifyProgressListeners(nodeName);

                try {
                    // Per l'ultima iterazione (nodo junit_generator), otteniamo il risultato finale
                    if (nodeName.equals("junit_generator")) {
                        generation = r.state().lastMessage().orElseThrow();
                    }

                    log.info("Completato nodo '{}': {}", nodeName,
                            r.state().lastMessage().isPresent() ? r.state().lastMessage().get().type() : "no message");
                } catch (Exception e) {
                    log.error("Errore nel nodo '{}': {}", nodeName, e.getMessage());
                    notifyErrorListeners(new GraphError(nodeName, "Errore nel nodo '" + nodeName + "': " + e.getMessage(), e));
                    throw e;
                }
            }

            log.info("Esecuzione del grafo completata");

            if (generation == null) {
                String errorMsg = "Nessun risultato generato dal grafo";
                log.error(errorMsg);
                notifyErrorListeners(new GraphError("execution", errorMsg, new IllegalStateException(errorMsg)));
                return "Nessun test JUnit generato";
            }

            return switch (generation.type()) {
                case USER -> ((UserMessage) generation).singleText();
                case AI -> ((AiMessage) generation).text();
                default -> {
                    String errorMsg = "Tipo di messaggio non previsto: " + generation.type();
                    notifyErrorListeners(new GraphError("execution", errorMsg, new IllegalStateException(errorMsg)));
                    yield "Errore: " + errorMsg;
                }
            };
        } catch (Exception e) {
            log.error("Errore durante l'esecuzione del grafo", e);
            notifyErrorListeners(new GraphError("execution", "Errore durante l'esecuzione del grafo: " + e.getMessage(), e));
            return "Errore durante la generazione dei test JUnit: " + e.getMessage();
        }
    }
}