package as;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


import java.io.*;
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

        try {
            app.downloadFile(app.fullPathToDownload, app.localFileName);
        } catch (Exception exc) {
            System.out.println("Failure reading files from: " + app.fullPathToDownload);
            exc.printStackTrace();
        }

        System.exit(0);
    }

    public void unzip(String zipFilePath) throws Exception {
        String fileZip = zipFilePath;
        File destDir = new File(localTargetPath);
        destDir.mkdirs();
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        List<String> order = Arrays.asList("I", "IR", "IP", "IPR", "AP", "APR", "UN", "UR", "A", "AR");
        Map<String, List<String>> filePathsByType = new LinkedHashMap<>();
        order.stream().forEach((s) -> filePathsByType.put(s, new ArrayList<>()));

        while (zipEntry != null) {

            File newFile = new File(destDir, zipEntry.getName());

            System.out.println("Zip entry Name : " + zipEntry.getName());

            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            System.out.println("Zip entry: " + zipEntry.getName());
            fos.close();
            zipEntry = zis.getNextEntry();
            String fileName = newFile.getName();
            String priority = fileName.split("_")[7];
            List<String> files = filePathsByType.get(priority);
            files.add(newFile.getCanonicalPath());
            filePathsByType.put(priority, files);
        }


        for (String s : order) {
            System.out.println("Sending priority type: " + s);
            for (String filePath : filePathsByType.get(s)) {
                try {
                    uploadFTP(filePath);
                } catch (Exception exc) {
                    System.out.println("Fail uploading ... " + filePath);
                    exc.printStackTrace();
                }
            }
            Thread.sleep(1000);
            filePathsByType.remove(s);
        }

        for (Map.Entry<String, List<String>> missing : filePathsByType.entrySet()) {
            for (String filePath : missing.getValue()) {
                uploadFTP(filePath);
            }
        }

        zis.closeEntry();
        zis.close();
    }

    public void downloadFile(String fileToDownload, String fileName) throws IOException {
        SSHClient sshClient = setupSshj();
        SFTPClient sftpClient = sshClient.newSFTPClient();
        List<RemoteResourceInfo> remoteResourceInfos = sftpClient.ls(fileToDownload);
        remoteResourceInfos.stream().filter((s) -> s.getName().contains(".zip")).forEach(rsi -> {
            System.out.println("Downloading..." + rsi.getPath());

            try {
                sftpClient.get(rsi.getPath(), localTargetPath + rsi.getName());

                unzip(localTargetPath + rsi.getName());

            } catch (IOException e) {
                System.out.println("Fail downloading: " + rsi.getPath());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Fail downloading: " + rsi.getPath());
                e.printStackTrace();
            }
        });
        sftpClient.close();
        sshClient.disconnect();
    }

    public void upload(String fullFilePath, String fileName) throws IOException {
        SSHClient sshClient = setupSshj();
        SFTPClient sftpClient = sshClient.newSFTPClient();

        sftpClient.put(fullFilePath, remoteTargetPath + fileName);

        sftpClient.close();
        sshClient.disconnect();
    }

    public void uploadFTP(String fullFilePath) throws Exception {
        System.out.println("Start uploading for " + fullFilePath);
        FTPClient ftp = new FTPClient();
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
        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        File file = new File(fullFilePath);
        InputStream in = new FileInputStream(file);
        OutputStream outputStream = ftp.storeFileStream(remoteTargetPath + file.getName());
        System.out.println("Sending: ... fullPath: " + fullFilePath + " Name: " + file.getName());
        IOUtils.copy(in, outputStream);
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(outputStream);

        ftp.logout();
        ftp.disconnect();
        System.out.println("Success upload for: " + fullFilePath);
    }

    private SSHClient setupSshj() throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(hostnameSource);
        client.authPassword(usernameSource, passwordSource);
        return client;
    }
}
