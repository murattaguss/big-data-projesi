package com.bdata;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.HighlightPainter;
import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Desktop GUI Application for Accessible Digital Libraries.
 * Fully optimized for host systems running any Java version (including Java 24)
 * by utilizing WebHDFS REST API for HDFS interactions and executing MapReduce
 * workloads within the Docker cluster via process orchestration.
 */
public class LibraryGUI extends JFrame {

    private final SearchEngine searchEngine = new SearchEngine();

    // HDFS Manager Components
    private JTextField txtHdfsPath;
    private JTable tblHdfsFiles;
    private DefaultTableModel modelHdfsFiles;

    // Job Monitor Components
    private JTextField txtJobInputPath;
    private JTextField txtJobOutputPath;
    private JTextArea txtConsoleLog;
    private JButton btnRunPipeline;
    private JProgressBar prgMap;
    private JProgressBar prgReduce;
    private JLabel lblStartTime;
    private JLabel lblEndTime;
    private JLabel lblElapsedTime;
    private JLabel lblRecordsProcessed;
    private JComboBox<String> cmbJobSelection;
    private JComboBox<String> cmbDatasetSelection;

    // Search Interface Components
    private JTextField txtIndexPath;
    private JTextField txtBooksPath;
    private JTextField txtSearchQuery;
    private JTable tblSearchResults;
    private DefaultTableModel modelSearchResults;
    private JTextArea txtBookViewer;
    private JLabel lblBookTitle;
    private JButton btnLoadIndex;
    private JLabel lblExecutionTime;
    private JComboBox<String> cmbSearchDatasetSelection;

    // Match Navigation Components
    private final java.util.List<int[]> highlightOffsets = new java.util.ArrayList<>();
    private int currentHighlightIndex = -1;
    private JLabel lblMatchStatus;
    private JButton btnPrevMatch;
    private JButton btnNextMatch;

    public LibraryGUI() {
        // UI Setup
        setTitle("Distributed Search Engine for Accessible Digital Libraries");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Native Look & Feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Fallback
        }

