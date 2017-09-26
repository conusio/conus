CREATE TABLE if not exists conus
(id INTEGER PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(200),
 description VARCHAR(200),
 askingprice VARCHAR(200),
 producturl VARCHAR(200),
 imageurl VARCHAR(200),
 email VARCHAR(200),
 timestamp TIMESTAMP);
--;;
CREATE TABLE if not exists things
(id INTEGER PRIMARY KEY AUTO_INCREMENT,
name VARCHAR(10000),
owner INTEGER,
description VARCHAR(10000),
askingprice VARCHAR(200),
producturl VARCHAR(200),
imageurl VARCHAR(200),
aal VARCHAR (1000),
timestamp TIMESTAMP);
--;;
CREATE TABLE if not exists users
(id INTEGER PRIMARY KEY AUTO_INCREMENT,
name VARCHAR(200),
githubid VARCHAR(200),
email VARCHAR(200),
login VARCHAR(200),
location VARCHAR(200),
timestamp TIMESTAMP);
