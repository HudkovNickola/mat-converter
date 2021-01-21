#!/bin/sh
libs=../libs/mat-to-json-1.0-SNAPSHOT.jar
configFile=../config.properties
java -cp $libs Main $configFile
