package com.optics.simulation.export;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.optics.simulation.ComplexField;
import com.optics.simulation.analysis.BeamAnalyzer;
import com.optics.simulation.manager.ElementManager;
import com.optics.simulation.model.OpticalElement;
import com.optics.simulation.util.Colormap;
import com.optics.simulation.util.ImageUtils;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.util.List;

public class PdfExporter {

    public static void export(String filename,
                              ComplexField field,
                              ElementManager elementManager,
                              double lambda,
                              double dx,
                              int n,
                              double beamWidth,
                              String sourceType,
                              boolean antiAliasing,
                              Colormap colormap,
                              boolean logScale) throws Exception {
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, new FileOutputStream(filename));
        document.open();

        // ---- Fonts ----
        Font titleFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 18, Font.BOLD);
        Font headingFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 14, Font.BOLD);
        Font normalFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, Font.NORMAL);
        Font headerFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, Font.BOLD);

        // ---- Header ----
        Paragraph title = new Paragraph("Optical Simulation Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        // ---- Simulation params ----
        document.add(new Paragraph("Simulation Parameters", headingFont));
        document.add(new Paragraph("Wavelength: " + lambda + " m", normalFont));
        document.add(new Paragraph("Sampling step: " + dx + " m", normalFont));
        document.add(new Paragraph("Grid size: " + n + " x " + n, normalFont));
        document.add(new Paragraph("Beam width: " + beamWidth + " m", normalFont));
        document.add(new Paragraph("Source type: " + sourceType, normalFont));
        document.add(new Paragraph("Anti-aliasing: " + (antiAliasing ? "enabled" : "disabled"), normalFont));
        document.add(new Paragraph("Colormap: " + colormap.name(), normalFont));
        document.add(new Paragraph("Intensity log scale: " + (logScale ? "yes" : "no"), normalFont));
        document.add(new Paragraph(" "));

        // ---- Element List ----
        document.add(new Paragraph("Optical elements in sequence:", headingFont));
        List<OpticalElement> elements = elementManager.getElements();
        for (int i = 0; i < elements.size(); i++) {
            document.add(new Paragraph("  " + (i + 1) + ". " + elements.get(i).getDescription(), normalFont));
        }
        document.add(new Paragraph(" "));

        document.newPage();

        // ---- 2D-images ----
        document.add(new Paragraph("2D Images", headingFont));
        document.add(new Paragraph(" "));

        double[][] intensity = field.computeIntensity();
        double[][] phase = field.computePhase();

        // Intensity
        BufferedImage intensityImg = ImageUtils.createBufferedImage(intensity, colormap, logScale);
        Image imgIntensity = Image.getInstance(intensityImg, null);
        imgIntensity.scaleToFit(500, 300);
        imgIntensity.setAlignment(Image.ALIGN_CENTER);
        document.add(imgIntensity);
        document.add(new Paragraph("Intensity (log scale)", normalFont));
        document.add(new Paragraph(" "));

        // Phase
        BufferedImage phaseImg = ImageUtils.createBufferedImage(phase, colormap, false);
        Image imgPhase = Image.getInstance(phaseImg, null);
        imgPhase.scaleToFit(500, 300);
        imgPhase.setAlignment(Image.ALIGN_CENTER);
        document.add(imgPhase);
        document.add(new Paragraph("Phase", normalFont));
        document.add(new Paragraph(" "));

        document.newPage();

        // ---- Intensity profile ----
        document.add(new Paragraph("Intensity Profile (horizontal)", headingFont));
        document.add(new Paragraph(" "));

        int centerRow = n / 2;
        double half = n / 2.0;
        int step = Math.max(1, n / 100);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        table.setHeaderRows(1);

        table.addCell(new Paragraph("x (m)", headerFont));
        table.addCell(new Paragraph("Intensity", headerFont));

        for (int j = 0; j < n; j += step) {
            double x = (j - half) * dx;
            table.addCell(new Paragraph(String.format("%.6e", x), normalFont));
            table.addCell(new Paragraph(String.format("%.6e", intensity[centerRow][j]), normalFont));
        }

        document.add(table);
        document.add(new Paragraph(" "));

        // ---- Beam analysis ----
        if (field != null) {
            document.add(new Paragraph("Beam Analysis", headingFont));
            BeamAnalyzer analyzer = new BeamAnalyzer(field);
            document.add(new Paragraph("Center X: " + analyzer.getCenterX() + " m", normalFont));
            document.add(new Paragraph("Center Y: " + analyzer.getCenterY() + " m", normalFont));
            document.add(new Paragraph("Sigma X: " + analyzer.getSigmaX() + " m", normalFont));
            document.add(new Paragraph("Sigma Y: " + analyzer.getSigmaY() + " m", normalFont));
            document.add(new Paragraph("Radius (1/e²) X: " + analyzer.getRadiusX() + " m", normalFont));
            document.add(new Paragraph("Radius (1/e²) Y: " + analyzer.getRadiusY() + " m", normalFont));
            document.add(new Paragraph("FWHM X: " + analyzer.getFwhmX() + " m", normalFont));
            document.add(new Paragraph("FWHM Y: " + analyzer.getFwhmY() + " m", normalFont));
            document.add(new Paragraph("Peak Intensity: " + analyzer.getPeakIntensity(), normalFont));
            document.add(new Paragraph("Total Power: " + analyzer.getTotalIntensity(), normalFont));
        }

        document.close();
    }
}