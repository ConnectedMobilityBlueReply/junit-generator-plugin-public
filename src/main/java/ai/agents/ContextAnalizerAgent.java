package ai.agents;

import ai.State;
import ai.tools.SearchContextTool;
import com.intellij.openapi.project.Project;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static ai.Prompt.CONTEXT_ANALYZER_PROMPT;

public class ContextAnalizerAgent implements NodeAction<State> {
    private static final Logger log = LoggerFactory.getLogger(ContextAnalizerAgent.class);

    interface Service {
        @SystemMessage(CONTEXT_ANALYZER_PROMPT)
        String evaluate(@dev.langchain4j.service.UserMessage @V("code") String code);
    }

    final Service service;

    public ContextAnalizerAgent(ChatLanguageModel model, Project project) {
        service = AiServices.builder(Service.class)
                .chatLanguageModel(model)
                .tools(new SearchContextTool(project))
                .build();
    }

    @Override
    public Map<String, Object> apply(State state) {
        log.info("Esecuzione ContextAnalizerAgent");
        Map<String, Object> result = new HashMap<>();

        // Otteniamo il codice dallo stato
        String code = state.code().orElseThrow(() -> new IllegalStateException("Codice non trovato nello stato"));

        // Otteniamo l'analisi delle dipendenze (se disponibile)
        String dependencyAnalysis = state.instruction().orElse("Analisi dipendenze non disponibile");

        // Eseguiamo l'analisi del contesto
        String contextAnalysis = service.evaluate(code);
        log.debug("Analisi contesto completata");

        // Preserviamo il codice originale e l'analisi delle dipendenze nello stato
        result.put("code", code);
        result.put("dependency_analysis", dependencyAnalysis);

        // Aggiungiamo il risultato dell'analisi del contesto
        result.put("context_analysis", contextAnalysis);

        // Aggiungiamo un messaggio per aggiornare lo stato
        result.put("messages", AiMessage.from(contextAnalysis));

        return result;
    }
}