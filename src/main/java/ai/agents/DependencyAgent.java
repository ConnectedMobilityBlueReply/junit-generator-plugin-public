package ai.agents;

import ai.State;
import ai.tools.DependencySearchTool;
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

import static ai.Prompt.DEPENDENCY_PROMPT;

public class DependencyAgent implements NodeAction<State> {
    private static final Logger log = LoggerFactory.getLogger(DependencyAgent.class);

    interface Service {
        @SystemMessage(DEPENDENCY_PROMPT)
        String searchDependency(@dev.langchain4j.service.UserMessage  @V("code") String code);
    }

    final Service service;

    public DependencyAgent(ChatLanguageModel model, Project project) {
        service = AiServices.builder(Service.class)
                .chatLanguageModel(model)
                .tools(new DependencySearchTool(project))
                .build();
    }

    @Override
    public Map<String, Object> apply(State state) {
        log.info("Esecuzione DependencyAgent");
        Map<String, Object> result = new HashMap<>();

        // Otteniamo il codice dallo stato
        String code = state.code().orElseThrow(() -> new IllegalStateException("Codice non trovato nello stato"));

        // Analizziamo le dipendenze
        String analysisResult = service.searchDependency(code);
        log.debug("Analisi dipendenze completata");

        // Preserviamo il codice originale nello stato
        result.put("code", code);

        // Aggiungiamo il risultato dell'analisi
        result.put("instruction", analysisResult);

        // Aggiungiamo un messaggio per aggiornare lo stato
        result.put("messages", AiMessage.from(analysisResult));

        return result;
    }
}