package com.optics.simulation.ui;

import com.optics.simulation.AngularSpectrumPropagator;
import com.optics.simulation.ComplexField;
import com.optics.simulation.ImageUtils;
import com.optics.simulation.engine.SimulationEngine;
import com.optics.simulation.factory.ElementFactory;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.*;
import com.optics.simulation.renderer.SchemeRenderer;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
    private ImageView intensityImageView;
    private ImageView phaseImageView;
    private ProgressIndicator progressIndicator;
    private Label statusLabel;
    private SchemeRenderer schemeRenderer;
    private int editingIndex = -1;

    public static void main(String[] args) {
        launch(args);
    }


    // UI Creation Methods
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Wave Optical Simulation");

        // Left panel (scrollable)
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

        // Right panel: fixed images at bottom, scrollable content above
        BorderPane rightPane = new BorderPane();
        rightPane.setPadding(new Insets(10));

        // Scrollable content (scheme + charts)
        VBox scrollableContent = new VBox(10);
        scrollableContent.setPadding(new Insets(0, 0, 10, 0));

        schemeCanvas = new Canvas(600, 350);
        schemeCanvas.setStyle("-fx-border-color: lightgray; -fx-border-width: 1;");
        schemeRenderer = new SchemeRenderer(schemeCanvas);
        VBox schemeBox = new VBox(new Label("Optical Scheme"), schemeCanvas);
        schemeBox.setAlignment(Pos.TOP_CENTER);
        scrollableContent.getChildren().add(schemeBox);

        VBox chartsBox = createCharts();
        scrollableContent.getChildren().add(chartsBox);

        ScrollPane scrollPane = new ScrollPane(scrollableContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightPane.setCenter(scrollPane);

        // Fixed images at bottom
        VBox imagesBox = new VBox(10);
        imagesBox.setPadding(new Insets(10));
        imagesBox.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: lightgray; -fx-border-width: 1 0 0 0;");
        imagesBox.setMaxHeight(400);
        imagesBox.setFillWidth(true);

        intensityImageView = new ImageView();
        intensityImageView.setFitWidth(600);
        intensityImageView.setFitHeight(200);
        intensityImageView.setPreserveRatio(true);
        intensityImageView.setSmooth(true);

        phaseImageView = new ImageView();
        phaseImageView.setFitWidth(600);
        phaseImageView.setFitHeight(200);
        phaseImageView.setPreserveRatio(true);
        phaseImageView.setSmooth(true);

        HBox imageViews = new HBox(20);
        imageViews.setAlignment(Pos.CENTER);
        imageViews.getChildren().addAll(
                new VBox(new Label("Intensity (log)"), intensityImageView),
                new VBox(new Label("Phase"), phaseImageView)
        );
        imagesBox.getChildren().add(imageViews);

        rightPane.setBottom(imagesBox);
        BorderPane.setMargin(imagesBox, new Insets(10, 0, 0, 0));

        // SplitPane: left scrollable, right with fixed images
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(leftScroll, rightPane);
        splitPane.setDividerPositions(0.35);

        Scene scene = new Scene(splitPane, 1400, 1000);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initial draw
        updateScheme();
        elementManager.getElements().addListener((javafx.collections.ListChangeListener<? super OpticalElement>) change -> updateScheme());
        sourceTypeCombo.valueProperty().addListener((obs, old, newVal) -> updateScheme());
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
        elementListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OpticalElement item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getDescription());
            }
        });
        elementListView.setPrefHeight(150);
        return new VBox(5, new Label("Element sequence:"), elementListView);
    }

    private VBox createRunButton() {
        Button runButton = new Button("Run Simulation");
        runButton.setOnAction(e -> runSimulation());
        return new VBox(runButton);
    }

    private VBox createStatusPanel() {
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        statusLabel = new Label("Ready");
        HBox statusBox = new HBox(10, progressIndicator, statusLabel);
        statusBox.setPadding(new Insets(5));
        return new VBox(statusBox);
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

                // Устанавливаем anti-aliasing
                AngularSpectrumPropagator.setAntiAliasingEnabled(antiAliasingCheck.isSelected());

                double k = 2.0 * Math.PI / lambda;
                ComplexField field = new ComplexField(N, dx, lambda);
                double half = N / 2.0;

                updateMessage("Creating initial field...");
                for (int i = 0; i < N; i++) {
                    double x = (i - half) * dx;
                    for (int j = 0; j < N; j++) {
                        double y = (j - half) * dx;
                        if (pointSource) {
                            double w0 = 1e-6;
                            double r2 = x * x + y * y;
                            double amp = Math.exp(-r2 / (w0 * w0));
                            double r = Math.sqrt(r2);
                            field.setValue(i, j, amp * Math.cos(k * r), amp * Math.sin(k * r));
                        } else {
                            double r2 = x * x + y * y;
                            field.setValue(i, j, Math.exp(-r2 / (beamWidth * beamWidth)), 0.0);
                        }
                    }
                }

                updateMessage("Propagating through elements...");
                engine.run(field, elementManager.getElements());

                //updateMessage("Saving images...");
                //ImageUtils.saveImage(field.computeIntensity(), "final_intensity.png");
                //ImageUtils.saveImage(field.computePhase(), "final_phase.png");

                Platform.runLater(() -> {
                    updateCharts(field);
                    updateScheme();
                    // Update 2D images
                    intensityImageView.setImage(ImageUtils.createImage(field.computeIntensity(), true));
                    phaseImageView.setImage(ImageUtils.createImage(field.computePhase(), false));
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
}