package ui.factory;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import ui.InfoWindowPanel;

public class InfoWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        InfoWindowPanel panel = new InfoWindowPanel();

        // Aggiungiamo un messaggio di benvenuto
        panel.addNewItem("welcome_message", InfoWindowPanel.StatusType.SUCCESS,
                "Ready to generate JUnit tests!",
                "Right-click on a Java file and select 'Generate JUnit Tests' to start", true);

        // Aggiungiamo una nota informativa sulle funzionalità
        panel.addNewItem("info_note", InfoWindowPanel.StatusType.WAITING,
                "This tool analyzes your code and generates comprehensive JUnit tests",
                "Uses AI to understand code structure, dependencies, and edge cases", false);

        toolWindow.setTitle("JUnit Generator");

        Content content = ContentFactory.getInstance().createContent(panel, "JUnit Tests", false);
        toolWindow.getContentManager().addContent(content);
    }
}
