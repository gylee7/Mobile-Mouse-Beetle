package com.github.joonasvali.naturalmouse.api; 

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataComparisonGraph extends JFrame {
    private static final String MOUSE_FILE_PATH = "C:\\Users\\user\\OneDrive\\Beetle\\mouse_trajectory.txt"; // 마우스 궤적 파일 경로
    private static final String ARCORE_FILE_PATH = "C:\\Users\\user\\OneDrive\\Beetle\\ARCore_sensor_pose.txt"; // ARCore 파일 경로

    public DataComparisonGraph(String title) {
        super(title);
        
        // 각 파일의 줄 수 출력
        int mouseLineCount = countLines(MOUSE_FILE_PATH);
        int arCoreLineCount = countLines(ARCORE_FILE_PATH);
        System.out.printf("Mouse Trajectory File Line Count: %d%n", mouseLineCount);
        System.out.printf("ARCore Sensor Pose File Line Count: %d%n", arCoreLineCount);

        CategoryDataset dataset = createDataset();
        JFreeChart chart = ChartFactory.createBarChart(
                "Mouse Trajectory vs ARCore t_x",
                "Data Point Index",
                "Value",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);
        ChartPanel panel = new ChartPanel(chart);
        panel.setPreferredSize(new Dimension(800, 600));
        setContentPane(panel);
    }

    private int countLines(String filePath) {
        int lineCount = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while (br.readLine() != null) {
                lineCount++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lineCount;
    }

    private CategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        List<Double> mouseXChanges = new ArrayList<>();
        List<Double> tXChanges = new ArrayList<>();

        // 데이터 읽기
        List<String[]> mouseTrajectory = readMouseTrajectory(MOUSE_FILE_PATH);
        List<String[]> arCoreData = readArCoreData(ARCORE_FILE_PATH);

        // 변화량 계산
        for (int i = 1; i < mouseTrajectory.size() && i < arCoreData.size(); i++) {
            double mouseX1 = Double.parseDouble(mouseTrajectory.get(i - 1)[0]);
            double mouseX2 = Double.parseDouble(mouseTrajectory.get(i)[0]);
            double tX1 = Double.parseDouble(arCoreData.get(i - 1)[5]);
            double tX2 = Double.parseDouble(arCoreData.get(i)[5]);

            double mouseXChange = mouseX2 - mouseX1;
            double tXChange = 53000*(tX2 - tX1);

            mouseXChanges.add(mouseXChange);
            tXChanges.add(tXChange);

            // 데이터셋에 추가
            dataset.addValue(mouseXChange, "Mouse Change", Integer.toString(i));
            dataset.addValue(tXChange, "ARCore t_x Change", Integer.toString(i));
        }

        return dataset;
    }

    // 마우스 궤적 파일 읽기
    private List<String[]> readMouseTrajectory(String filePath) {
        List<String[]> trajectory = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                trajectory.add(line.split(",")); // x,y로 나누기
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return trajectory;
    }

    // ARCore 데이터 파일 읽기
    private List<String[]> readArCoreData(String filePath) {
        List<String[]> arCoreData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            // 첫 번째 줄(메타데이터)은 건너뜁니다.
            br.readLine();
            while ((line = br.readLine()) != null) {
                arCoreData.add(line.split(" ")); // 공백으로 나누기
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return arCoreData;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DataComparisonGraph example = new DataComparisonGraph("Data Comparison Graph");
            example.setSize(800, 600);
            example.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            example.setVisible(true);
        });
    }
}
    

