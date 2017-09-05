-- :name save-message! :! :n
-- :doc creates a new message
INSERT INTO conus
       ( name,  description,  askingprice,  producturl,  imageurl,  email,  timestamp)
VALUES (:name, :description, :askingprice, :producturl, :imageurl, :email, :timestamp)

-- :name get-messages :? :*
-- :doc selects all available messages
SELECT * from conus
