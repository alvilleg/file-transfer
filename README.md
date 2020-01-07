### Generate jar
```
mvn clean
mvn install
```

### Execute
```
java \
-DhostnameTarget=181.55.123.197 \
-DpasswordTarget=@user \
-DusernameTarget=ftppass \
-DhostnameSource=localhost -DpasswordSource=pass \
-DusernameSource=user  \
-DfullPathToDownload=/path/to/files/in/sftp/89_CCF57_201911_S_N_I_2962853.zip \ 
-DlocalTargetPath=/tmp/  \
-DremoteTargetPath=/Users/alde/Documents/work/alianzap/ \ 
-DlocalFileName=89_CCF57_201911_S_N_I_2962853.zip  \
-Dfile.encoding=UTF-8  \
-Dmode=passive \
-classpath :simple-file-transfer-1.0-SNAPSHOT-jar-with-dependencies.jar:lib/bcprov-jdk15on-1.60.jar:lib/bcpkix-jdk15on-1.60.jar:lib/bcprov-jdk15on-1.60.jar:lib/eddsa-0.2.0.jar:lib/jzlib-1.1.3.jar:lib/slf4j-api-1.7.7.jar:lib/sshj-0.27.0.jar as.App
```
