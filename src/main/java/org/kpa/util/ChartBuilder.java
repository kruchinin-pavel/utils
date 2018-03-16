package org.kpa.util;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYDotRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.*;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.ui.RectangleInsets;

import java.awt.*;
import java.util.*;

/**
 * Created by krucpav on 01.02.14.
 */
public class ChartBuilder {
    private String chartName;
    private String subTitle;
    private String xAxisName;
    private static final java.util.List<Color> paintColors = Arrays.asList(Color.black, Color.red, Color.blue, Color.magenta,
            Color.orange);
    TimeSeries lastSeries;
    private final java.util.List<XYDataset> datasetList = new ArrayList<>();
    private final java.util.List<String> rangeAxisName = new ArrayList<>();
    private final Map<String, TimeSeries> seriesByName = new HashMap<>();
    private boolean ignoreErrors = false;

    public ChartBuilder ignoreErrors(boolean ignoreErrors) {
        this.ignoreErrors = ignoreErrors;
        return this;
    }

    private ChartBuilder() {
    }

    public ChartBuilder name(String name) {
        this.chartName = name;
        return this;
    }

    public ChartBuilder subTitle(String subTitle) {
        this.subTitle = subTitle;
        return this;
    }

    public ChartBuilder xRangeName(String xAxisName) {
        this.xAxisName = xAxisName;
        return this;
    }

    public JFreeChart chart() {
        Iterator<XYDataset> xyDatasetIterator = datasetList.iterator();
        Iterator<String> rangeNameIterator = rangeAxisName.iterator();
        Iterator<Color> colorIterator = paintColors.iterator();
        XYDataset dataset1 = xyDatasetIterator.next();

        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                chartName,
                xAxisName,
                rangeNameIterator.next(),
                dataset1,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(Color.white);
        if (subTitle != null) {
            chart.addSubtitle(new TextTitle(subTitle));
        }
        XYPlot plot = chart.getXYPlot();
        plot.setOrientation(PlotOrientation.VERTICAL);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);

        plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));
        plot.getRangeAxis().setFixedDimension(15.0);
        XYItemRenderer renderer = plot.getRenderer();
        renderer.setPaint(colorIterator.next());

        int cnt = 0;
        while (xyDatasetIterator.hasNext()) {
            cnt++;
            NumberAxis axis = new NumberAxis(rangeNameIterator.next());
            axis.setFixedDimension(10.0);
            axis.setAutoRangeIncludesZero(false);
            Color color = colorIterator.next();
            axis.setLabelPaint(color);
            axis.setTickLabelPaint(color);
            plot.setRangeAxis(cnt, axis);
            plot.setRangeAxisLocation(1, AxisLocation.BOTTOM_OR_LEFT);

            XYDataset xyDataset = xyDatasetIterator.next();
            plot.setDataset(cnt, xyDataset);
            plot.mapDatasetToRangeAxis(cnt, cnt);
            renderer = new StandardXYItemRenderer();
            renderer.setSeriesPaint(0, color);
            plot.setRenderer(cnt, renderer);
        }
        return chart;
    }


    public ChartBuilder dataset(String name, String yRangeName) {
        if (seriesByName.containsKey(name)) {
            throw new IllegalArgumentException("Already contains series: " + name);
        }
        TimeSeries series = new TimeSeries(name);
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);
        lastSeries = series;
        datasetList.add(dataset);
        rangeAxisName.add(yRangeName);
        seriesByName.put(name, series);
        return this;
    }

    public ChartBuilder value(String seriesName, Long time, Double value) {
        TimeSeries series = seriesByName.get(seriesName);
        if (series == null) {
            if (ignoreErrors) {
                return this;
            }
            throw new IllegalArgumentException("No series with id: " + seriesName);
        }
        series.add(new Millisecond(new Date(time)), value);
        return this;
    }

    public ChartBuilder value(Long time, Double value) {
        lastSeries.add(new Millisecond(new Date(time)), value);
        return this;
    }

    public ChartBuilder values(Iterable<Long> time, Iterable<Double> values) {
        Iterator<Long> longIterator = time.iterator();
        Iterator<Double> doubleIterator = values.iterator();
        while (longIterator.hasNext() && doubleIterator.hasNext()) {
            value(longIterator.next(), doubleIterator.next());
        }
        return this;
    }

    public static ChartBuilder builder() {
        return new ChartBuilder();
    }


    public ChartFrame show(String frameName) {
        JFreeChart chart = chart();
        ChartFrame frame = new ChartFrame(frameName, chart);
        frame.pack();
        frame.setVisible(true);
        return frame;
    }

    public static void main(String[] args) {
        ChartBuilder builder = ChartBuilder.builder()
                .subTitle("Four datasets and four range axes.")
                .xRangeName("Time of Day")
                .name("Multiple Axis Demo 1");
        createDataset(builder, "Series 1", 100.0, new Minute(), 200, "Primary Range Axis");
        createDataset(builder, "Series 2", 1000.0, new Minute(), 170, "Range Axis 2");
        createDataset(builder, "Series 3", 10000.0, new Minute(), 170, "Range Axis 3");
        createDataset(builder, "Series 4", 25.0, new Minute(), 200, "Range Axis 4");
        builder.show("Test");
    }


    private static void createDataset(ChartBuilder builder, String name, double base,
                                      RegularTimePeriod start, int count, String rangeName) {

        builder.dataset(name, rangeName);

        RegularTimePeriod period = start;
        double value = base;
        for (int i = 0; i < count; i++) {
            builder.value(period.getMiddleMillisecond(), value);
            period = period.next();
            value = value * (1 + (Math.random() - 0.495) / 10.0);
        }
    }

    public static void xyChart(double[][] data, String frameTitle, String chartTitle, String seriesKey, String xAxisLabel, String yAxisLabel) {
        DefaultXYDataset dataSet = new DefaultXYDataset();
        dataSet.addSeries(seriesKey, data);
        JFreeChart jfreechart = ChartFactory.createScatterPlot(chartTitle, xAxisLabel, yAxisLabel, dataSet, PlotOrientation.VERTICAL, true, true, false);
        XYPlot xyplot = (XYPlot) jfreechart.getPlot();
        xyplot.setRangeTickBandPaint(new Color(200, 200, 100, 100));
        XYDotRenderer xydotrenderer = new XYDotRenderer();
        xydotrenderer.setDotWidth(4);
        xydotrenderer.setDotHeight(4);
        xyplot.setRenderer(xydotrenderer);
        xyplot.setDomainCrosshairVisible(true);
        xyplot.setRangeCrosshairVisible(true);
        NumberAxis numberaxis = (NumberAxis) xyplot.getDomainAxis();
        numberaxis.setAutoRangeIncludesZero(false);
        xyplot.getRangeAxis().setInverted(false);
        ChartFrame frame = new ChartFrame(frameTitle, jfreechart);
        frame.pack();
        frame.setVisible(true);
    }


}
