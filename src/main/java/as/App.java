package as;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Hello world!
 */
public class App {
    BouncyCastleProvider bouncyCastleProvider;
    String hostname;
    String username;
    String password;
    String fullPathToDownload;
    String localTargetPath;
    String remoteTargetPath;
    String localFileName;

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public static void main(String[] args) throws IOException {

        App app = new App();
        app.setHostname(System.getProperty("hostname"));
        app.setPassword(System.getProperty("password"));
        app.setUsername(System.getProperty("username"));
        app.setFullPathToDownload(System.getProperty("fullPathToDownload"));
        app.setLocalTargetPath(System.getProperty("localTargetPath"));
        app.setRemoteTargetPath(System.getProperty("remoteTargetPath"));
        app.setLocalFileName(System.getProperty("localFileName"));

        app.downloadFile(app.fullPathToDownload, app.localFileName);
        app.unzip(app.localTargetPath + app.localFileName);

        System.exit(0);
    }

    public void unzip(String zipFilePath) throws IOException {
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
            upload(newFile.getCanonicalPath(), zipEntry.getName());
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
        //sftpClient.get(fileToDownload, localTargetPath + fileName);

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

    private SSHClient setupSshj() throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(hostname);
        client.authPassword(username, password);
        return client;
    }
}
