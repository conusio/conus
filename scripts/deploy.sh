#!/bin/bash
set -e

nohup java -cp target/uberjar/conus.jar:resources conus.core > foo.out 2> foo.err  & 
disown %
