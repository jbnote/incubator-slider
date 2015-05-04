#! /bin/bash
echo "Remember to customize appConfig-default.json and possibly resources-default.json"
APPNAME=${1:-storm-on-slider}
slider install-package --replacepkg --name STORM --package target/slider-storm-app-package-0.70.1-incubating.zip
slider stop $APPNAME
slider destroy $APPNAME 
slider create $APPNAME --filesystem hdfs://root --queue dev --template target/slider-storm-app-package-0.70.1-incubating/appConfig-default.json --resources target/slider-storm-app-package-0.70.1-incubating/resources-default.json -D fs.defaultFS=hdfs://root
