package co.turismo.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para Scalar API Reference UI.
 * Permite personalizar el aspecto y comportamiento de la documentación.
 */
@Configuration
@ConfigurationProperties(prefix = "scalar")
public class ScalarProperties {

    /**
     * Título que aparecerá en la pestaña del navegador
     */
    private String title = "Turismo API Documentation";

    /**
     * Tema de color para la UI de Scalar.
     * Opciones: "purple", "blue", "green", "orange", "none", "moon", "saturn", "kepler", "mars", "default"
     */
    private String theme = "purple";

    /**
     * Habilita o deshabilita el modo oscuro
     */
    private boolean darkMode = false;

    /**
     * Layout de la interfaz.
     * Opciones: "modern", "classic"
     */
    private String layout = "modern";

    /**
     * Muestra u oculta la barra lateral
     */
    private boolean showSidebar = true;

    /**
     * Oculta los modelos/schemas en la documentación
     */
    private boolean hideModels = false;

    /**
     * Oculta el botón de descarga de la especificación OpenAPI
     */
    private boolean hideDownloadButton = false;

    /**
     * Tecla de acceso rápido para la búsqueda
     */
    private String searchHotKey = "k";

    // Getters y Setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public boolean isShowSidebar() {
        return showSidebar;
    }

    public void setShowSidebar(boolean showSidebar) {
        this.showSidebar = showSidebar;
    }

    public boolean isHideModels() {
        return hideModels;
    }

    public void setHideModels(boolean hideModels) {
        this.hideModels = hideModels;
    }

    public boolean isHideDownloadButton() {
        return hideDownloadButton;
    }

    public void setHideDownloadButton(boolean hideDownloadButton) {
        this.hideDownloadButton = hideDownloadButton;
    }

    public String getSearchHotKey() {
        return searchHotKey;
    }

    public void setSearchHotKey(String searchHotKey) {
        this.searchHotKey = searchHotKey;
    }
}
