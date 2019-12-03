package as;

import ch.qos.logback.core.ConsoleAppender;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;


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

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(App.class);

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
        logger.info("Start app execution");
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
            logger.debug("Failure reading files from: " + app.fullPathToDownload);
            logger.error(null, exc);
            app.showDialogError("Error processing files", "Error");
        }

        logger.info("End app execution");
        System.exit(0);
    }

    private void closeDbConn(Connection connection) {

        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ex) {
            logger.debug(ex.getMessage());
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
            logger.debug("Connection to SQLite has been established.");

        } catch (SQLException e) {
            logger.debug(e.getMessage());
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
            logger.debug("Sending priority type: " + s);
            for (String filePath : filePathsByType.get(s)) {
                try {
                    uploadFTP(filePath, ftp);
                    countUploaded++;
                } catch (Exception exc) {
                    logger.debug("Fail uploading ... " + filePath);
                    logger.error(null, exc);
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
                    logger.debug("Fail uploading ... " + filePath);
                    logger.error(null, e);
                    failFiles.put(zipFilePath, filePath);
                }
            }
        }

        zis.closeEntry();
        zis.close();
        watch.stop();
        logger.info(count + "/" + total + "====>>> UNZIP Time Elapsed: [" + watch.getTime() + "ms] <<<==== " + countUploaded + "/" + countEntries + " files ");
        failFiles.entrySet().stream().forEach(System.err::println);
    }

    public void downloadFile(String fileToDownload, String fileName) throws Exception {
        //
        final FTPClient ftp = new FTPClient();
        ftp.setDataTimeout(5000);
        ftp.setConnectTimeout(5000);
        SSHClient sshClient = null;
        SFTPClient sftpClient = null;
        try {
            sshClient = setupSshj();
            sftpClient = sshClient.newSFTPClient();
        } catch (Exception exc) {
            showDialogError("Couldn't reach the source sever", "Source connection error");
        }
        //ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(new OutputStreamWriter(System.err, "UTF-8")), true));
        try {

            ftp.connect(hostnameTarget, 21);
            int reply = ftp.getReplyCode();
            logger.debug("Reply ::: " + reply);
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
            List<RemoteResourceInfo> remoteResourceInfos = new LinkedList<>();
            try {
                remoteResourceInfos = sftpClient.ls(fileToDownload);
            } catch (Exception e) {
                logger.error(null, e);
                e.printStackTrace();
                showDialogError("Couldn't read files from: " + fullPathToDownload, "Reading source error");
            }
            /*remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).forEach(rsi -> {
                try {
                    insertFileToProcess(rsi.getPath());
                } catch (SQLException e) {
                    logger.debug("Error inserting file name");
                    e.printStackTrace();
                }
            });*/
            int count[] = {0};
            final SFTPClient sftpClient_ = sftpClient;
            long total = remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).count();

            remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).forEach(rsi -> {
                logger.debug("Downloading..." + rsi.getPath());

                try {
                    sftpClient_.get(rsi.getPath(), localTargetPath + rsi.getName());

                    unzip(localTargetPath + rsi.getName(), ftp, ++count[0], total);
                    //insertProcessedFile(rsi.getPath());
                } catch (IOException e) {
                    logger.debug("Fail downloading: " + rsi.getPath());
                    logger.error(null, e);
                } catch (Exception e) {
                    logger.debug("Fail downloading: " + rsi.getPath());
                    logger.error(null, e);
                }
            });
        } catch (Exception exc) {
            logger.error(null, exc);
            showDialogError("Error processing files", "Error");
        } finally {
            if (sftpClient != null) {
                sftpClient.close();
            }
            if (sshClient != null) {
                sshClient.disconnect();
            }
            ftp.logout();
            ftp.disconnect();
        }

    }

    private void showDialogError(String message, String title) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.ERROR_MESSAGE);
        JDialog dlg = pane.createDialog(message);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.addComponentListener(new ComponentAdapter() {
            @Override
            // =====================
            public void componentShown(ComponentEvent e) {
                // =====================
                super.componentShown(e);
                Timer t;
                t = new Timer(1000, new ActionListener() {
                    @Override
                    // =====================
                    public void actionPerformed(ActionEvent e) {
                        // =====================
                        dlg.setVisible(false);
                    }
                });
                t.setRepeats(false);
                t.start();
            }
        });
        dlg.setVisible(true);
        System.out.println("Finished");
        dlg.dispose();
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
            logger.error(null, exc);
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
