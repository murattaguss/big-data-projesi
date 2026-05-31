package com.bdata;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebHDFS REST API Client. Operates entirely over HTTP, allowing host compatibility
 * with any Java version (including Java 24) without requiring local Hadoop binaries or libraries.
 * Handles Docker-to-Host network address translation for redirects.
 */
public class WebHDFSClient {

    private static final String BASE_URL = "http://localhost:9870/webhdfs/v1";

    public static class HDFSFileStatus {
        private final String type; // "FOLDER" or "FILE"
        private final String name;
        private final long length;
        private final long blockSize;
        private final int replication;

        public HDFSFileStatus(String type, String name, long length, long blockSize, int replication) {
            this.type = type;
            this.name = name;
            this.length = length;
            this.blockSize = blockSize;
            this.replication = replication;
        }

        public String getType() { return type; }
        public String getName() { return name; }
        public long getLength() { return length; }
        public long getBlockSize() { return blockSize; }
        public int getReplication() { return replication; }
    }

    /**
     * Translates internal Docker container network locations returned in redirects
     * to the corresponding exposed localhost ports on the host Windows machine.
     */
    private static String translateLocation(String location) {
        if (location == null) {
            return null;
        }
        // datanode2 internal port 9864 is mapped to localhost port 9865
        if (location.contains("datanode2:9864")) {
            return location.replace("datanode2:9864", "localhost:9865");
        }
        // datanode internal port 9864 is mapped to localhost port 9864
        if (location.contains("datanode:9864")) {
            return location.replace("datanode:9864", "localhost:9864");
        }
        // namenode internal port 9870 is mapped to localhost port 9870
        if (location.contains("namenode:9870")) {
            return location.replace("namenode:9870", "localhost:9870");
        }
        return location;
    }

    // Helper for sending GET requests and reading responses as Strings
    private static String getRequest(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " - " + conn.getResponseMessage());
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    // Helper for simple PUT requests (mkdirs, rename, etc.)
    private static boolean putRequest(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        int responseCode = conn.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED;
    }

    // Helper for DELETE requests
    private static boolean deleteRequest(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        int responseCode = conn.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    /**
     * Lists HDFS directory contents.
     */
    public static List<HDFSFileStatus> listStatus(String path) throws IOException {
        String encodedPath = encodePath(path);
        String url = BASE_URL + encodedPath + "?op=LISTSTATUS";
        String json = getRequest(url);

        List<HDFSFileStatus> results = new ArrayList<>();
        // Parse JSON file status arrays using regular expressions
        Pattern statusPattern = Pattern.compile("\\{[^}]*\\}");
        Matcher statusMatcher = statusPattern.matcher(json);
        while (statusMatcher.find()) {
            String statusJson = statusMatcher.group();
            if (!statusJson.contains("pathSuffix")) continue;

            String name = getJsonStringField(statusJson, "pathSuffix");
            String type = getJsonStringField(statusJson, "type");
            long length = getJsonLongField(statusJson, "length");
            long blockSize = getJsonLongField(statusJson, "blockSize");
            int replication = (int) getJsonLongField(statusJson, "replication");

            results.add(new HDFSFileStatus("DIRECTORY".equals(type) ? "FOLDER" : "FILE", name, length, blockSize, replication));
        }
        return results;
    }

    private static String getJsonStringField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"[ \\t]*:[ \\t]*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static long getJsonLongField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"[ \\t]*:[ \\t]*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return 0;
    }

    /**
     * Creates a directory on HDFS.
     */
    public static boolean mkdirs(String path) throws IOException {
        String encodedPath = encodePath(path);
        String url = BASE_URL + encodedPath + "?op=MKDIRS&user.name=root";
        return putRequest(url);
    }

    /**
     * Renames or moves a file or directory on HDFS.
     */
    public static boolean rename(String src, String dest) throws IOException {
        String encodedPath = encodePath(src);
        String encodedDest = encodePath(dest);
        String url = BASE_URL + encodedPath + "?op=RENAME&destination=" + encodedDest + "&user.name=root";
        return putRequest(url);
    }

