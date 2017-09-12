-- :name save-message! :! :n
-- :doc creates a new message
INSERT INTO conus
       ( name,  description,  askingprice,  producturl,  imageurl,  email,  timestamp)
VALUES (:name, :description, :askingprice, :producturl, :imageurl, :email, :timestamp)

-- :name get-messages :? :*
-- :doc selects all available messages
SELECT * from conus

-- :name save-thing! :! :n
-- :doc creates a new thing
INSERT INTO things
       ( name,  owner,  description,  askingprice,  producturl,  imageurl,  timestamp)
VALUES (:name, :owner, :description, :askingprice, :producturl, :imageurl, :timestamp)

-- :name get-things :? :*
-- :doc selects all available things
SELECT * from things

-- :name save-user! :! :n
-- :doc inserts a user
INSERT INTO users
       ( name,  githubid,  email,  login,  location,  timestamp)
VALUES (:name, :githubid, :email, :login, :location, :timestamp)


-- :name get-users :? :*
-- :doc get all users
SELECT * from users

-- :name get-owner-from-login :? :*
SELECT id from users
where login = :login

-- :name get-logins :? :*
-- :doc get all users
SELECT login from users
