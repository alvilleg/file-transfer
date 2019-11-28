package as;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Hello world!
 */
public class App {
    String hostnameSource;
    String hostnameTarget;
    String usernameTarget;
    String passwordTarget;
    String usernameSource;
    String passwordSource;
    String fullPathToDownload;
    String localTargetPath;
    String remoteTargetPath;
    String localFileName;
    String dbControlName;
    Map<String, String> failFiles = new HashMap<>();

    public void setHostnameSource(String hostnameSource) {
        this.hostnameSource = hostnameSource;
    }

    public void setHostnameTarget(String hostnameTarget) {
        this.hostnameTarget = hostnameTarget;
    }

    public void setUsernameTarget(String usernameTarget) {
        this.usernameTarget = usernameTarget;
    }

    public void setPasswordTarget(String passwordTarget) {
        this.passwordTarget = passwordTarget;
    }

    public void setUsernameSource(String usernameSource) {
        this.usernameSource = usernameSource;
    }

    public void setPasswordSource(String passwordSource) {
        this.passwordSource = passwordSource;
    }

    public void setFullPathToDownload(String fullPathToDownload) {
        this.fullPathToDownload = fullPathToDownload;
    }

    public void setLocalTargetPath(String localTargetPath) {
        this.localTargetPath = localTargetPath;
    }

    public void setRemoteTargetPath(String remoteTargetPath) {
        this.remoteTargetPath = remoteTargetPath;
    }

    public void setLocalFileName(String localFileName) {
        this.localFileName = localFileName;
    }

    public void setDbControlName(String dbControlName) {
        this.dbControlName = dbControlName;
    }

    public static void main(String[] args) throws Exception {

        App app = new App();
        app.setHostnameTarget(System.getProperty("hostnameTarget"));
        app.setHostnameSource(System.getProperty("hostnameSource"));
        //
        app.setPasswordTarget(System.getProperty("passwordTarget"));
        app.setPasswordSource(System.getProperty("passwordSource"));
        //
        app.setUsernameTarget(System.getProperty("usernameTarget"));
        app.setUsernameSource(System.getProperty("usernameSource"));
        //
        app.setFullPathToDownload(System.getProperty("fullPathToDownload"));
        app.setLocalTargetPath(System.getProperty("localTargetPath"));
        app.setRemoteTargetPath(System.getProperty("remoteTargetPath"));
        app.setLocalFileName(System.getProperty("localFileName"));
        app.setDbControlName(System.getProperty("dbControlName"));
        app.createDB();
        try {
            app.downloadFile(app.fullPathToDownload, app.localFileName);
        } catch (Exception exc) {
            System.out.println("Failure reading files from: " + app.fullPathToDownload);
            exc.printStackTrace();
        }

        System.exit(0);
    }

