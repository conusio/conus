# conus

generated using Luminus version "2.9.11.08"

FIXME

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

    lein repl
   
   
then from the repl, run 

    (start)
    
    
    
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

## License

Copyright © 2016 FIXME
