#! /bin/bash
wget -N http://apache.mirrors.ovh.net/ftp.apache.org/dist/storm/apache-storm-0.9.4/apache-storm-0.9.4.tar.gz -O $HOME/apache-storm-0.9.4.tar.gz
mvn clean package -Pstorm-app-package -Dpkg.version=0.9.4 -Dstorm.version=0.9.4 -Dpkg.name=apache-storm-0.9.4.tar.gz -Dpkg.src=$HOME
