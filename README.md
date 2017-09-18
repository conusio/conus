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

## making changes to queries.sql

to have the app reload the queries.sql file, inside of `conus.db.core`, eval
```clojure
(conman/bind-connection *db* "sql/queries.sql")
```
(when you're running the app via `lein repl`)
