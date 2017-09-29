#!/bin/bash
set -e

nohup java -cp conus.jar:resources conus.core > foo.out 2> foo.err  & 
disown %
