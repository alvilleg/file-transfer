### Generate jar
```
mvn clean
```

### Execute
```
java -Dhostname=<hostname> -Dpassword=<password> -Dusername=<username> -DfullPathToDownload=<path_to_zip_file> -DlocalTargetPath=<local_target_path> -DremoteTargetPath=<remote_target_path> -DlocalFileName=<local_name_zip_file> -Dfile.encoding=UTF-8 -classpath :simple-file-transfer-1.0-SNAPSHOT-jar-with-dependencies.jar:lib/bcprov-jdk15on-1.60.jar:lib/bcpkix-jdk15on-1.60.jar:lib/bcprov-jdk15on-1.60.jar:lib/eddsa-0.2.0.jar:lib/jzlib-1.1.3.jar:lib/slf4j-api-1.7.7.jar:lib/sshj-0.27.0.jar as.App
```
