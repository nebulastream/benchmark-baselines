#!/bin/bash

for i in 1 2 4 8 16
do
   bash ./runYSB.sh "$i"
   bash ./runCM1.sh "$i"
   bash ./runCM2.sh "$i"
   bash ./runLR1.sh "$i"
   bash ./runLR2.sh "$i"
   bash ./runME1.sh "$i"
   bash ./runSG1.sh "$i"
   bash ./runSG2.sh "$i"
   bash ./runSG3.sh "$i"
done