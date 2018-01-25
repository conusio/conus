# conus ![build status](https://api.travis-ci.org/conusio/conus.svg?branch=master)

## Prerequisites

lein

## Running

To start a web server for the application, run:

    lein repl
   
   
then from the repl, run 

    (start)
    
    
    
## setting up fixture data

TODO make this better.
in user.clj there are examples of example data. to hand-insert, you'd call `(db/save-thing! thing-map)` and `(db/save-user! user-map)`. But it's easier to upload pictures through localhost:3000/user/hedonist , so you should just do that. 


## when you get db errors (like "table doesn't exist") try deleting the dev db in $proj_root

## deploying to production

```bash
cd ~/conus
git checkout master
git pull
lein uberjar
sudo -E java -cp target/uberjar/conus.jar:resources conus.core &
# for the curious, `sudo` is needed because the app runs on port 443.
# -E means "keep the user's environment, i.e. env vars." we want DATABASE_URL, LEIN_ROOT, and a few others.
# & is for running asynchronously, so the job runs in the background
```

## updated (jan 25 2018) deploying to production (though the old way will still work)

to deploy the jar, assuming you've _already_ setcap'd:

```bash
nohup java -cp target/uberjar/conus.jar:resources conus.core &
```

to setcap:
```bash
sudo setcap CAP_NET_BIND_SERVICE=+eip /usr/lib/jvm/java-8-openjdk-amd64/jre/bin/java
```

(see notes in the spendgap slack, administrivia channel, for details). also stackoverflow: https://superuser.com/a/892391 )


## making changes to queries.sql

to have the app reload the queries.sql file, inside of `conus.db.core`, eval
```clojure
(conman/bind-connection *db* "sql/queries.sql")
```
(when you're running the app via `lein repl`)

## chrome redirects localhost:3000 to https when developing

[this worked for me](https://superuser.com/a/869739). "empty cache and hard reload."

