package as;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;


import java.io.*;
import java.util.List;
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

        app.downloadFile(app.fullPathToDownload, app.localFileName);
        app.unzip(app.localTargetPath + app.localFileName);

        System.exit(0);
    }

    public void unzip(String zipFilePath) throws Exception {
        String fileZip = zipFilePath;
        File destDir = new File(localTargetPath);
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {

            File newFile = new File(destDir, zipEntry.getName());
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            System.out.println(zipEntry.getName());
            fos.close();
            uploadFTP(newFile.getCanonicalPath(), zipEntry.getName());
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();
    }

    public void downloadFile(String fileToDownload, String fileName)
        throws IOException {
        SSHClient sshClient = setupSshj();
        SFTPClient sftpClient = sshClient.newSFTPClient();

        List<RemoteResourceInfo> ls = sftpClient.ls("/Users/alde/Downloads");
        //ls.stream().forEach((res) -> System.out.println(res.getName()));
        sftpClient.get(fileToDownload, localTargetPath + fileName);

        sftpClient.close();
        sshClient.disconnect();
    }

    public void upload(String fullFilePath, String fileName)
        throws IOException {
        SSHClient sshClient = setupSshj();
        SFTPClient sftpClient = sshClient.newSFTPClient();

        sftpClient.put(fullFilePath, remoteTargetPath + fileName);

        sftpClient.close();
        sshClient.disconnect();
    }

    public void uploadFTP(String fullFilePath, String fileName)
        throws Exception {
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
        OutputStream outputStream = ftp.storeFileStream(file.getName());
        System.out.println("Sending: ..." + file.getName());
        IOUtils.copy(in, outputStream);
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(outputStream);

        ftp.logout();
        ftp.disconnect();
        System.out.println("Finished " + fullFilePath);
    }

    private SSHClient setupSshj() throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(hostnameSource);
        client.authPassword(usernameSource, passwordSource);
        return client;
    }
}
