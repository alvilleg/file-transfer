#!/bin/bash
#month=$(date +"%m")
#day=$(date +"%d")
#year=$(date +"%Y")
month=11 #$(date +"%_m")
day=30 # $(date +"%_d")
year=2018 #$(date +"%Y")

db_control_name=$year$month$day
echo $db_control_name
java \
-DhostnameTarget=181.55.123.197 \
-DpasswordTarget=@Simple \
-DusernameTarget=ftpsimple \
-DhostnameSource=localhost -DpasswordSource=Absolute4n \
-DusernameSource=aldemarv  \
-DfullPathToDownload=/home/aldemarv/Downloads/publicaciones/comfandiftp/$year/$month/$day/CCF \
-DlocalTargetPath=/tmp/file-transfer/files/downloaded/from/sftp/  \
-DremoteTargetPath=/Cuota_mon/PROCESS/ReAp_Pil/Pend_Inb/Opr_Info/ \
-DlocalFileName=89_CCF57_201911_S_N_I_2962853.zip  \
-DdbControlName=$db_control_name \
-Dmode=passive \
-classpath :target/simple-file-transfer-1.0-SNAPSHOT-jar-with-dependencies.jar as.App
#:lib/bcprov-jdk15on-1.60.jar:lib/bcpkix-jdk15on-1.60.jar:lib/bcprov-jdk15on-1.60.jar:lib/eddsa-0.2.0.jar:lib/jzlib-1.1.3.jar:lib/slf4j-api-1.7.7.jar:lib/sshj-0.27.0.jar:lib/deploy.jar:lib/ext/cldrdata.jar:lib/ext/dnsns.jar:lib/ext/jaccess.jar:lib/ext/jfxrt.jar:lib/ext/localedata.jar:lib/ext/nashorn.jar:lib/ext/sunec.jar:lib/ext/sunjce_provider.jar:lib/ext/sunpkcs11.jar:lib/ext/zipfs.jar:lib/javaws.jar:lib/jce.jar:lib/jfr.jar:lib/jfxswt.jar:lib/jsse.jar:lib/management-agent.jar:lib/plugin.jar:lib/resources.jar:lib/rt.jar:ant-javafx.jar:dt.jar:javafx-mx.jar:jconsole.jar:packager.jar:sa-jdi.jar:tools.jar:lib/commons-net-3.6.jar:lib/commons-io-2.5.jar