    private void closeDbConn(Connection connection) {

        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }

    }

    public Connection connect() {
        Connection conn = null;
        try {
            // db parameters
            String url = "jdbc:sqlite:file_transfer_" + dbControlName + ".db";
            // create a connection to the database
            conn = DriverManager.getConnection(url);
            conn.setAutoCommit(true);
            System.out.println("Connection to SQLite has been established.");

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    private void createDB() throws SQLException {
        Connection connection = connect();
        Statement statement = connection.createStatement();
        statement.execute("Create table if not exists files_to_process(file_name text primary key)");
        statement.execute("Create table if not exists processed_files(file_name text primary key)");
        closeDbConn(connection);
    }

    public void insertFileToProcess(String fileName) throws SQLException {
        Connection connection = connect();
        PreparedStatement pstmt = connection.prepareStatement("Insert into files_to_process(file_name ) values (?)");
        pstmt.setString(1, fileName);
        pstmt.executeUpdate();
        closeDbConn(connection);
    }

    public void insertProcessedFile(String fileName) throws SQLException {
        Connection connection = connect();
        PreparedStatement pstmt = connection.prepareStatement("Insert into processed_files(file_name) values (?)");
        pstmt.setString(1, fileName);
        pstmt.executeUpdate();
        closeDbConn(connection);
    }

    public void unzip(String zipFilePath, FTPClient ftp, int count, long total) throws IOException {
        String fileZip = zipFilePath;
        File destDir = new File(localTargetPath);
        destDir.mkdirs();
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        List<String> order = Arrays.asList("I", "IR", "IP", "IPR", "AP", "APR", "UN", "UR", "A", "AR");
        Map<String, List<String>> filePathsByType = new LinkedHashMap<>();
        order.stream().forEach((s) -> filePathsByType.put(s, new ArrayList<>()));
        StopWatch watch = new StopWatch();
        watch.start();
        int countEntries = 0;
        while (zipEntry != null) {

            File newFile = new File(destDir, zipEntry.getName());
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
            String fileName = newFile.getName();
            String priority = fileName.split("_")[7];
            List<String> files = filePathsByType.get(priority);
            files.add(newFile.getCanonicalPath());
            filePathsByType.put(priority, files);
            countEntries++;
        }

        int countUploaded = 0;
        for (String s : order) {
            System.out.println("Sending priority type: " + s);
            for (String filePath : filePathsByType.get(s)) {
                try {
                    uploadFTP(filePath, ftp);
                    countUploaded++;
                } catch (Exception exc) {
                    System.out.println("Fail uploading ... " + filePath);
                    exc.printStackTrace();
                    failFiles.put(zipFilePath, filePath);
                }
            }

            filePathsByType.remove(s);
        }

        for (Map.Entry<String, List<String>> missing : filePathsByType.entrySet()) {
            for (String filePath : missing.getValue()) {
                try {
                    uploadFTP(filePath, ftp);
                    countUploaded++;
                } catch (Exception e) {
                    System.out.println("Fail uploading ... " + filePath);
                    e.printStackTrace();
                    failFiles.put(zipFilePath, filePath);
                }
            }
        }

        zis.closeEntry();
        zis.close();
        watch.stop();
        System.err.println(count + "/" + total + "====>>> UNZIP Time Elapsed: [" + watch.getTime() + "ms] <<<==== " + countUploaded + "/" + countEntries + " files ");
        failFiles.entrySet().stream().forEach(System.err::println);
    }

    public void downloadFile(String fileToDownload, String fileName) throws Exception {
        //
        final FTPClient ftp = new FTPClient();
        ftp.setDataTimeout(5000);
        ftp.setConnectTimeout(5000);

        final SSHClient sshClient = setupSshj();
        final SFTPClient sftpClient = sshClient.newSFTPClient();
        //ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(new OutputStreamWriter(System.err, "UTF-8")), true));
        try {

            ftp.connect(hostnameTarget, 21);
            int reply = ftp.getReplyCode();
            System.out.println("Reply ::: " + reply);
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new Exception("Exception in connecting to FTP Server");
            }
            boolean logged = ftp.login(usernameTarget, passwordTarget);
            if (!logged) {
                throw new Exception("Couldn't log ");
            }
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.ASCII_FILE_TYPE);
            ftp.setFileTransferMode(FTP.COMPRESSED_TRANSFER_MODE);
            //
            List<RemoteResourceInfo> remoteResourceInfos = sftpClient.ls(fileToDownload);

            /*remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).forEach(rsi -> {
                try {
                    insertFileToProcess(rsi.getPath());
                } catch (SQLException e) {
                    System.out.println("Error inserting file name");
                    e.printStackTrace();
                }
            });*/
            int count[] = {0};
            long total = remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).count();
            remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).forEach(rsi -> {
                System.out.println("Downloading..." + rsi.getPath());

                try {
                    sftpClient.get(rsi.getPath(), localTargetPath + rsi.getName());

                    unzip(localTargetPath + rsi.getName(), ftp, ++count[0], total);
                    //insertProcessedFile(rsi.getPath());
                } catch (IOException e) {
                    System.out.println("Fail downloading: " + rsi.getPath());
                    e.printStackTrace();
                } catch (Exception e) {
                    System.out.println("Fail downloading: " + rsi.getPath());
                    e.printStackTrace();
                }
            });
        } catch (Exception exc) {

        } finally {
            sftpClient.close();
            sshClient.disconnect();
            ftp.logout();
            ftp.disconnect();
        }

    }

    public void upload(String fullFilePath, String fileName) throws IOException {
        SSHClient sshClient = setupSshj();
        SFTPClient sftpClient = sshClient.newSFTPClient();

        sftpClient.put(fullFilePath, remoteTargetPath + fileName);

        sftpClient.close();
        sshClient.disconnect();
    }

    public void uploadFTP(String fullFilePath, FTPClient ftp) throws Exception {


        File file = new File(fullFilePath);
        InputStream in = new FileInputStream(file);
        try {
            ftp.storeFile(remoteTargetPath + file.getName(), in);
            IOUtils.closeQuietly(in);
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }


    private SSHClient setupSshj() throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(hostnameSource);
        client.authPassword(usernameSource, passwordSource);
        return client;
    }
}
