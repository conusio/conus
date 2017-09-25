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

-- :name get-id-from-login :? :1
-- :doc get the user's id from the user's login
SELECT id from users
where login = :login

-- :name get-owner-from-login :? :1
SELECT id from users
where login = :login

-- :name get-things-by-owner :? :*
SELECT * from things
inner join users on things.owner = users.id
where users.login = :login

-- :name get-thing-by-login-and-name :? :1
SELECT * from things
inner join users on things.owner = users.id
where users.login = :login and
things.name = :name

-- :name get-for-home-page
-- :doc
SELECT users.login, things.name, things.description, things.imageurl, things.producturl, things.askingprice, things.timestamp
from things
inner join users on things.owner = users.id


-- :name get-logins :? :*
-- :doc get all users
SELECT login from users

-- :name get-id-of-thing :? :1
SELECT id
FROM things
WHERE name = :name
      AND description = :description
      AND askingprice = :askingprice
      AND producturl = :producturl
      AND imageurl = :imageurl;

-- :name update-thing! :! :*
-- :doc UPDATEs a thing
UPDATE things
SET name = :name,
    description = :description,
    askingprice = :askingprice,
    producturl = :producturl,
    imageurl = :imageurl
WHERE id = :id

-- :name update-thing-without-picture! :! :*
-- :doc updates a thing
update things
set name = :name,
description = :description,
askingprice = :askingprice,
producturl = :producturl
where id = :id

-- :name delete-thing! :! :*
-- :doc deletes a thing
delete from things
where id = :id

-- :name get-things-from-description :? :*
-- returns a list of things matching a word in description
select * from things
where description like :tag
