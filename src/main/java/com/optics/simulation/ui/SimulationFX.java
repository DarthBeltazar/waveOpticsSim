package com.optics.simulation.ui;

import com.optics.simulation.AngularSpectrumPropagator;
import com.optics.simulation.ComplexField;
import com.optics.simulation.analysis.BeamAnalyzer;
import com.optics.simulation.config.SimulationConfig;
import com.optics.simulation.source.PartiallyCoherentSource;
import com.optics.simulation.util.Colormap;
import com.optics.simulation.util.ImageUtils;
import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.*;
import com.optics.simulation.renderer.SchemeRenderer;
import com.optics.simulation.config.PresetConfigs;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.optics.simulation.util.ImageUtils.findMax;
import static com.optics.simulation.util.ImageUtils.findMin;

public class SimulationFX extends Application {

    private final ElementManager elementManager = new ElementManager();
    private final SimulationEngine engine = new SimulationEngine();
    // UI components
    private TextField lambdaField, dxField, nField, beamWidthField;
    private ComboBox<String> sourceTypeCombo;
    private ComboBox<String> typeCombo;
    private TextField distanceField, focalField, periodField, amplitudeField, widthField;
    private CheckBox flatCheck, rectangularCheck, antiAliasingCheck;
    private Button addButton, editButton, removeButton, clearButton;
    private ListView<OpticalElement> elementListView;
    private Canvas schemeCanvas;
    private LineChart<Number, Number> intensityChart;
    private LineChart<Number, Number> phaseChart;
    private VBox paramBox;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    private SchemeRenderer schemeRenderer;
    private int editingIndex = -1;
    private ImageView intensityImageView, phaseImageView;
    private Canvas intensityColorbarCanvas, phaseColorbarCanvas;
    private ScrollPane intensityScroll, phaseScroll;
    private CheckBox logScaleCheck;
    private ComboBox<String> colormapCombo;
    private Slider zoomSlider;
    private Button saveIntensityBtn, savePhaseBtn;
    private ComplexField lastField = null;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private Stage primaryStage;
    private CheckBox partiallyCoherentCheck;
    private TextField coherenceLengthField;
    private TextField numRealizationsField;
    private CheckBox rgbModeCheck;
    private TextField redWavelengthField, greenWavelengthField, blueWavelengthField;

    public static void main(String[] args) {
        launch(args);
    }


    // UI Creation Methods
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Optical Simulation");

        // --- Menu Bar ---
        MenuBar menuBar = new MenuBar();
        Menu configMenu = new Menu("Config");

        MenuItem saveItem = new MenuItem("Save Configuration");
        saveItem.setOnAction(e -> saveConfiguration());

        MenuItem loadItem = new MenuItem("Load Configuration");
        loadItem.setOnAction(e -> loadConfiguration());

        MenuItem resetItem = new MenuItem("Reset All");
        resetItem.setOnAction(e -> {
            clearElements();
            lastField = null;
            intensityImageView.setImage(null);
            phaseImageView.setImage(null);
            updateScheme();
            statusLabel.setText("Reset");
        });

        Menu presetsMenu = new Menu("Presets");
        String[] presetNames = {
                "Free space propagation",
                "Lens focusing",
                "Diffraction on slit",
                "Grating diffraction",
                "Lens + grating"
        };
        for (String name : presetNames) {
            MenuItem presetItem = new MenuItem(name);
            presetItem.setOnAction(e -> loadPreset(name));
            presetsMenu.getItems().add(presetItem);
        }

        configMenu.getItems().addAll(saveItem, loadItem, resetItem, new SeparatorMenuItem(), presetsMenu);
        Menu toolsMenu = new Menu("Tools");
        MenuItem analyzeItem = new MenuItem("Beam Analysis");
        analyzeItem.setOnAction(e -> showBeamAnalysis());
        toolsMenu.getItems().add(analyzeItem);
        menuBar.getMenus().addAll(configMenu, toolsMenu);

        // Left panel (settings, scrollable)
        VBox leftContent = new VBox(10);
        leftContent.setPadding(new Insets(10));
        leftContent.getChildren().addAll(
                createCommonParams(),
                createElementConstructor(),
                createElementList(),
                createRunButton(),
                createStatusPanel()
        );
        ScrollPane leftScroll = new ScrollPane(leftContent);
        leftScroll.setFitToWidth(true);
        leftScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Right panel (single scrollable page)
        VBox rightContent = new VBox(10);
        rightContent.setPadding(new Insets(10));
        rightContent.setFillWidth(true);

        // 1. Optical Scheme
        schemeCanvas = new Canvas(600, 350);
        schemeCanvas.setStyle("-fx-border-color: lightgray; -fx-border-width: 1;");
        schemeRenderer = new SchemeRenderer(schemeCanvas);
        VBox schemeBox = new VBox(new Label("Optical Scheme"), schemeCanvas);
        schemeBox.setAlignment(Pos.TOP_CENTER);
        rightContent.getChildren().add(schemeBox);

        // 2. Profiles (charts)
        VBox chartsBox = createCharts();
        rightContent.getChildren().add(chartsBox);

