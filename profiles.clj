;; WARNING
;; The profiles.clj file is used for local environment variables, such as database credentials.
;; This file is listed in .gitignore and will be excluded from version control by Git.

{:profiles/dev  {:env {:database-url "jdbc:mysql://localhost:3306/conus?user=root"}}
 :profiles/test {:env {:database-url "jdbc:h2:./guestbook_test.db"}}}
