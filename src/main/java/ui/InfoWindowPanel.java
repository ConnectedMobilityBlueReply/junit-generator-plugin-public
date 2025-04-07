package ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InfoWindowPanel extends JBPanel<InfoWindowPanel> {
    private static final Logger logger = LoggerFactory.getLogger(InfoWindowPanel.class);

    // Icone statiche
    private static final Icon ERROR_ICON = UIUtil.getBalloonErrorIcon();
    private static final Icon SUCCESS_ICON = AllIcons.Actions.Checked;
    private static final Icon WARNING_ICON = UIUtil.getBalloonWarningIcon();
    private static final Icon WAITING_ICON = AllIcons.Actions.Pause;
    private static final Icon[] LOADING_ICONS = {
            AllIcons.Process.Step_1, AllIcons.Process.Step_2, AllIcons.Process.Step_3,
            AllIcons.Process.Step_4, AllIcons.Process.Step_5, AllIcons.Process.Step_6,
            AllIcons.Process.Step_7, AllIcons.Process.Step_8
    };

    // Enum per i tipi di stato
    public enum StatusType {
        LOADING, SUCCESS, ERROR, WARNING, WAITING
    }

    private final JPanel contentPanel;
    private int loadingIconIndex = 0;
    private Timer loadingTimer;
    private final List<JLabel> loadingLabels = new ArrayList<>();

    // Mappa per tenere traccia di tutti gli elementi per ID
    private final Map<String, JLabel> iconLabelMap = new HashMap<>();
    private final Map<String, JLabel> textLabelMap = new HashMap<>();
    private final Map<String, JPanel> itemPanelMap = new HashMap<>();

    public InfoWindowPanel() {
        logger.debug("Initializing InfoWindowPanel");
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(10));

        contentPanel = new JBPanel<>();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JPanel mainPanel = new JBPanel<>(new BorderLayout());
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.NORTH);

        logger.info("InfoWindowPanel initialized");
        startLoadingAnimation();
    }

    private void startLoadingAnimation() {
        logger.debug("Starting loading animation timer");
        loadingTimer = new Timer(125, e -> {
            loadingIconIndex = (loadingIconIndex + 1) % LOADING_ICONS.length;
            updateLoadingIcons();
        });
        loadingTimer.start();
        logger.debug("Animation timer started with {} loading labels", loadingLabels.size());
    }

    private void updateLoadingIcons() {
        // Aggiorna tutte le icone di caricamento tenute traccia nella lista
        for (JLabel iconLabel : loadingLabels) {
            iconLabel.setIcon(LOADING_ICONS[loadingIconIndex]);
        }
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /**
     * Crea un elemento di stato con un ID per il riferimento futuro
     */
    public void createStatusItemWithId(String id, Icon icon, String text, String description, boolean isBold, JPanel container) {
        logger.debug("Creating status item with id='{}', text='{}', description='{}'", id, text, description);

        JPanel itemPanel = new JBPanel<>(new BorderLayout(10, 0));
        itemPanel.setBorder(JBUI.Borders.empty(3, 0));
        itemPanel.setOpaque(false);

        JLabel iconLabel = new JLabel(icon);
        JLabel textLabel = new JBLabel(text);

        if (isBold) {
            textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));
        }

        itemPanel.add(iconLabel, BorderLayout.WEST);
        itemPanel.add(textLabel, BorderLayout.CENTER);

        if (description != null && !description.isEmpty()) {
            JLabel descLabel = new JBLabel(description);
            descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
            descLabel.setForeground(JBColor.GRAY);

            JPanel textPanel = new JBPanel<>(new BorderLayout());
            textPanel.setOpaque(false);
            textPanel.add(textLabel, BorderLayout.NORTH);
            textPanel.add(descLabel, BorderLayout.CENTER);

            itemPanel.add(textPanel, BorderLayout.CENTER);
        }

        container.add(itemPanel);

        // Memorizza i label nella mappa per il riferimento futuro
        iconLabelMap.put(id, iconLabel);
        textLabelMap.put(id, textLabel);
        itemPanelMap.put(id, itemPanel);

        // Se è un'icona di caricamento, aggiungila alla lista loadingLabels
        if (isLoadingIcon(icon)) {
            logger.trace("Added loading icon for item id='{}'", id);
            loadingLabels.add(iconLabel);
        }
    }

    /**
     * Verifica se un'icona è una delle icone di caricamento
     */
    private boolean isLoadingIcon(Icon icon) {
        for (Icon loadingIcon : LOADING_ICONS) {
            if (loadingIcon == icon) {
                return true;
            }
        }
        return false;
    }

    /**
     * Aggiorna lo stato di un elemento tramite il suo ID
     */
    public void updateItemStatus(String id, StatusType statusType) {
        logger.debug("Updating item status: id='{}', new status='{}'", id, statusType);
        JLabel iconLabel = iconLabelMap.get(id);
        if (iconLabel != null) {
            // Rimuovi dalla lista delle icone di caricamento se necessario
            if (loadingLabels.contains(iconLabel) && statusType != StatusType.LOADING) {
                logger.trace("Removing item id='{}' from loading labels", id);
                loadingLabels.remove(iconLabel);
            }

            // Imposta l'icona appropriata in base al tipo di stato
            switch (statusType) {
                case LOADING:
                    iconLabel.setIcon(LOADING_ICONS[loadingIconIndex]);
                    if (!loadingLabels.contains(iconLabel)) {
                        logger.trace("Adding item id='{}' to loading labels", id);
                        loadingLabels.add(iconLabel);
                    }
                    break;
                case SUCCESS:
                    iconLabel.setIcon(SUCCESS_ICON);
                    break;
                case ERROR:
                    iconLabel.setIcon(ERROR_ICON);
                    break;
                case WARNING:
                    iconLabel.setIcon(WARNING_ICON);
                    break;
                case WAITING:
                    iconLabel.setIcon(WAITING_ICON);
                    break;
            }

            // Aggiorna l'interfaccia
            iconLabel.getParent().revalidate();
            iconLabel.getParent().repaint();
        } else {
            logger.warn("Could not update status for item with id='{}' - item not found", id);
        }
    }

    /**
     * Aggiorna il testo di un elemento tramite il suo ID
     */
    public void updateItemText(String id, String newText) {
        logger.debug("Updating item text: id='{}', new text='{}'", id, newText);
        JLabel textLabel = textLabelMap.get(id);
        if (textLabel != null) {
            textLabel.setText(newText);
            textLabel.getParent().revalidate();
            textLabel.getParent().repaint();
        } else {
            logger.warn("Could not update text for item with id='{}' - item not found", id);
        }
    }

    /**
     * Aggiunge un nuovo elemento con un ID
     */
    public void addNewItem(String id, StatusType statusType, String text, String description, boolean isBold) {
        logger.info("Adding new item: id='{}', status='{}', text='{}'", id, statusType, text);

        if (iconLabelMap.containsKey(id)) {
            logger.warn("Item with id='{}' already exists - will be overwritten", id);
        }

        Icon icon = switch (statusType) {
            case LOADING -> LOADING_ICONS[loadingIconIndex];
            case ERROR -> ERROR_ICON;
            case WARNING -> WARNING_ICON;
            case WAITING -> WAITING_ICON;
            default -> SUCCESS_ICON;
        };

        createStatusItemWithId(id, icon, text, description, isBold, contentPanel);
    }

    /**
     * Aggiunge un pulsante a un elemento esistente tramite il suo ID
     */
    public void addButtonToItem(String id, String buttonText, ActionListener actionListener) {
        logger.debug("Adding button '{}' to item with id='{}'", buttonText, id);
        JPanel itemPanel = itemPanelMap.get(id);

        if (itemPanel != null) {
            // Crea il pulsante
            JButton button = new JButton(buttonText);
            button.addActionListener(actionListener);

            // Impostazioni di stile
            button.setFocusPainted(false);
            button.setBorder(JBUI.Borders.empty(2, 8));

            // Crea un pannello per contenere il pulsante (allineato a destra)
            JPanel buttonPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.setOpaque(false);
            buttonPanel.add(button);

            // Aggiungi il pannello del pulsante all'elemento
            itemPanel.add(buttonPanel, BorderLayout.EAST);

            // Aggiorna l'interfaccia
            itemPanel.revalidate();
            itemPanel.repaint();

            logger.debug("Button added successfully to item id='{}'", id);
        } else {
            logger.warn("Could not add button to item with id='{}' - item not found", id);
        }
    }

    /**
     * Rimuove un elemento tramite il suo ID
     */
    public void removeItem(String id) {
        logger.debug("Removing item with id='{}'", id);
        JLabel iconLabel = iconLabelMap.get(id);
        if (iconLabel != null) {
            Container parent = iconLabel.getParent();
            Container grandParent = parent.getParent();

            // Rimuovi dalla lista delle icone di caricamento se necessario
            if (loadingLabels.remove(iconLabel)) {
                logger.trace("Removed item id='{}' from loading labels", id);
            }

            // Rimuovi dalla UI
            grandParent.remove(parent);
            grandParent.revalidate();
            grandParent.repaint();

            // Rimuovi dalle mappe
            iconLabelMap.remove(id);
            textLabelMap.remove(id);
            itemPanelMap.remove(id);
            logger.debug("Item with id='{}' successfully removed", id);
        } else {
            logger.warn("Could not remove item with id='{}' - item not found", id);
        }
    }

    /**
     * Rimuove tutti gli elementi dal pannello
     */
    public void clearAll() {
        logger.debug("Clearing all items from panel");

        // Ferma l'animazione di caricamento per tutti gli elementi
        stopLoadingAnimation();

        // Rimuovi tutti i componenti dal contentPanel
        contentPanel.removeAll();

        // Pulisci tutte le liste e mappe
        loadingLabels.clear();
        iconLabelMap.clear();
        textLabelMap.clear();
        itemPanelMap.clear();

        // Aggiorna l'interfaccia
        contentPanel.revalidate();
        contentPanel.repaint();

        // Riavvia l'animazione di caricamento
        startLoadingAnimation();

        logger.info("All items cleared from panel");
    }

    /**
     * Metodo per fermare l'animazione quando non è più necessaria
     */
    public void stopLoadingAnimation() {
        logger.debug("Stopping loading animation timer");
        if (loadingTimer != null && loadingTimer.isRunning()) {
            loadingTimer.stop();
            logger.debug("Animation timer stopped");
        }
    }

    /**
     * Chiamare questo metodo nel dispose() o quando il pannello viene rimosso
     */
    public void dispose() {
        logger.info("Disposing InfoWindowPanel, clearing {} items", iconLabelMap.size());
        stopLoadingAnimation();
        loadingLabels.clear();
        iconLabelMap.clear();
        textLabelMap.clear();
        itemPanelMap.clear();
    }
}