        // 3. 2D Images with controls
        VBox imagesBox = new VBox(10);
        imagesBox.setPadding(new Insets(10));
        imagesBox.setFillWidth(true);

        // Controls for images
        HBox controls = new HBox(15);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(5));

        colormapCombo = new ComboBox<>();
        colormapCombo.getItems().addAll("Grayscale", "Viridis", "Jet", "Hot", "Cool", "Plasma");
        colormapCombo.setValue("Viridis");
        colormapCombo.setTooltip(new Tooltip("Color map for 2D images"));

        logScaleCheck = new CheckBox("Log scale");
        logScaleCheck.setSelected(true);
        logScaleCheck.setTooltip(new Tooltip("Apply log(1+I) scaling to intensity"));

        zoomSlider = new Slider(0.5, 2.0, 1.0);
        zoomSlider.setShowTickLabels(true);
        zoomSlider.setShowTickMarks(true);
        zoomSlider.setMajorTickUnit(0.5);
        zoomSlider.setMinorTickCount(0);
        zoomSlider.setBlockIncrement(0.1);
        zoomSlider.setPrefWidth(120);
        zoomSlider.setTooltip(new Tooltip("Zoom level for images"));

        saveIntensityBtn = new Button("Save Intensity");
        savePhaseBtn = new Button("Save Phase");
        saveIntensityBtn.setTooltip(new Tooltip("Save current intensity image as PNG"));
        savePhaseBtn.setTooltip(new Tooltip("Save current phase image as PNG"));

        controls.getChildren().addAll(
                new Label("Colormap:"), colormapCombo,
                logScaleCheck,
                new Label("Zoom:"), zoomSlider,
                saveIntensityBtn, savePhaseBtn
        );
        imagesBox.getChildren().add(controls);

        // ImageViews and ScrollPanes for panning
        intensityImageView = new ImageView();
        phaseImageView = new ImageView();
        intensityImageView.setPreserveRatio(true);
        phaseImageView.setPreserveRatio(true);
        intensityImageView.setFitWidth(400);
        phaseImageView.setFitWidth(400);

        intensityScroll = new ScrollPane(intensityImageView);
        intensityScroll.setFitToWidth(true);
        intensityScroll.setFitToHeight(true);
        intensityScroll.setPannable(true);
        intensityScroll.setPrefViewportHeight(250);

        phaseScroll = new ScrollPane(phaseImageView);
        phaseScroll.setFitToWidth(true);
        phaseScroll.setFitToHeight(true);
        phaseScroll.setPannable(true);
        phaseScroll.setPrefViewportHeight(250);

        // Colorbar canvases
        intensityColorbarCanvas = new Canvas(20, 250);
        phaseColorbarCanvas = new Canvas(20, 250);

        // Arrange images with colorbars
        HBox intensityPane = new HBox(5, intensityScroll, intensityColorbarCanvas);
        HBox.setHgrow(intensityScroll, Priority.ALWAYS);
        HBox phasePane = new HBox(5, phaseScroll, phaseColorbarCanvas);
        HBox.setHgrow(phaseScroll, Priority.ALWAYS);

        imagesBox.getChildren().addAll(
                new Label("Intensity (log scale)"),
                intensityPane,
                new Label("Phase"),
                phasePane
        );

        rightContent.getChildren().add(imagesBox);

        // Wrap rightContent in a ScrollPane to allow vertical scrolling
        ScrollPane rightScroll = new ScrollPane(rightContent);
        rightScroll.setFitToWidth(true);
        rightScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // SplitPane with left and right scrollable panels
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(leftScroll, rightScroll);
        splitPane.setDividerPositions(0.35);
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1400, 1000);

        // Scene
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        // Event Handlers (save, listeners)
        // Save intensity
        saveIntensityBtn.setOnAction(e -> {
            if (lastField == null) {
                showWarning("No data to save. Run simulation first.");
                return;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Intensity Image");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png")
            );
            fileChooser.setInitialFileName("intensity.png");
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    Colormap cmap = Colormap.fromDisplayName(colormapCombo.getValue());
                    if (cmap == null) cmap = Colormap.GRAYSCALE;
                    boolean log = logScaleCheck.isSelected();
                    ImageUtils.saveColoredImage(lastField.computeIntensity(), cmap, log, file.getAbsolutePath());
                } catch (IOException ex) {
                    showError("Failed to save: " + ex.getMessage());
                }
            }
        });

        // Save phase
        savePhaseBtn.setOnAction(e -> {
            if (lastField == null) {
                showWarning("No data to save. Run simulation first.");
                return;
            }
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Phase Image");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png")
            );
            fileChooser.setInitialFileName("phase.png");
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try {
                    Colormap cmap = Colormap.fromDisplayName(colormapCombo.getValue());
                    if (cmap == null) cmap = Colormap.GRAYSCALE;
                    ImageUtils.saveColoredImage(lastField.computePhase(), cmap, false, file.getAbsolutePath());
                } catch (IOException ex) {
                    showError("Failed to save: " + ex.getMessage());
                }
            }
        });

        // Listener for colormap change
        colormapCombo.valueProperty().addListener((obs, old, newVal) -> {
            if (lastField != null) updateImages(lastField);
        });

        // Listener for log scale toggle
        logScaleCheck.selectedProperty().addListener((obs, old, newVal) -> {
            if (lastField != null) updateImages(lastField);
        });

        // Listener for zoom slider
        zoomSlider.valueProperty().addListener((obs, old, newVal) -> {
            double zoom = newVal.doubleValue();
            intensityImageView.setFitWidth(zoom * 400);
            phaseImageView.setFitWidth(zoom * 400);
        });

        // Initial draw
        updateScheme();
        elementManager.getElements().addListener((javafx.collections.ListChangeListener<? super OpticalElement>) change -> updateScheme());
        sourceTypeCombo.valueProperty().addListener((obs, old, newVal) -> updateScheme());
    }
    private void showBeamAnalysis() {
        if (lastField == null) {
            showWarning("No field data. Run simulation first.");
            return;
        }
        BeamAnalyzer analyzer = new BeamAnalyzer(lastField);

        Stage stage = new Stage();
        stage.setTitle("Beam Analysis");
        stage.initModality(Modality.NONE);
        stage.initOwner(primaryStage);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));
        grid.setStyle("-fx-background-color: #2e2e2e;");

        Label titleLabel = new Label("Beam Parameters");
        titleLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 16px; -fx-font-weight: bold;");
        grid.add(titleLabel, 0, 0, 2, 1);

        int row = 1;
        addResultRow(grid, row++, "Center X (m)", String.format("%.4e", analyzer.getCenterX()));
        addResultRow(grid, row++, "Center Y (m)", String.format("%.4e", analyzer.getCenterY()));
        addResultRow(grid, row++, "Sigma X (m)", String.format("%.4e", analyzer.getSigmaX()));
        addResultRow(grid, row++, "Sigma Y (m)", String.format("%.4e", analyzer.getSigmaY()));
        addResultRow(grid, row++, "Radius 1/e² X (m)", String.format("%.4e", analyzer.getRadiusX()));
        addResultRow(grid, row++, "Radius 1/e² Y (m)", String.format("%.4e", analyzer.getRadiusY()));
        addResultRow(grid, row++, "FWHM X (m)", String.format("%.4e", analyzer.getFwhmX()));
        addResultRow(grid, row++, "FWHM Y (m)", String.format("%.4e", analyzer.getFwhmY()));
        addResultRow(grid, row++, "Peak Intensity", String.format("%.4e", analyzer.getPeakIntensity()));
        addResultRow(grid, row++, "Total Power", String.format("%.4e", analyzer.getTotalIntensity()));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #4a4a4a; -fx-text-fill: #e0e0e0;");
        closeButton.setOnAction(e -> stage.close());

        VBox vbox = new VBox(15, grid, closeButton);
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-background-color: #2e2e2e;");

        Scene scene = new Scene(vbox, 500, 450);

        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());

        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void addResultRow(GridPane grid, int row, String label, String value) {
        grid.add(new Label(label), 0, row);
        grid.add(new Label(value), 1, row);
    }

    /**
     * Updates the 2D images (intensity and phase) with the current colormap and log scale settings.
     * @param field the field to visualize
     */
    private void updateImages(ComplexField field) {
        if (field == null) return;
        lastField = field;

        Colormap cmap = Colormap.fromDisplayName(colormapCombo.getValue());
        if (cmap == null) cmap = Colormap.GRAYSCALE;
        boolean log = logScaleCheck.isSelected();

        double[][] intensity = field.computeIntensity();
        double[][] phase = field.computePhase();

        // Update ImageViews
        intensityImageView.setImage(ImageUtils.createColoredImage(intensity, cmap, log));
        phaseImageView.setImage(ImageUtils.createColoredImage(phase, cmap, false)); // phase always linear

        // Update colorbars
        double iMin = findMin(intensity);
        double iMax = findMax(intensity);
        double pMin = findMin(phase);
        double pMax = findMax(phase);
        ImageUtils.drawColorbar(intensityColorbarCanvas, cmap, iMin, iMax, "Intensity");
        ImageUtils.drawColorbar(phaseColorbarCanvas, cmap, pMin, pMax, "Phase");
    }

    /** Show a warning dialog. */
    private void showWarning(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg);
        alert.showAndWait();
    }

    /** Show an error dialog. */
    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.showAndWait();
    }

    private GridPane createCommonParams() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        lambdaField = new TextField("0.5e-6");
        dxField = new TextField("1e-5");
        nField = new TextField("512");
        beamWidthField = new TextField("1e-3");
        sourceTypeCombo = new ComboBox<>();
        sourceTypeCombo.getItems().addAll("Collimated beam", "Point source");
        sourceTypeCombo.setValue("Collimated beam");
        antiAliasingCheck = new CheckBox("Enable anti-aliasing (zero-padding)");
        antiAliasingCheck.setSelected(false);

        partiallyCoherentCheck = new CheckBox("Partially coherent");
        partiallyCoherentCheck.setSelected(false);
        partiallyCoherentCheck.setTooltip(new Tooltip("Enable partial coherence (Schell model)"));

        coherenceLengthField = new TextField("0.001");
        coherenceLengthField.setTooltip(new Tooltip("Coherence length (m) – Gaussian correlation length"));
        coherenceLengthField.setDisable(true);

        numRealizationsField = new TextField("20");
        numRealizationsField.setTooltip(new Tooltip("Number of realizations for averaging"));
        numRealizationsField.setDisable(true);

        partiallyCoherentCheck.selectedProperty().addListener((obs, old, val) -> {
            coherenceLengthField.setDisable(!val);
            numRealizationsField.setDisable(!val);
        });
        rgbModeCheck = new CheckBox("RGB mode (color diffraction)");
        rgbModeCheck.setSelected(false);
        rgbModeCheck.setTooltip(new Tooltip("Run simulation for red, green, blue wavelengths and combine into color image"));

        redWavelengthField = new TextField("0.65e-6");
        greenWavelengthField = new TextField("0.532e-6");
        blueWavelengthField = new TextField("0.45e-6");
        redWavelengthField.setDisable(true);
        greenWavelengthField.setDisable(true);
        blueWavelengthField.setDisable(true);

        rgbModeCheck.selectedProperty().addListener((obs, old, val) -> {
            redWavelengthField.setDisable(!val);
            greenWavelengthField.setDisable(!val);
            blueWavelengthField.setDisable(!val);
        });

        grid.add(rgbModeCheck, 1, 9);
        grid.add(new Label("Red λ (m):"), 0, 10);
        grid.add(redWavelengthField, 1, 10);
        grid.add(new Label("Green λ (m):"), 0, 11);
        grid.add(greenWavelengthField, 1, 11);
        grid.add(new Label("Blue λ (m):"), 0, 12);
        grid.add(blueWavelengthField, 1, 12);

        grid.add(new Label("Wavelength (m):"), 0, 0);
        grid.add(lambdaField, 1, 0);
        grid.add(new Label("Sampling (m):"), 0, 1);
        grid.add(dxField, 1, 1);
        grid.add(new Label("Grid size N:"), 0, 2);
        grid.add(nField, 1, 2);
        grid.add(new Label("Beam width (m):"), 0, 3);
        grid.add(beamWidthField, 1, 3);
        grid.add(new Label("Source type:"), 0, 4);
        grid.add(sourceTypeCombo, 1, 4);
        grid.add(antiAliasingCheck, 1, 5);
        grid.add(partiallyCoherentCheck, 1, 6);
        grid.add(new Label("Coherence length (m):"), 0, 7);
        grid.add(coherenceLengthField, 1, 7);
        grid.add(new Label("Realizations:"), 0, 8);
        grid.add(numRealizationsField, 1, 8);
        return grid;
    }

    private GridPane createElementConstructor() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Free space", "Lens", "Mirror", "Grating", "Slit");
        typeCombo.setValue("Free space");

        distanceField = new TextField("0.05");
        focalField = new TextField("0.05");
        periodField = new TextField("0.001");
        amplitudeField = new TextField("1.0");
        widthField = new TextField("0.001");
        flatCheck = new CheckBox("Flat mirror");
        rectangularCheck = new CheckBox("Rectangular grating");

        paramBox = new VBox(5);
        updateParamBox();
        typeCombo.setOnAction(e -> updateParamBox());

        addButton = new Button("Add");
        addButton.setOnAction(e -> addOrUpdateElement());
        editButton = new Button("Edit");
        editButton.setOnAction(e -> startEditing());
        removeButton = new Button("Remove");
        removeButton.setOnAction(e -> removeElement());
        clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearElements());

        HBox buttons = new HBox(10, addButton, editButton, removeButton, clearButton);

        grid.add(new Label("Element type:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Parameters:"), 0, 1);
        grid.add(paramBox, 1, 1);
        grid.add(buttons, 0, 2, 2, 1);

        return grid;
    }

    private VBox createElementList() {
        elementListView = new ListView<>(elementManager.getElements());
        elementListView.setCellFactory(lv -> new DragDropListCell());
        elementListView.setPrefHeight(150);

        elementListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                OpticalElement selected = elementListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    elementManager.remove(selected);
                    if (editingIndex >= 0) {
                        editingIndex = -1;
                        addButton.setText("Add");
                    }
                }
            }
        });

        return new VBox(5, new Label("Element sequence:"), elementListView);
    }

    private VBox createStatusPanel() {
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        statusLabel = new Label("Ready");
        HBox statusBox = new HBox(10, progressIndicator, statusLabel);
        statusBox.setPadding(new Insets(5));
        return new VBox(statusBox);
    }

    private VBox createRunButton() {
        Button runButton = new Button("Run Simulation");
        runButton.setOnAction(e -> runSimulation());
        return new VBox(runButton);
    }

    private void loadPreset(String name) {
        SimulationConfig config = null;
        switch (name) {
            case "Free space propagation":
                config = PresetConfigs.freeSpace();
                break;
            case "Lens focusing":
                config = PresetConfigs.lensFocusing();
                break;
            case "Diffraction on slit":
                config = PresetConfigs.slitDiffraction();
                break;
            case "Grating diffraction":
                config = PresetConfigs.gratingDiffraction();
                break;
            case "Lens + grating":
                config = PresetConfigs.lensAndGrating();
                break;
            default:
                return;
        }
        if (config == null) return;
        applyConfig(config);
        statusLabel.setText("Loaded preset: " + name);
    }

    private void applyConfig(SimulationConfig config) {
        lambdaField.setText(String.valueOf(config.lambda));
        dxField.setText(String.valueOf(config.dx));
        nField.setText(String.valueOf(config.n));
        beamWidthField.setText(String.valueOf(config.beamWidth));
        sourceTypeCombo.setValue(config.sourceType);
        antiAliasingCheck.setSelected(config.antiAliasing);
        colormapCombo.setValue(config.colormap);
        logScaleCheck.setSelected(config.logScale);

        elementManager.clear();
        editingIndex = -1;
        addButton.setText("Add");

        for (SimulationConfig.ElementConfig ec : config.elements) {
            OpticalElement elem = null;
            switch (ec.type) {
                case "Free space":
                    if (ec.distance != null) elem = ElementFactory.createFreeSpace(ec.distance);
                    break;
                case "Lens":
                    if (ec.focalLength != null) elem = ElementFactory.createLens(ec.focalLength);
                    break;
                case "Mirror":
                    if (ec.flat != null && ec.flat) {
                        elem = ElementFactory.createMirror(true);
                    } else if (ec.focalLength != null) {
                        elem = ElementFactory.createMirror(false, ec.focalLength);
                    }
                    break;
                case "Grating":
                    if (ec.period != null && ec.amplitude != null) {
                        boolean rect = ec.rectangular != null && ec.rectangular;
                        elem = ElementFactory.createGrating(ec.period, ec.amplitude, rect);
                    }
                    break;
                case "Slit":
                    if (ec.width != null) elem = ElementFactory.createSlit(ec.width);
                    break;
            }
            if (elem != null) elementManager.add(elem);
        }

        updateScheme();
        lastField = null;
        intensityImageView.setImage(null);
        phaseImageView.setImage(null);
    }

    private void saveConfiguration() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Configuration");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("config.json");
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file == null) return;

        try {
            SimulationConfig config = new SimulationConfig();
            config.lambda = Double.parseDouble(lambdaField.getText());
            config.dx = Double.parseDouble(dxField.getText());
            config.n = Integer.parseInt(nField.getText());
            config.beamWidth = Double.parseDouble(beamWidthField.getText());
            config.sourceType = sourceTypeCombo.getValue();
            config.antiAliasing = antiAliasingCheck.isSelected();
            config.colormap = colormapCombo.getValue();
            config.logScale = logScaleCheck.isSelected();

            List<SimulationConfig.ElementConfig> elemConfigs = new ArrayList<>();
            for (OpticalElement elem : elementManager.getElements()) {
                SimulationConfig.ElementConfig ec = new SimulationConfig.ElementConfig();
                if (elem instanceof FreeSpaceElement) {
                    ec.type = "Free space";
                    ec.distance = ((FreeSpaceElement) elem).getLength();
                } else if (elem instanceof LensElement) {
                    ec.type = "Lens";
                    ec.focalLength = ((LensElement) elem).getFocalLength();
                } else if (elem instanceof MirrorElement) {
                    ec.type = "Mirror";
                    MirrorElement m = (MirrorElement) elem;
                    ec.flat = m.isFlat();
                    if (!m.isFlat()) ec.focalLength = m.getFocalLength();
                } else if (elem instanceof GratingElement) {
                    ec.type = "Grating";
                    GratingElement g = (GratingElement) elem;
                    ec.period = g.getPeriod();
                    ec.amplitude = g.getAmplitude();
                    ec.rectangular = g.isRectangular();
                } else if (elem instanceof SlitElement) {
                    ec.type = "Slit";
                    ec.width = ((SlitElement) elem).getWidth();
                } else {
                    continue;
                }
                elemConfigs.add(ec);
            }
            config.elements = elemConfigs;

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
            statusLabel.setText("Configuration saved to " + file.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Failed to save: " + ex.getMessage());
        }
    }

    private void loadConfiguration() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Configuration");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file == null) return;

        try {
            SimulationConfig config = objectMapper.readValue(file, SimulationConfig.class);

            lambdaField.setText(String.valueOf(config.lambda));
            dxField.setText(String.valueOf(config.dx));
            nField.setText(String.valueOf(config.n));
            beamWidthField.setText(String.valueOf(config.beamWidth));
            sourceTypeCombo.setValue(config.sourceType);
            antiAliasingCheck.setSelected(config.antiAliasing);
            colormapCombo.setValue(config.colormap);
            logScaleCheck.setSelected(config.logScale);

            elementManager.clear();
            editingIndex = -1;
            addButton.setText("Add");

            for (SimulationConfig.ElementConfig ec : config.elements) {
                OpticalElement elem = null;
                switch (ec.type) {
                    case "Free space":
                        if (ec.distance != null) elem = ElementFactory.createFreeSpace(ec.distance);
                        break;
                    case "Lens":
                        if (ec.focalLength != null) elem = ElementFactory.createLens(ec.focalLength);
                        break;
                    case "Mirror":
                        if (ec.flat != null && ec.flat) {
                            elem = ElementFactory.createMirror(true);
                        } else if (ec.focalLength != null) {
                            elem = ElementFactory.createMirror(false, ec.focalLength);
                        }
                        break;
                    case "Grating":
                        if (ec.period != null && ec.amplitude != null) {
                            boolean rect = ec.rectangular != null && ec.rectangular;
                            elem = ElementFactory.createGrating(ec.period, ec.amplitude, rect);
                        }
                        break;
                    case "Slit":
                        if (ec.width != null) elem = ElementFactory.createSlit(ec.width);
                        break;
                }
                if (elem != null) elementManager.add(elem);
            }

            updateScheme();
            lastField = null;
            intensityImageView.setImage(null);
            phaseImageView.setImage(null);
            statusLabel.setText("Configuration loaded from " + file.getName());
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Failed to load: " + ex.getMessage());
        }
    }

    // Element Management
    private VBox createCharts() {
        NumberAxis xAxisIntensity = new NumberAxis("x (m)", 0, 0, 1e-5);
        NumberAxis yAxisIntensity = new NumberAxis("log(1+I)", 0, 0, 0.1);
        intensityChart = new LineChart<>(xAxisIntensity, yAxisIntensity);
        intensityChart.setTitle("Intensity Profile");
        intensityChart.setCreateSymbols(false);

        NumberAxis xAxisPhase = new NumberAxis("x (m)", 0, 0, 1e-5);
        NumberAxis yAxisPhase = new NumberAxis("Phase (rad)", -Math.PI, Math.PI, 0.5);
        phaseChart = new LineChart<>(xAxisPhase, yAxisPhase);
        phaseChart.setTitle("Phase Profile");
        phaseChart.setCreateSymbols(false);

        VBox box = new VBox(15);
        box.setPadding(new Insets(10));
        box.getChildren().addAll(intensityChart, phaseChart);
        return box;
    }

    private void updateParamBox() {
        paramBox.getChildren().clear();
        String type = typeCombo.getValue();
        switch (type) {
            case "Free space":
                paramBox.getChildren().add(new HBox(5, new Label("Distance (m):"), distanceField));
                break;
            case "Lens":
                paramBox.getChildren().add(new HBox(5, new Label("Focal length (m):"), focalField));
                break;
            case "Mirror":
                paramBox.getChildren().addAll(
                        new HBox(5, new Label("Focal length (m) [blank for flat]:"), focalField),
                        flatCheck
                );
                break;
            case "Grating":
                paramBox.getChildren().addAll(
                        new HBox(5, new Label("Period (m):"), periodField),
                        new HBox(5, new Label("Amplitude (rad):"), amplitudeField),
                        rectangularCheck
                );
                break;
            case "Slit":
                paramBox.getChildren().add(new HBox(5, new Label("Width (m):"), widthField));
                break;
        }
    }

    private void addOrUpdateElement() {
        String type = typeCombo.getValue();
        OpticalElement elem = null;
        try {
            switch (type) {
                case "Free space":
                    double d = Double.parseDouble(distanceField.getText());
                    elem = ElementFactory.createFreeSpace(d);
                    break;
                case "Lens":
                    double f = Double.parseDouble(focalField.getText());
                    elem = ElementFactory.createLens(f);
                    break;
                case "Mirror":
                    String fText = focalField.getText().trim();
                    boolean flat = flatCheck.isSelected();
                    if (fText.isEmpty()) {
                        elem = ElementFactory.createMirror(flat);
                    } else {
                        double f2 = Double.parseDouble(fText);
                        elem = ElementFactory.createMirror(flat, f2);
                    }
                    break;
                case "Grating":
                    double p = Double.parseDouble(periodField.getText());
                    double a = Double.parseDouble(amplitudeField.getText());
                    boolean rect = rectangularCheck.isSelected();
                    elem = ElementFactory.createGrating(p, a, rect);
                    break;
                case "Slit":
                    double w = Double.parseDouble(widthField.getText());
                    elem = ElementFactory.createSlit(w);
                    break;
            }
            if (elem == null) return;
            if (editingIndex >= 0 && editingIndex < elementManager.size()) {
                elementManager.set(editingIndex, elem);
                editingIndex = -1;
                addButton.setText("Add");
                elementListView.getSelectionModel().clearSelection();
            } else {
                elementManager.add(elem);
            }
            addButton.setText("Add");
        } catch (NumberFormatException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid parameter value.");
            alert.showAndWait();
        }
    }

    private void startEditing() {
        OpticalElement selected = elementListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "No element selected.");
            alert.showAndWait();
            return;
        }
        editingIndex = elementListView.getSelectionModel().getSelectedIndex();
        if (selected instanceof FreeSpaceElement) {
            typeCombo.setValue("Free space");
            distanceField.setText(String.valueOf(selected.getLength()));
        } else if (selected instanceof LensElement) {
            typeCombo.setValue("Lens");
            focalField.setText(String.valueOf(((LensElement) selected).getFocalLength()));
        } else if (selected instanceof MirrorElement m) {
            typeCombo.setValue("Mirror");
            if (m.isFlat()) {
                focalField.setText("");
                flatCheck.setSelected(true);
            } else {
                focalField.setText(String.valueOf(m.getFocalLength()));
                flatCheck.setSelected(false);
            }
        } else if (selected instanceof GratingElement g) {
            typeCombo.setValue("Grating");
            periodField.setText(String.valueOf(g.getPeriod()));
            amplitudeField.setText(String.valueOf(g.getAmplitude()));
            rectangularCheck.setSelected(g.isRectangular());
        } else if (selected instanceof SlitElement) {
            typeCombo.setValue("Slit");
            widthField.setText(String.valueOf(((SlitElement) selected).getWidth()));
        }
        updateParamBox();
        addButton.setText("Update");
    }

    private void removeElement() {
        OpticalElement selected = elementListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            elementManager.remove(selected);
            if (editingIndex >= 0) {
                editingIndex = -1;
                addButton.setText("Add");
            }
        }
    }


    // Scheme and Simulation
    private void clearElements() {
        elementManager.clear();
        editingIndex = -1;
        addButton.setText("Add");
    }

    private void updateScheme() {
        if (schemeRenderer != null) {
            boolean pointSource = "Point source".equals(sourceTypeCombo.getValue());
            schemeRenderer.draw(elementManager.getElements(), pointSource);
        }
    }


    // Helpers for Charts and Images
    private void runSimulation() {
        progressIndicator.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        statusLabel.setText("Starting simulation...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                double lambda = Double.parseDouble(lambdaField.getText());
                double dx = Double.parseDouble(dxField.getText());
                int N = Integer.parseInt(nField.getText());
                double beamWidth = Double.parseDouble(beamWidthField.getText());
                boolean pointSource = "Point source".equals(sourceTypeCombo.getValue());
                boolean partiallyCoherent = partiallyCoherentCheck.isSelected();

                AngularSpectrumPropagator.setAntiAliasingEnabled(antiAliasingCheck.isSelected());

                List<ComplexField> initialFields;
                if (partiallyCoherent) {
                    double coherenceLength = Double.parseDouble(coherenceLengthField.getText());
                    int numRealizations = Integer.parseInt(numRealizationsField.getText());
                    PartiallyCoherentSource source = new PartiallyCoherentSource(N, dx, lambda, beamWidth, coherenceLength, numRealizations);
                    initialFields = source.generateFields();
                    updateMessage("Generated " + numRealizations + " realizations");
                } else {
                    ComplexField field = createCoherentField(N, dx, lambda, beamWidth, pointSource);
                    initialFields = List.of(field);
                }

                List<ComplexField> propagatedFields = new ArrayList<>(initialFields.size());
                if (partiallyCoherent) {
                    IntStream.range(0, initialFields.size()).parallel().forEach(i -> {
                        ComplexField f = initialFields.get(i);
                        engine.run(f, elementManager.getElements());
                        synchronized (propagatedFields) {
                            propagatedFields.add(f);
                        }
                    });
                } else {
                    ComplexField field = initialFields.get(0);
                    engine.run(field, elementManager.getElements());
                    propagatedFields.add(field);
                }

                Platform.runLater(() -> {
                    if (partiallyCoherent) {
                        double[][] avgIntensity = averageIntensity(propagatedFields);
                        double[][] phase = propagatedFields.get(0).computePhase();

                        try {
                            ImageUtils.saveImage(avgIntensity, "final_intensity_avg.png");
                            ImageUtils.saveImage(phase, "final_phase_first.png");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        updateCharts(avgIntensity, phase);
                        updateImages(avgIntensity, phase);
                    } else {
                        ComplexField field = propagatedFields.get(0);
                        try {
                            ImageUtils.saveImage(field.computeIntensity(), "final_intensity.png");
                            ImageUtils.saveImage(field.computePhase(), "final_phase.png");
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        updateCharts(field);
                        updateImages(field);
                    }
                    updateScheme();
                    progressIndicator.setVisible(false);
                    statusLabel.setText("Done.");
                });
                return null;
            }
        };

        task.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            statusLabel.setText("Error: " + task.getException().getMessage());
            task.getException().printStackTrace();
        });

        new Thread(task).start();
    }

    private void updateCharts(ComplexField field) {
        int n = field.getN();
        double dx = field.getDx();
        double half = n / 2.0;
        int centerRow = n / 2;
        double[][] intensity = field.computeIntensity();
        double[][] phase = field.computePhase();

        XYChart.Series<Number, Number> intSeries = new XYChart.Series<>();
        intSeries.setName("Intensity (log)");
        XYChart.Series<Number, Number> phaseSeries = new XYChart.Series<>();
        phaseSeries.setName("Phase");

        for (int j = 0; j < n; j++) {
            double x = (j - half) * dx;
            double I = intensity[centerRow][j];
            intSeries.getData().add(new XYChart.Data<>(x, Math.log1p(I)));
            phaseSeries.getData().add(new XYChart.Data<>(x, phase[centerRow][j]));
        }

        intensityChart.getData().clear();
        intensityChart.getData().add(intSeries);
        phaseChart.getData().clear();
        phaseChart.getData().add(phaseSeries);

        intensityChart.getXAxis().setAutoRanging(true);
        intensityChart.getYAxis().setAutoRanging(true);
        phaseChart.getXAxis().setAutoRanging(true);
        phaseChart.getYAxis().setAutoRanging(true);
    }

    private ComplexField createCoherentField(int N, double dx, double lambda, double beamWidth, boolean pointSource) {
        ComplexField field = new ComplexField(N, dx, lambda);
        double half = N / 2.0;
        double k = 2.0 * Math.PI / lambda;
        double[][] data = field.getData();
        for (int i = 0; i < N; i++) {
            double x = (i - half) * dx;
            for (int j = 0; j < N; j++) {
                double y = (j - half) * dx;
                int idx = 2 * j;
                if (pointSource) {
                    double w0 = 1e-6;
                    double r2 = x*x + y*y;
                    double amp = Math.exp(-r2/(w0*w0));
                    double r = Math.sqrt(r2);
                    data[i][idx] = amp * Math.cos(k*r);
                    data[i][idx+1] = amp * Math.sin(k*r);
                } else {
                    double r2 = x*x + y*y;
                    data[i][idx] = Math.exp(-r2/(beamWidth*beamWidth));
                    data[i][idx+1] = 0.0;
                }
            }
        }
        return field;
    }

    private double[][] averageIntensity(List<ComplexField> fields) {
        int n = fields.get(0).getN();
        double[][] avg = new double[n][n];
        for (ComplexField f : fields) {
            double[][] I = f.computeIntensity();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    avg[i][j] += I[i][j];
                }
            }
        }
        double inv = 1.0 / fields.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                avg[i][j] *= inv;
            }
        }
        return avg;
    }

    private void updateCharts(double[][] intensity, double[][] phase) {
        int n = intensity.length;
        double dx = Double.parseDouble(dxField.getText());
        double half = n / 2.0;
        int centerRow = n / 2;

        XYChart.Series<Number, Number> intSeries = new XYChart.Series<>();
        intSeries.setName("Intensity (log)");
        XYChart.Series<Number, Number> phaseSeries = new XYChart.Series<>();
        phaseSeries.setName("Phase");

        for (int j = 0; j < n; j++) {
            double x = (j - half) * dx;
            double I = intensity[centerRow][j];
            intSeries.getData().add(new XYChart.Data<>(x, Math.log1p(I)));
            phaseSeries.getData().add(new XYChart.Data<>(x, phase[centerRow][j]));
        }

        intensityChart.getData().clear();
        intensityChart.getData().add(intSeries);
        phaseChart.getData().clear();
        phaseChart.getData().add(phaseSeries);
        intensityChart.getXAxis().setAutoRanging(true);
        intensityChart.getYAxis().setAutoRanging(true);
        phaseChart.getXAxis().setAutoRanging(true);
        phaseChart.getYAxis().setAutoRanging(true);
    }

    private void updateImages(double[][] intensity, double[][] phase) {
        lastField = null;
        Colormap cmap = Colormap.fromDisplayName(colormapCombo.getValue());
        if (cmap == null) cmap = Colormap.GRAYSCALE;
        boolean log = logScaleCheck.isSelected();

        intensityImageView.setImage(ImageUtils.createColoredImage(intensity, cmap, log));
        phaseImageView.setImage(ImageUtils.createColoredImage(phase, cmap, false));

        double iMin = findMin(intensity);
        double iMax = findMax(intensity);
        double pMin = findMin(phase);
        double pMax = findMax(phase);
        ImageUtils.drawColorbar(intensityColorbarCanvas, cmap, iMin, iMax, "Intensity");
        ImageUtils.drawColorbar(phaseColorbarCanvas, cmap, pMin, pMax, "Phase");
    }

    /**
     * Custom ListCell that supports drag-and-drop reordering.
     */
    private static class DragDropListCell extends ListCell<OpticalElement> {

        @Override
        protected void updateItem(OpticalElement item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getDescription());
            }
        }

        {
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString("move");
                db.setContent(content);
                event.consume();
            });

            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            setOnDragEntered(event -> {
                setStyle("-fx-background-color: #555555;");
            });

            setOnDragExited(event -> {
                setStyle("");
            });

            setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    ListView<OpticalElement> listView = getListView();
                    if (listView != null) {
                        OpticalElement sourceItem = listView.getSelectionModel().getSelectedItem();
                        if (sourceItem != null) {
                            int sourceIndex = listView.getItems().indexOf(sourceItem);
                            int targetIndex = getIndex();
                            if (sourceIndex >= 0 && targetIndex >= 0 && sourceIndex != targetIndex) {
                                ObservableList<OpticalElement> items = listView.getItems();
                                OpticalElement moved = items.remove(sourceIndex);
                                if (targetIndex > sourceIndex) targetIndex--;
                                items.add(targetIndex, moved);
                                success = true;
                            }
                        }
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            setOnDragDone(event -> {
                setStyle("");
                event.consume();
            });
        }
    }
}