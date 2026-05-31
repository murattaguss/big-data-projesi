package com.bdata;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.net.URI;

/**
 * HDFS Client class to interact with Hadoop HDFS.
 * It provides basic file operations like mkdir, put, get, delete, rename, and list.
 */
public class HDFSClient {

    private static final String HDFS_URI = "hdfs://localhost:9000";

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        // Set Hadoop user to root to avoid permission problems on Docker container
        System.setProperty("HADOOP_USER_NAME", "root");

        Configuration conf = new Configuration();
        // Define default file system URL
        conf.set("fs.defaultFS", HDFS_URI);

        String command = args[0].toLowerCase(java.util.Locale.ROOT);

        try (FileSystem fs = FileSystem.get(URI.create(HDFS_URI), conf)) {
            switch (command) {
                case "mkdir":
                    if (args.length < 2) {
                        System.out.println("Error: Directory path is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    makeDirectory(fs, args[1]);
                    break;

                case "put":
                    if (args.length < 3) {
                        System.out.println("Error: Local source path or HDFS destination path is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    uploadFile(fs, args[1], args[2]);
                    break;

                case "get":
                    if (args.length < 3) {
                        System.out.println("Error: HDFS source path or local destination path is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    downloadFile(fs, args[1], args[2]);
                    break;

                case "delete":
                    if (args.length < 2) {
                        System.out.println("Error: HDFS path to delete is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    deleteFile(fs, args[1]);
                    break;

                case "rename":
                    if (args.length < 3) {
                        System.out.println("Error: Source or destination HDFS path is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    renameFile(fs, args[1], args[2]);
                    break;

                case "list":
                    if (args.length < 2) {
                        System.out.println("Error: HDFS directory path to list is missing.");
                        printUsage();
                        System.exit(1);
                    }
                    listStatus(fs, args[1]);
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    printUsage();
                    break;
            }
        } catch (Exception e) {
            System.err.println("An error occurred during operation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prints helper usage guide
     */
    private static void printUsage() {
        System.out.println("\n=== HDFS Client Usage ===");
        System.out.println("1. Create Directory:      java com.bdata.HDFSClient mkdir <hdfs_directory_path>");
        System.out.println("2. Upload File (Local -> HDFS): java com.bdata.HDFSClient put <local_file_path> <hdfs_destination_path>");
        System.out.println("3. Download File (HDFS -> Local): java com.bdata.HDFSClient get <hdfs_file_path> <local_destination_path>");
        System.out.println("4. Delete File/Folder:   java com.bdata.HDFSClient delete <hdfs_path>");
        System.out.println("5. Rename/Move:          java com.bdata.HDFSClient rename <hdfs_source_path> <hdfs_destination_path>");
        System.out.println("6. List Directory:      java com.bdata.HDFSClient list <hdfs_directory_path>");
    }

    /**
     * Creates directory in HDFS
     */
    private static void makeDirectory(FileSystem fs, String hdfsPathStr) throws IOException {
        Path path = new Path(hdfsPathStr);
        if (fs.exists(path)) {
            System.out.println("Info: Directory '" + hdfsPathStr + "' already exists in HDFS.");
            return;
        }
        boolean isCreated = fs.mkdirs(path);
        if (isCreated) {
            System.out.println("Success: Created HDFS directory: " + hdfsPathStr);
        } else {
            System.out.println("Failure: Could not create directory.");
        }
    }

    /**
     * Uploads local file to HDFS
     */
    private static void uploadFile(FileSystem fs, String localPathStr, String hdfsPathStr) throws IOException {
        Path localPath = new Path(localPathStr);
        Path hdfsPath = new Path(hdfsPathStr);
        
        System.out.println("Uploading: " + localPathStr + " -> HDFS: " + hdfsPathStr);
        fs.copyFromLocalFile(false, true, localPath, hdfsPath);
        System.out.println("Success: File uploaded to HDFS.");
    }

    /**
     * Downloads file from HDFS to local path
     */
    private static void downloadFile(FileSystem fs, String hdfsPathStr, String localPathStr) throws IOException {
        Path hdfsPath = new Path(hdfsPathStr);
        Path localPath = new Path(localPathStr);
        
        System.out.println("Downloading: HDFS: " + hdfsPathStr + " -> Local: " + localPathStr);
        fs.copyToLocalFile(false, hdfsPath, localPath, true);
        System.out.println("Success: File downloaded to local filesystem.");
    }

    /**
     * Deletes file or directory recursively
     */
    private static void deleteFile(FileSystem fs, String hdfsPathStr) throws IOException {
        Path path = new Path(hdfsPathStr);
        if (!fs.exists(path)) {
            System.out.println("Error: Target path '" + hdfsPathStr + "' not found in HDFS.");
            return;
        }
        boolean isDeleted = fs.delete(path, true);
        if (isDeleted) {
            System.out.println("Success: Deleted '" + hdfsPathStr + "' from HDFS.");
        } else {
            System.out.println("Failure: Could not delete path.");
        }
    }

    /**
     * Renames or moves a file or folder in HDFS
     */
    private static void renameFile(FileSystem fs, String srcPathStr, String destPathStr) throws IOException {
        Path src = new Path(srcPathStr);
        Path dest = new Path(destPathStr);
        if (!fs.exists(src)) {
            System.out.println("Error: Source '" + srcPathStr + "' not found in HDFS.");
            return;
        }
        boolean isRenamed = fs.rename(src, dest);
        if (isRenamed) {
            System.out.println("Success: Moved '" + srcPathStr + "' to '" + destPathStr + "'.");
        } else {
            System.out.println("Failure: Could not move/rename path.");
        }
    }

    /**
     * Lists HDFS directory status
     */
    private static void listStatus(FileSystem fs, String hdfsPathStr) throws IOException {
        Path path = new Path(hdfsPathStr);
        if (!fs.exists(path)) {
            System.out.println("Error: Directory '" + hdfsPathStr + "' not found in HDFS.");
            return;
        }

        FileStatus[] fileStatuses = fs.listStatus(path);
        System.out.println("\n=== HDFS Directory Content: " + hdfsPathStr + " ===");
        if (fileStatuses.length == 0) {
            System.out.println("(Directory is empty)");
            return;
        }

        for (FileStatus status : fileStatuses) {
            String type = status.isDirectory() ? "[FOLDER]" : "[FILE]  ";
            long size = status.getLen();
            short replication = status.getReplication();
            long blockSize = status.getBlockSize();
            System.out.printf("%s %-20s (Size: %d bytes, Replication: %d, Block Size: %d)%n", 
                    type, status.getPath().getName(), size, replication, blockSize);
        }
    }
}