    /**
     * Deletes a file or directory on HDFS.
     */
    public static boolean delete(String path, boolean recursive) throws IOException {
        String encodedPath = encodePath(path);
        String url = BASE_URL + encodedPath + "?op=DELETE&recursive=" + recursive + "&user.name=root";
        return deleteRequest(url);
    }

    /**
     * Opens an HDFS file and returns a reader for its content.
     */
    public static BufferedReader open(String path) throws IOException {
        String encodedPath = encodePath(path);
        String urlStr = BASE_URL + encodedPath + "?op=OPEN";
        
        // Fetch with redirected address handled manually to support docker port-forwarding
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(false); // Manually handle redirect
        int responseCode = conn.getResponseCode();

        if (responseCode == 307 || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
            String redirectUrl = translateLocation(conn.getHeaderField("Location"));
            URL targetUrl = new URL(redirectUrl);
            HttpURLConnection targetConn = (HttpURLConnection) targetUrl.openConnection();
            targetConn.setRequestMethod("GET");
            responseCode = targetConn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + " - " + targetConn.getResponseMessage());
            }
            return new BufferedReader(new InputStreamReader(targetConn.getInputStream(), StandardCharsets.UTF_8));
        }

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode + " - " + conn.getResponseMessage());
        }
        return new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Uploads a local file to HDFS.
     */
    public static void copyFromLocalFile(String localPath, String hdfsDest) throws IOException {
        String encodedPath = encodePath(hdfsDest);
        String urlStr = BASE_URL + encodedPath + "?op=CREATE&overwrite=true&user.name=root";
        
        // Step 1: Initial request to NameNode (disabling automatic redirect)
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("PUT");
        conn.setInstanceFollowRedirects(false);
        int responseCode = conn.getResponseCode();
        
        if (responseCode != 307) {
            throw new IOException("Expected 307 Redirect from NameNode, got: " + responseCode);
        }
        
        String uploadUrl = translateLocation(conn.getHeaderField("Location"));
        if (uploadUrl == null) {
            throw new IOException("Redirect location header missing.");
        }
        
        // Step 2: Upload raw file stream to the translated DataNode endpoint
        URL datanodeUrl = new URL(uploadUrl);
        HttpURLConnection dnConn = (HttpURLConnection) datanodeUrl.openConnection();
        dnConn.setRequestMethod("PUT");
        dnConn.setDoOutput(true);
        
        try (OutputStream out = dnConn.getOutputStream();
             FileInputStream in = new FileInputStream(localPath)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        int dnResponse = dnConn.getResponseCode();
        if (dnResponse != HttpURLConnection.HTTP_CREATED && dnResponse != HttpURLConnection.HTTP_OK) {
            throw new IOException("DataNode upload failed with code: " + dnResponse);
        }
    }

    /**
     * Downloads a file from HDFS to a local file.
     */
    public static void copyToLocalFile(String hdfsSrc, String localDest) throws IOException {
        try (BufferedReader br = open(hdfsSrc);
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(localDest), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int charsRead;
            while ((charsRead = br.read(buffer)) != -1) {
                bw.write(buffer, 0, charsRead);
            }
        }
    }

    /**
     * Recursively downloads a directory from HDFS to local filesystem.
     */
    public static void downloadDirectory(String hdfsSrc, String localDest) throws IOException {
        File localDir = new File(localDest);
        if (!localDir.exists()) {
            localDir.mkdirs();
        }
        List<HDFSFileStatus> statuses = listStatus(hdfsSrc);
        for (HDFSFileStatus status : statuses) {
            String name = status.getName();
            String subHdfs = hdfsSrc.endsWith("/") ? hdfsSrc + name : hdfsSrc + "/" + name;
            String subLocal = localDest + File.separator + name;
            if ("FOLDER".equals(status.getType())) {
                downloadDirectory(subHdfs, subLocal);
            } else {
                copyToLocalFile(subHdfs, subLocal);
            }
        }
    }

    private static String encodePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append("/");
                try {
                    sb.append(URLEncoder.encode(part, "UTF-8").replace("+", "%20"));
                } catch (UnsupportedEncodingException e) {
                    sb.append(part);
                }
            }
        }
        return sb.length() == 0 ? "/" : sb.toString();
    }
}