        initComponents();
        refreshHdfsList("/");
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(28, 40, 51));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel lblTitle = new JLabel("Accessible Digital Library Indexing & Search Engine");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(Color.WHITE);
        headerPanel.add(lblTitle, BorderLayout.WEST);

        JLabel lblSub = new JLabel("WebHDFS REST Interface & Docker Integration | Murat Ağuş");
        lblSub.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        lblSub.setForeground(new Color(174, 182, 191));
        headerPanel.add(lblSub, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        tabbedPane.addTab("HDFS Manager", createHdfsTab());
        tabbedPane.addTab("MapReduce Job Monitor", createJobTab());
        tabbedPane.addTab("Search Interface", createSearchTab());

        add(tabbedPane, BorderLayout.CENTER);
    }

    // ==========================================
    // TAB 1: HDFS MANAGER
    // ==========================================
    private JPanel createHdfsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Navigation top bar
        JPanel navPanel = new JPanel(new BorderLayout(5, 5));
        navPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        navPanel.add(new JLabel("HDFS Path: "), BorderLayout.WEST);
        txtHdfsPath = new JTextField("/");
        txtHdfsPath.setFont(new Font("Consolas", Font.PLAIN, 14));
        navPanel.add(txtHdfsPath, BorderLayout.CENTER);

        JButton btnGo = new JButton("Navigate");
        btnGo.addActionListener(e -> refreshHdfsList(txtHdfsPath.getText().trim()));
        navPanel.add(btnGo, BorderLayout.EAST);
        panel.add(navPanel, BorderLayout.NORTH);

        // File list table
        String[] columns = { "Type", "Name", "Size (bytes)", "Replication", "Block Size" };
        modelHdfsFiles = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblHdfsFiles = new JTable(modelHdfsFiles);
        tblHdfsFiles.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tblHdfsFiles.setRowHeight(22);
        JScrollPane scrollPane = new JScrollPane(tblHdfsFiles);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Sidebar action panel
        JPanel actionPanel = new JPanel(new GridLayout(6, 1, 5, 5));
        actionPanel.setBorder(new EmptyBorder(0, 10, 0, 0));

        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> refreshHdfsList(txtHdfsPath.getText().trim()));
        actionPanel.add(btnRefresh);

        JButton btnMkdir = new JButton("New Folder");
        btnMkdir.addActionListener(e -> createHdfsDirectory());
        actionPanel.add(btnMkdir);

        JButton btnUpload = new JButton("Upload File");
        btnUpload.addActionListener(e -> uploadToHdfs());
        actionPanel.add(btnUpload);

        JButton btnDownload = new JButton("Download Selected");
        btnDownload.addActionListener(e -> downloadFromHdfs());
        actionPanel.add(btnDownload);

        JButton btnRename = new JButton("Rename Selected");
        btnRename.addActionListener(e -> renameInHdfs());
        actionPanel.add(btnRename);

        JButton btnDelete = new JButton("Delete Selected");
        btnDelete.addActionListener(e -> deleteFromHdfs());
        actionPanel.add(btnDelete);

        panel.add(actionPanel, BorderLayout.EAST);
        return panel;
    }

    private void refreshHdfsList(String pathStr) {
        new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                List<WebHDFSClient.HDFSFileStatus> fileStatuses = WebHDFSClient.listStatus(pathStr);
                modelHdfsFiles.setRowCount(0);
                for (WebHDFSClient.HDFSFileStatus status : fileStatuses) {
                    String repl = status.getReplication() > 0 ? String.valueOf(status.getReplication()) : "-";
                    String blk = status.getBlockSize() > 0 ? (status.getBlockSize() / (1024 * 1024)) + " MB" : "-";
                    publish(new Object[] { status.getType(), status.getName(), status.getLength(), repl, blk });
                }
                return null;
            }

            @Override
            protected void process(List<Object[]> chunks) {
                for (Object[] row : chunks) {
                    modelHdfsFiles.addRow(row);
                }
            }

            @Override
            protected void done() {
                try {
                    get();
                    txtHdfsPath.setText(pathStr);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this,
                            "Error listing HDFS directory: " + e.getCause().getMessage(),
                            "HDFS Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void createHdfsDirectory() {
        String folderName = JOptionPane.showInputDialog(this, "Enter folder name:");
        if (folderName == null || folderName.trim().isEmpty()) {
            return;
        }

        String currentPath = txtHdfsPath.getText().trim();
        String fullPath = currentPath.endsWith("/") ? currentPath + folderName : currentPath + "/" + folderName;

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return WebHDFSClient.mkdirs(fullPath);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Folder created successfully.");
                        refreshHdfsList(currentPath);
                    } else {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Folder could not be created.");
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Error creating folder: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void uploadToHdfs() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File localFile = fileChooser.getSelectedFile();
        String currentPath = txtHdfsPath.getText().trim();
        String hdfsDest = currentPath.endsWith("/") ? currentPath + localFile.getName()
                : currentPath + "/" + localFile.getName();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                WebHDFSClient.copyFromLocalFile(localFile.getAbsolutePath(), hdfsDest);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(LibraryGUI.this, "File uploaded successfully.");
                    refreshHdfsList(currentPath);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Error uploading file: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void downloadFromHdfs() {
        int selectedRow = tblHdfsFiles.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an item to download.");
            return;
        }

        String type = (String) modelHdfsFiles.getValueAt(selectedRow, 0);
        String fileName = (String) modelHdfsFiles.getValueAt(selectedRow, 1);
        String currentPath = txtHdfsPath.getText().trim();
        String hdfsSrc = currentPath.endsWith("/") ? currentPath + fileName : currentPath + "/" + fileName;

        JFileChooser fileChooser = new JFileChooser();
        if ("FOLDER".equals(type)) {
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setSelectedFile(new File(fileName));
        } else {
            fileChooser.setSelectedFile(new File(fileName));
        }

        if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File destFile = fileChooser.getSelectedFile();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("FOLDER".equals(type)) {
                    WebHDFSClient.downloadDirectory(hdfsSrc, destFile.getAbsolutePath());
                } else {
                    WebHDFSClient.copyToLocalFile(hdfsSrc, destFile.getAbsolutePath());
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Downloaded successfully.");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Error downloading: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void deleteFromHdfs() {
        int selectedRow = tblHdfsFiles.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an item to delete.");
            return;
        }

        String fileName = (String) modelHdfsFiles.getValueAt(selectedRow, 1);
        String currentPath = txtHdfsPath.getText().trim();
        String hdfsTarget = currentPath.endsWith("/") ? currentPath + fileName : currentPath + "/" + fileName;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete '" + fileName + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return WebHDFSClient.delete(hdfsTarget, true);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Item deleted successfully.");
                        refreshHdfsList(currentPath);
                    } else {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Delete operation failed.");
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Error deleting item: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void renameInHdfs() {
        int selectedRow = tblHdfsFiles.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select an item to rename.");
            return;
        }

        String fileName = (String) modelHdfsFiles.getValueAt(selectedRow, 1);
        String currentPath = txtHdfsPath.getText().trim();
        String hdfsSource = currentPath.endsWith("/") ? currentPath + fileName : currentPath + "/" + fileName;

        String newName = JOptionPane.showInputDialog(this, "Enter new name/path for '" + fileName + "':", fileName);
        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(fileName)) {
            return;
        }

        String hdfsDest = currentPath.endsWith("/") ? currentPath + newName.trim() : currentPath + "/" + newName.trim();

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return WebHDFSClient.rename(hdfsSource, hdfsDest);
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Item renamed successfully.");
                        refreshHdfsList(currentPath);
                    } else {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "Rename operation failed.");
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Error renaming item: " + e.getMessage());
                }
            }
        }.execute();
    }

    // ==========================================
    // TAB 2: JOB MONITOR (MAPREDUCE RUNNER)
    // ==========================================
    private JPanel createJobTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Configuration Inputs
        JPanel configPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        configPanel.setBorder(new TitledBorder("MapReduce Configurations"));

        configPanel.add(new JLabel("Select Dataset Size: "));
        String[] datasets = {
                "Small Dataset (100 Books)",
                "Half Dataset (4000 Books)",
                "All Dataset (8500 Books)"
        };
        cmbDatasetSelection = new JComboBox<>(datasets);
        cmbDatasetSelection.addActionListener(e -> {
            int selectedIndex = cmbDatasetSelection.getSelectedIndex();
            if (selectedIndex == 0) {
                txtJobInputPath.setText("/input/small_books");
                txtJobOutputPath.setText("/output_test_small");
            } else if (selectedIndex == 1) {
                txtJobInputPath.setText("/input/half_books");
                txtJobOutputPath.setText("/output_test_half");
            } else if (selectedIndex == 2) {
                txtJobInputPath.setText("/input/all_books");
                txtJobOutputPath.setText("/output_all_books");
            }
        });
        configPanel.add(cmbDatasetSelection);

        configPanel.add(new JLabel("HDFS Input Path (Books Directory): "));
        txtJobInputPath = new JTextField("/input/small_books");
        configPanel.add(txtJobInputPath);

        configPanel.add(new JLabel("HDFS Output Base Path: "));
        txtJobOutputPath = new JTextField("/output_test_small");
        configPanel.add(txtJobOutputPath);

        configPanel.add(new JLabel("Select MapReduce Job to Launch: "));
        String[] jobs = {
                "Full Pipeline (Jobs 1, 2 & 3)",
                "Job 1 Only (Tokenization)",
                "Job 2 Only (Inverted Index)",
                "Job 3 Only (TF-IDF Scoring)"
        };
        cmbJobSelection = new JComboBox<>(jobs);
        configPanel.add(cmbJobSelection);

        // Progress and Metrics Panel
        JPanel progressPanel = new JPanel(new GridBagLayout());
        progressPanel.setBorder(new TitledBorder("Job Progress & Metrics"));
        GridBagConstraints pgbc = new GridBagConstraints();
        pgbc.fill = GridBagConstraints.HORIZONTAL;
        pgbc.insets = new Insets(4, 8, 4, 8);
        pgbc.weightx = 1.0;

        pgbc.gridx = 0;
        pgbc.gridy = 0;
        progressPanel.add(new JLabel("Map Phase Progress:"), pgbc);
        prgMap = new JProgressBar(0, 100);
        prgMap.setStringPainted(true);
        prgMap.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pgbc.gridy = 1;
        progressPanel.add(prgMap, pgbc);

        pgbc.gridy = 2;
        progressPanel.add(new JLabel("Reduce Phase Progress:"), pgbc);
        prgReduce = new JProgressBar(0, 100);
        prgReduce.setStringPainted(true);
        prgReduce.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pgbc.gridy = 3;
        progressPanel.add(prgReduce, pgbc);

        // Metrics Grid
        JPanel metricsPanel = new JPanel(new GridLayout(2, 2, 10, 5));
        metricsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        lblStartTime = new JLabel("Start Time: --");
        lblEndTime = new JLabel("End Time: --");
        lblElapsedTime = new JLabel("Elapsed Time: --");
        lblRecordsProcessed = new JLabel("Records Processed: --");
        metricsPanel.add(lblStartTime);
        metricsPanel.add(lblEndTime);
        metricsPanel.add(lblElapsedTime);
        metricsPanel.add(lblRecordsProcessed);

        pgbc.gridy = 4;
        progressPanel.add(metricsPanel, pgbc);

        // Trigger Button
        btnRunPipeline = new JButton("Run MapReduce Indexing Pipeline (Docker)");
        btnRunPipeline.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnRunPipeline.setBackground(new Color(46, 204, 113));
        btnRunPipeline.setForeground(Color.WHITE);
        btnRunPipeline.addActionListener(e -> runMapReducePipeline());

        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(configPanel, BorderLayout.NORTH);
        topPanel.add(progressPanel, BorderLayout.CENTER);
        topPanel.add(btnRunPipeline, BorderLayout.SOUTH);
        panel.add(topPanel, BorderLayout.NORTH);

        // Log Console
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBorder(new TitledBorder("Job Execution Console logs"));

        txtConsoleLog = new JTextArea();
        txtConsoleLog.setEditable(false);
        txtConsoleLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtConsoleLog.setBackground(Color.BLACK);
        txtConsoleLog.setForeground(new Color(46, 204, 113)); // Matrix green logs
        JScrollPane scrollConsole = new JScrollPane(txtConsoleLog);

        consolePanel.add(scrollConsole, BorderLayout.CENTER);
        panel.add(consolePanel, BorderLayout.CENTER);

        return panel;
    }

    private void runMapReducePipeline() {
        String inputPath = txtJobInputPath.getText().trim();
        String outputPath = txtJobOutputPath.getText().trim();

        if (inputPath.isEmpty() || outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Input and Output paths cannot be empty.");
            return;
        }

        btnRunPipeline.setEnabled(false);
        btnRunPipeline.setText("Pipeline Running...");
        txtConsoleLog.setText("Initializing MapReduce pipeline in Docker namenode container...\n");

        new SwingWorker<Void, String>() {
            private Process process;

            @Override
            protected Void doInBackground() throws Exception {
                long startTime = System.currentTimeMillis();
                SwingUtilities.invokeLater(() -> {
                    prgMap.setValue(0);
                    prgReduce.setValue(0);
                    lblStartTime.setText("Start Time: " + new java.util.Date(startTime).toString().substring(11, 19));
                    lblEndTime.setText("End Time: Running...");
                    lblElapsedTime.setText("Elapsed Time: 0s");
                    lblRecordsProcessed.setText("Records Processed: --");
                });

                // Start timer task to update elapsed time every second
                javax.swing.Timer timer = new javax.swing.Timer(1000, ev -> {
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    lblElapsedTime.setText("Elapsed Time: " + elapsed + "s");
                });
                timer.start();

                // Determine selected job arg
                String jobSelection = (String) cmbJobSelection.getSelectedItem();
                String jobArg = "all";
                if (jobSelection.contains("Job 1")) {
                    jobArg = "job1";
                } else if (jobSelection.contains("Job 2")) {
                    jobArg = "job2";
                } else if (jobSelection.contains("Job 3")) {
                    jobArg = "job3";
                }

                // Pre-clean HDFS output directories using WebHDFS REST API based on jobArg
                publish("Cleaning up HDFS output paths (if exist)...\n");
                if (jobArg.equals("all") || jobArg.equals("job1")) {
                    WebHDFSClient.delete(outputPath + "/tokens", true);
                }
                if (jobArg.equals("all") || jobArg.equals("job2")) {
                    WebHDFSClient.delete(outputPath + "/index", true);
                }
                if (jobArg.equals("all") || jobArg.equals("job3")) {
                    WebHDFSClient.delete(outputPath + "/tfidf", true);
                }

                publish("Executing: docker exec namenode yarn jar /digital-library-search.jar " + inputPath + " "
                        + outputPath + " " + jobArg + "\n\n");

                // Execute pipeline inside docker container
                ProcessBuilder pb = new ProcessBuilder(
                        "docker", "exec", "namenode", "yarn", "jar",
                        "/digital-library-search.jar", inputPath, outputPath, jobArg);
                pb.redirectErrorStream(true);
                process = pb.start();

                long mapInputRecords = 0;
                long reduceOutputRecords = 0;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line + "\n");

                        // Parse progress
                        if (line.toLowerCase(java.util.Locale.ROOT).contains("map ")
                                && line.toLowerCase(java.util.Locale.ROOT).contains("reduce ")) {
                            Pattern progressPattern = Pattern.compile("(?i)map\\s+(\\d+)%\\s+reduce\\s+(\\d+)%");
                            Matcher progressMatcher = progressPattern.matcher(line);
                            if (progressMatcher.find()) {
                                int mapPercent = Integer.parseInt(progressMatcher.group(1));
                                int reducePercent = Integer.parseInt(progressMatcher.group(2));
                                SwingUtilities.invokeLater(() -> {
                                    prgMap.setValue(mapPercent);
                                    prgReduce.setValue(reducePercent);
                                });
                            }
                        }

                        // Parse records
                        if (line.contains("Map input records=")) {
                            try {
                                mapInputRecords = Long.parseLong(line.split("=")[1].trim());
                            } catch (Exception ignored) {
                            }
                        }
                        if (line.contains("Reduce output records=")) {
                            try {
                                reduceOutputRecords = Long.parseLong(line.split("=")[1].trim());
                            } catch (Exception ignored) {
                            }
                        }

                        if (mapInputRecords > 0) {
                            final long finalMapRecs = mapInputRecords;
                            final long finalRedRecs = reduceOutputRecords;
                            SwingUtilities.invokeLater(() -> {
                                lblRecordsProcessed
                                        .setText("Records: Map In=" + finalMapRecs + " | Red Out=" + finalRedRecs);
                            });
                        }
                    }
                }

                int exitCode = process.waitFor();
                timer.stop();
                long endTime = System.currentTimeMillis();
                long elapsed = (endTime - startTime) / 1000;
                SwingUtilities.invokeLater(() -> {
                    lblEndTime.setText("End Time: " + new java.util.Date(endTime).toString().substring(11, 19));
                    lblElapsedTime.setText("Elapsed Time: " + elapsed + "s");
                    prgMap.setValue(100);
                    prgReduce.setValue(100);
                });

                if (exitCode != 0) {
                    throw new Exception("Hadoop MapReduce pipeline exited inside container with code: " + exitCode);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String logChunk : chunks) {
                    txtConsoleLog.append(logChunk);
                    txtConsoleLog.setCaretPosition(txtConsoleLog.getDocument().getLength());
                }
            }

            @Override
            protected void done() {
                btnRunPipeline.setEnabled(true);
                btnRunPipeline.setText("Run MapReduce Indexing Pipeline (Docker)");
                try {
                    get();
                    JOptionPane.showMessageDialog(LibraryGUI.this,
                            "MapReduce pipeline finished successfully inside Docker!",
                            "Pipeline Finished", JOptionPane.INFORMATION_MESSAGE);
                    // Automatically update index path
                    txtIndexPath.setText(outputPath + "/tfidf/part-r-00000");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this,
                            "Job Pipeline failed: " + e.getCause().getMessage(),
                            "Pipeline Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ==========================================
    // TAB 3: SEARCH INTERFACE
    // ==========================================
    private JPanel createSearchTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();

        // Left Panel - Search Controls and Results
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(new TitledBorder("Inverted Index Search"));

        // Index Path Top Bar
        JPanel indexLoaderPanel = new JPanel(new GridLayout(3, 1, 3, 3));

        JPanel datasetRow = new JPanel(new BorderLayout(5, 0));
        datasetRow.add(new JLabel("Dataset:"), BorderLayout.WEST);
        String[] searchDatasets = {
                "Small Dataset (100 Books)",
                "Half Dataset (4000 Books)",
                "All Dataset (8500 Books)"
        };
        cmbSearchDatasetSelection = new JComboBox<>(searchDatasets);
        cmbSearchDatasetSelection.addActionListener(e -> {
            int selectedIndex = cmbSearchDatasetSelection.getSelectedIndex();
            if (selectedIndex == 0) {
                txtIndexPath.setText("/output_test_small/tfidf/part-r-00000");
                txtBooksPath.setText("/input/small_books");
            } else if (selectedIndex == 1) {
                txtIndexPath.setText("/output_test_half/tfidf/part-r-00000");
                txtBooksPath.setText("/input/half_books");
            } else if (selectedIndex == 2) {
                txtIndexPath.setText("/output_all_books/tfidf/part-r-00000");
                txtBooksPath.setText("/input/all_books");
            }
        });
        datasetRow.add(cmbSearchDatasetSelection, BorderLayout.CENTER);

        JPanel indexRow = new JPanel(new BorderLayout(5, 0));
        indexRow.add(new JLabel("HDFS Index:"), BorderLayout.WEST);
        txtIndexPath = new JTextField("/output_test_small/tfidf/part-r-00000");
        txtIndexPath.setFont(new Font("Consolas", Font.PLAIN, 11));
        indexRow.add(txtIndexPath, BorderLayout.CENTER);
        btnLoadIndex = new JButton("Load Index");
        btnLoadIndex.addActionListener(e -> loadInvertedIndex());
        indexRow.add(btnLoadIndex, BorderLayout.EAST);

        JPanel booksRow = new JPanel(new BorderLayout(5, 0));
        booksRow.add(new JLabel("Books path:"), BorderLayout.WEST);
        txtBooksPath = new JTextField("/input/small_books");
        txtBooksPath.setFont(new Font("Consolas", Font.PLAIN, 11));
        booksRow.add(txtBooksPath, BorderLayout.CENTER);

        indexLoaderPanel.add(datasetRow);
        indexLoaderPanel.add(indexRow);
        indexLoaderPanel.add(booksRow);
        leftPanel.add(indexLoaderPanel, BorderLayout.NORTH);

        // Search panel
        JPanel searchForm = new JPanel(new BorderLayout(5, 5));
        searchForm.setBorder(new EmptyBorder(5, 0, 5, 0));
        txtSearchQuery = new JTextField();
        txtSearchQuery.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtSearchQuery.addActionListener(e -> performSearch());
        searchForm.add(txtSearchQuery, BorderLayout.CENTER);

        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> performSearch());
        searchForm.add(btnSearch, BorderLayout.EAST);

        lblExecutionTime = new JLabel("Query executed in -- ms");
        lblExecutionTime.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblExecutionTime.setForeground(Color.GRAY);

        JPanel searchFormContainer = new JPanel(new BorderLayout());
        searchFormContainer.add(searchForm, BorderLayout.CENTER);
        searchFormContainer.add(lblExecutionTime, BorderLayout.SOUTH);

        JPanel leftCenterPanel = new JPanel(new BorderLayout());
        leftCenterPanel.add(searchFormContainer, BorderLayout.NORTH);

        // Results table
        String[] columns = { "Book Title", "Book ID", "TF-IDF Score" };
        modelSearchResults = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblSearchResults = new JTable(modelSearchResults);
        tblSearchResults.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tblSearchResults.setRowHeight(22);
        tblSearchResults.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = tblSearchResults.getSelectedRow();
                if (selectedRow != -1) {
                    String docId = (String) modelSearchResults.getValueAt(selectedRow, 1);
                    String title = (String) modelSearchResults.getValueAt(selectedRow, 0);
                    loadBookContent(docId, title);
                }
            }
        });

        JScrollPane scrollResults = new JScrollPane(tblSearchResults);
        leftCenterPanel.add(scrollResults, BorderLayout.CENTER);
        leftPanel.add(leftCenterPanel, BorderLayout.CENTER);

        // Right Panel - Book Content Viewer
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(new TitledBorder("Book Reader / Viewer"));

        JPanel headerContainer = new JPanel(new BorderLayout());

        lblBookTitle = new JLabel("No Book Selected");
        lblBookTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblBookTitle.setBorder(new EmptyBorder(0, 0, 5, 0));
        headerContainer.add(lblBookTitle, BorderLayout.NORTH);

        // Navigation Toolbar
        JPanel navigationToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        navigationToolbar.setBorder(new EmptyBorder(0, 0, 5, 0));

        btnPrevMatch = new JButton("◀ Previous Match");
        btnPrevMatch.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnPrevMatch.setEnabled(false);
        btnPrevMatch.addActionListener(e -> prevMatch());

        btnNextMatch = new JButton("Next Match ▶");
        btnNextMatch.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnNextMatch.setEnabled(false);
        btnNextMatch.addActionListener(e -> nextMatch());

        lblMatchStatus = new JLabel("0 matches");
        lblMatchStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblMatchStatus.setBorder(new EmptyBorder(0, 10, 0, 0));

        navigationToolbar.add(btnPrevMatch);
        navigationToolbar.add(btnNextMatch);
        navigationToolbar.add(lblMatchStatus);

        headerContainer.add(navigationToolbar, BorderLayout.CENTER);
        rightPanel.add(headerContainer, BorderLayout.NORTH);

        txtBookViewer = new JTextArea();
        txtBookViewer.setEditable(false);
        txtBookViewer.setFont(new Font("Georgia", Font.PLAIN, 14));
        txtBookViewer.setLineWrap(true);
        txtBookViewer.setWrapStyleWord(true);
        txtBookViewer.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane scrollViewer = new JScrollPane(txtBookViewer);
        rightPanel.add(scrollViewer, BorderLayout.CENTER);

        // Split Layout Constraints
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        // Left Panel taking 40% width
        gbc.gridx = 0;
        gbc.weightx = 0.4;
        panel.add(leftPanel, gbc);

        // Right Panel taking 60% width
        gbc.gridx = 1;
        gbc.weightx = 0.6;
        panel.add(rightPanel, gbc);

        return panel;
    }

    private void loadInvertedIndex() {
        String indexPath = txtIndexPath.getText().trim();
        if (indexPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Index path cannot be empty.");
            return;
        }

        btnLoadIndex.setEnabled(false);
        btnLoadIndex.setText("Loading...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                searchEngine.loadIndex(indexPath);
                return searchEngine.getIndexSize();
            }

            @Override
            protected void done() {
                btnLoadIndex.setEnabled(true);
                btnLoadIndex.setText("Load Index");
                try {
                    int size = get();
                    JOptionPane.showMessageDialog(LibraryGUI.this,
                            "Successfully loaded TF-IDF inverted index into memory.\nUnique vocabulary terms: " + size,
                            "Index Loaded", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(LibraryGUI.this,
                            "Failed to load inverted index: " + e.getCause().getMessage(),
                            "Index Loading Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void performSearch() {
        final String rawQuery = txtSearchQuery.getText().trim();
        if (rawQuery.isEmpty()) {
            return;
        }

        if (searchEngine.getIndexSize() == 0) {
            JOptionPane.showMessageDialog(this,
                    "In-memory index is empty. Please load an index file from HDFS first.",
                    "Index Not Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final boolean hasQuotes = rawQuery.startsWith("\"") && rawQuery.endsWith("\"") && rawQuery.length() > 2;
        final String query = hasQuotes ? rawQuery.substring(1, rawQuery.length() - 1).trim() : rawQuery;

        String[] rawTermsCheck = query.toLowerCase(java.util.Locale.ROOT).split("[^a-zA-Z0-9]+");
        java.util.List<String> termsCheckList = new java.util.ArrayList<>();
        for (String t : rawTermsCheck) {
            if (!t.isEmpty()) {
                termsCheckList.add(t);
            }
        }
        String[] termsCheck = termsCheckList.toArray(new String[0]);
        final boolean runPhraseFilter = termsCheck.length > 1;
        final boolean strictPhrase = hasQuotes;

        lblExecutionTime.setText("Searching...");
        modelSearchResults.setRowCount(0);
        txtBookViewer.setText("");
        lblBookTitle.setText("No Book Selected");

        new SwingWorker<List<Object[]>, Void>() {
            private double durationMs;

            @Override
            protected List<Object[]> doInBackground() throws Exception {
                long startTime = System.nanoTime();
                List<SearchEngine.SearchResult> results = searchEngine.search(query);
                List<Object[]> matchedRows = new java.util.ArrayList<>();

                if (results.isEmpty()) {
                    long endTime = System.nanoTime();
                    durationMs = (endTime - startTime) / 1_000_000.0;
                    return matchedRows;
                }

                if (runPhraseFilter) {
                    // Tokenize query to build exact phrase regex
                    String[] rawTerms = query.toLowerCase(java.util.Locale.ROOT).split("[^a-zA-Z0-9]+");
                    java.util.List<String> termsList = new java.util.ArrayList<>();
                    for (String t : rawTerms) {
                        if (!t.isEmpty()) {
                            termsList.add(t);
                        }
                    }
                    String[] terms = termsList.toArray(new String[0]);

                    StringBuilder phraseRegex = new StringBuilder("\\b");
                    boolean hasValidTerm = false;
                    for (String term : terms) {
                        if (term.isEmpty())
                            continue;
                        if (hasValidTerm) {
                            phraseRegex.append("[^a-zA-Z0-9]+");
                        }
                        phraseRegex.append(Pattern.quote(term));
                        hasValidTerm = true;
                    }
                    phraseRegex.append("\\b");

                    if (hasValidTerm) {
                        Pattern pattern = Pattern.compile(phraseRegex.toString(), Pattern.CASE_INSENSITIVE);
                        int count = 0;
                        for (SearchEngine.SearchResult res : results) {
                            if (count >= 100)
                                break; // Limit phrase matches to top 100
                            String docId = res.getDocId();

                            // Fetch file content from HDFS
                            String inputBase = resolveBooksPath();
                            String bookPath = inputBase.endsWith("/") ? inputBase + docId + ".txt"
                                    : inputBase + "/" + docId + ".txt";

                            try (BufferedReader br = WebHDFSClient.open(bookPath)) {
                                StringBuilder sb = new StringBuilder();
                                char[] buf = new char[8192];
                                int read;
                                while ((read = br.read(buf)) != -1) {
                                    sb.append(buf, 0, read);
                                }
                                if (pattern.matcher(sb.toString()).find()) {
                                    String title = searchEngine.getBookTitle(docId);
                                    matchedRows
                                            .add(new Object[] { title, docId, String.format("%.6f", res.getScore()) });
                                    count++;
                                }
                            } catch (Exception e) {
                                // Skip on error
                            }
                        }
                    }
                } else {
                    // Regular single keyword search (instant in-memory!)
                    for (SearchEngine.SearchResult res : results) {
                        String docId = res.getDocId();
                        String title = searchEngine.getBookTitle(docId);
                        matchedRows.add(new Object[] { title, docId, String.format("%.6f", res.getScore()) });
                    }
                }

                long endTime = System.nanoTime();
                durationMs = (endTime - startTime) / 1_000_000.0;
                return matchedRows;
            }

            @Override
            protected void done() {
                try {
                    List<Object[]> matchedRows = get();
                    lblExecutionTime.setText(String.format("Query executed in %.2f ms", durationMs));

                    if (matchedRows.isEmpty()) {
                        JOptionPane.showMessageDialog(LibraryGUI.this, "No matching documents found.");
                        return;
                    }

                    for (Object[] row : matchedRows) {
                        modelSearchResults.addRow(row);
                    }

                    // Start background title resolver for unresolved titles
                    new SwingWorker<Void, Object[]>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            for (int i = 0; i < modelSearchResults.getRowCount(); i++) {
                                String currentTitle = (String) modelSearchResults.getValueAt(i, 0);
                                String docId = (String) modelSearchResults.getValueAt(i, 1);

                                if (currentTitle.equals("Book #" + docId)) {
                                    String realTitle = fetchBookTitleFromHDFS(docId);
                                    if (!realTitle.equals("Book #" + docId)) {
                                        searchEngine.cacheBookTitle(docId, realTitle);
                                        publish(new Object[] { i, docId, realTitle });
                                    }
                                }
                            }
                            return null;
                        }

                        @Override
                        protected void process(List<Object[]> chunks) {
                            for (Object[] chunk : chunks) {
                                int row = (int) chunk[0];
                                String docId = (String) chunk[1];
                                String newTitle = (String) chunk[2];

                                if (row < modelSearchResults.getRowCount()) {
                                    String currentDocId = (String) modelSearchResults.getValueAt(row, 1);
                                    if (docId.equals(currentDocId)) {
                                        modelSearchResults.setValueAt(newTitle, row, 0);
                                    }
                                }
                            }
                        }
                    }.execute();

                } catch (Exception e) {
                    lblExecutionTime.setText("Search failed.");
                    JOptionPane.showMessageDialog(LibraryGUI.this, "Search error: " + e.getMessage());
                }
            }
        }.execute();
    }

    private String resolveBooksPath() {
        String inputBase = txtBooksPath.getText().trim();
        if (inputBase.isEmpty() || inputBase.contains("small_books") || inputBase.contains("half_books")) {
            inputBase = "/input/all_books";
            try {
                if (WebHDFSClient.listStatus("/input/all_books").isEmpty()) {
                    inputBase = "/input/books";
                }
            } catch (Exception e) {
                inputBase = "/input/books";
            }
        }
        return inputBase;
    }

    private String fetchBookTitleFromHDFS(String docId) {
        String inputBase = resolveBooksPath();
        String bookPath = inputBase.endsWith("/") ? inputBase + docId + ".txt" : inputBase + "/" + docId + ".txt";

        try (BufferedReader br = WebHDFSClient.open(bookPath)) {
            char[] buf = new char[1500];
            int read = br.read(buf);
            if (read > 0) {
                String content = new String(buf, 0, read);
                String title = extractTitleFromContent(content);
                if (title != null && !title.isEmpty()) {
                    return title;
                }
            }
        } catch (Exception e) {
            // Fallback
        }
        return "Book #" + docId;
    }

    private static String extractTitleFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        int titleIdx = content.indexOf("Title: ");
        if (titleIdx == -1) {
            titleIdx = content.indexOf("TITLE: ");
        }
        if (titleIdx == -1) {
            int lowerIdx = content.toLowerCase(java.util.Locale.ROOT).indexOf("title:");
            if (lowerIdx != -1) {
                titleIdx = lowerIdx;
            }
        }

        if (titleIdx != -1) {
            int start = titleIdx + 6;
            if (start < content.length() && content.charAt(start) == ' ') {
                start++;
            }
            int end = content.length();

            String[] markers = { "Author:", "Release Date:", "Release date:", "Language:", "EBook #", "[eBook #",
                    "Posting Date:" };
            for (String marker : markers) {
                int markerIdx = content.indexOf(marker, start);
                if (markerIdx != -1 && markerIdx < end) {
                    end = markerIdx;
                }
            }

            int newlineIdx = content.indexOf('\n', start);
            if (newlineIdx != -1 && newlineIdx < end) {
                end = newlineIdx;
            }
            int carrageIdx = content.indexOf('\r', start);
            if (carrageIdx != -1 && carrageIdx < end) {
                end = carrageIdx;
            }

            if (end > start) {
                String title = content.substring(start, end).trim();
                if (title.endsWith(",")) {
                    title = title.substring(0, title.length() - 1).trim();
                }
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }

        int pgIdx = content.indexOf("The Project Gutenberg eBook of");
        if (pgIdx == -1) {
            pgIdx = content.indexOf("The Project Gutenberg EBook of");
        }
        if (pgIdx != -1) {
            int start = pgIdx + 30;
            int end = content.length();
            int newlineIdx = content.indexOf('\n', start);
            if (newlineIdx != -1 && newlineIdx < end) {
                end = newlineIdx;
            }
            int thisIdx = content.indexOf("This eBook", start);
            if (thisIdx != -1 && thisIdx < end) {
                end = thisIdx;
            }
            if (end > start) {
                String title = content.substring(start, end).trim();
                if (!title.isEmpty()) {
                    return title;
                }
            }
        }

        return null;
    }

    private void loadBookContent(String docId, String title) {
        lblBookTitle.setText("Loading: " + title + "...");
        txtBookViewer.setText("Fetching file content from HDFS...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String inputBase = resolveBooksPath();
                String bookPath = inputBase.endsWith("/") ? inputBase + docId + ".txt"
                        : inputBase + "/" + docId + ".txt";

                // Read character-by-character to handle books stored as single long lines
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = WebHDFSClient.open(bookPath)) {
                    char[] buf = new char[4096];
                    int read;
                    while ((read = br.read(buf)) != -1) {
                        sb.append(buf, 0, read);
                    }
                }
                return sb.toString();
            }

            @Override
            protected void done() {
                try {
                    String content = get();
                    if (content == null || content.trim().isEmpty()) {
                        lblBookTitle.setText("Error - Empty Response");
                        txtBookViewer.setText("HDFS returned empty content for: " + docId + ".txt");
                        return;
                    }
                    lblBookTitle.setText("📚 " + title);
                    txtBookViewer.setText(content);
                    txtBookViewer.setCaretPosition(0);
                    highlightSearchTerms();
                } catch (Exception e) {
                    lblBookTitle.setText("Error Loading Book");
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    txtBookViewer.setText("Failed to load book content:\n\nError: " + cause.getMessage()
                            + "\n\nBook path tried: " + resolveBooksPath() + "/" + docId + ".txt");
                }
            }
        }.execute();
    }

    private void highlightSearchTerms() {
        String query = txtSearchQuery.getText().trim();
        highlightOffsets.clear();
        currentHighlightIndex = -1;

        if (query.isEmpty()) {
            if (lblMatchStatus != null) {
                lblMatchStatus.setText("0 matches");
                btnPrevMatch.setEnabled(false);
                btnNextMatch.setEnabled(false);
            }
            return;
        }

        // If query is in quotes, strip them for highlighting
        boolean isPhraseSearch = query.startsWith("\"") && query.endsWith("\"") && query.length() > 2;
        String cleanQuery = isPhraseSearch ? query.substring(1, query.length() - 1).trim() : query;

        String[] rawTerms = cleanQuery.toLowerCase(java.util.Locale.ROOT).split("[^a-zA-Z0-9]+");
        java.util.List<String> termsList = new java.util.ArrayList<>();
        for (String t : rawTerms) {
            if (!t.isEmpty()) {
                termsList.add(t);
            }
        }
        String[] terms = termsList.toArray(new String[0]);

        String text = txtBookViewer.getText();
        Highlighter highlighter = txtBookViewer.getHighlighter();
        highlighter.removeAllHighlights();

        HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 235, 156));

        // Build a regex for the exact phrase with flexible spacing (e.g.
        // \bwizard\s+of\s+oz\b)
        StringBuilder phraseRegex = new StringBuilder("\\b");
        boolean hasValidTerm = false;
        for (int i = 0; i < terms.length; i++) {
            String term = terms[i].trim();
            if (term.isEmpty())
                continue;
            if (hasValidTerm) {
                phraseRegex.append("[^a-zA-Z0-9]+");
            }
            phraseRegex.append(Pattern.quote(term));
            hasValidTerm = true;
        }
        phraseRegex.append("\\b");

        boolean foundPhrase = false;
        if (hasValidTerm) {
            try {
                Pattern pattern = Pattern.compile(phraseRegex.toString(), Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    highlighter.addHighlight(matcher.start(), matcher.end(), painter);
                    highlightOffsets.add(new int[] { matcher.start(), matcher.end() });
                    foundPhrase = true;
                }
            } catch (Exception e) {
                // Ignore and try fallback
            }
        }

        // Fallback: if the exact phrase was not found (or couldn't be matched),
        // highlight individual keywords
        // No stopwords are filtered out here because if the user explicitly typed it,
        // we highlight it.
        if (!foundPhrase && hasValidTerm && terms.length == 1) {
            java.util.Set<String> stopWords = new java.util.HashSet<>(java.util.Arrays.asList(
                "the", "a", "an", "and", "or", "but", "of", "for", "in", "on", "at", "to", "by", "with", "from",
                "as", "is", "was", "were", "are", "be", "been", "being", "it", "its", "they", "them", "their",
                "this", "that", "these", "those", "i", "you", "he", "she", "we", "his", "her", "him", "my",
                "your", "our", "me", "us", "had", "have", "has", "do", "does", "did", "will", "would", "shall",
                "should", "can", "could", "may", "might", "must"
            ));

            for (String term : terms) {
                term = term.trim();
                if (term.isEmpty())
                    continue;

                // Skip highlighting common stopwords individually if it's a multi-word query
                if (terms.length > 1 && stopWords.contains(term.toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }

                try {
                    Pattern pattern = Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(text);
                    while (matcher.find()) {
                        highlighter.addHighlight(matcher.start(), matcher.end(), painter);
                        highlightOffsets.add(new int[] { matcher.start(), matcher.end() });
                    }
                } catch (Exception e) {
                    int index = 0;
                    String lowerText = text.toLowerCase(java.util.Locale.ROOT);
                    while ((index = lowerText.indexOf(term, index)) >= 0) {
                        try {
                            highlighter.addHighlight(index, index + term.length(), painter);
                            highlightOffsets.add(new int[] { index, index + term.length() });
                        } catch (Exception ignored) {
                        }
                        index += term.length();
                    }
                }
            }
        }

        // Sort highlightOffsets by start offset ascending
        java.util.Collections.sort(highlightOffsets, (a, b) -> Integer.compare(a[0], b[0]));

        if (!highlightOffsets.isEmpty()) {
            currentHighlightIndex = 0;
            lblMatchStatus.setText("Match 1 of " + highlightOffsets.size());
            btnPrevMatch.setEnabled(true);
            btnNextMatch.setEnabled(true);
            scrollToMatch(0);
        } else {
            lblMatchStatus.setText("0 matches");
            btnPrevMatch.setEnabled(false);
            btnNextMatch.setEnabled(false);
        }
    }

    private void scrollToMatch(int index) {
        if (index >= 0 && index < highlightOffsets.size()) {
            int[] range = highlightOffsets.get(index);
            txtBookViewer.setCaretPosition(range[0]);
            txtBookViewer.select(range[0], range[1]);
            lblMatchStatus.setText("Match " + (index + 1) + " of " + highlightOffsets.size());
        }
    }

    private void nextMatch() {
        if (highlightOffsets.isEmpty())
            return;
        currentHighlightIndex = (currentHighlightIndex + 1) % highlightOffsets.size();
        scrollToMatch(currentHighlightIndex);
    }

    private void prevMatch() {
        if (highlightOffsets.isEmpty())
            return;
        currentHighlightIndex = (currentHighlightIndex - 1 + highlightOffsets.size()) % highlightOffsets.size();
        scrollToMatch(currentHighlightIndex);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LibraryGUI().setVisible(true));
    }
}
