package ai.agents;

import ai.State;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ai.Prompt.JUNIT_GENERATOR_PROMPT;

public class JunitGeneratorAgent implements NodeAction<State> {

    private static final Logger log = LoggerFactory.getLogger(JunitGeneratorAgent.class);

    interface Service {
        @SystemMessage(JUNIT_GENERATOR_PROMPT)
        String generate(@dev.langchain4j.service.UserMessage  @V("code") String code,
                        @V("dependency_analysis") String dependencyAnalysis,
                        @V("context_analysis") String contextAnalysis);
    }

    final Service service;

    public JunitGeneratorAgent(ChatLanguageModel model) {
        service = AiServices.builder(Service.class)
                .chatLanguageModel(model)
                .build();
    }

    @Override
    public Map<String, Object> apply(State state) throws Exception {
        log.info("Esecuzione JunitGeneratorAgent");
        Map<String, Object> result = new HashMap<>();

        // Otteniamo il codice e le analisi precedenti dallo stato
        String code = state.code().orElseThrow(() -> new IllegalStateException("Codice non trovato nello stato"));
        String dependencyAnalysis = state.dependencyAnalysis().orElse("Analisi dipendenze non disponibile");
        String contextAnalysis = state.instruction().orElse("Analisi contesto non disponibile");

        log.info("Generazione JUnit tests con tutte le informazioni disponibili");
        String junitTests = service.generate(code, dependencyAnalysis, contextAnalysis);

        // Verifica se il risultato contiene codice Java valido
        junitTests = ensureValidJavaCode(junitTests);

        log.info("JUnit tests generati con successo");

        // Aggiungiamo il messaggio finale con i test JUnit
        result.put("messages", AiMessage.from(junitTests));

        return result;
    }

    /**
     * Assicura che l'output sia un codice Java valido, rimuovendo eventuali blocchi di codice
     * o altri elementi non validi, e formattando i commenti correttamente.
     */
    private String ensureValidJavaCode(String code) {
        log.debug("Verifica e pulizia del codice Java generato");

        // Rimuovi eventuali blocchi di codice markdown ```java ... ```
        Pattern codeBlockPattern = Pattern.compile("```java\\s*\\n([\\s\\S]*?)```", Pattern.MULTILINE);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(code);

        if (codeBlockMatcher.find()) {
            log.debug("Trovato blocco di codice markdown, estraendo solo il codice Java");
            code = codeBlockMatcher.group(1);
        }

        // Rimuovi eventuali intestazioni o testo esplicativo all'inizio
        code = code.replaceAll("^\\s*(?:#.*|Here's the.*|The following.*)[^\\n]*\\n+", "");

        log.debug("Codice Java pulito e validato");
        return code;
    }
}